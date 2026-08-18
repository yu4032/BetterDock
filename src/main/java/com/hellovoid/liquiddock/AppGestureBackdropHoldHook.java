package com.hellovoid.liquiddock;

import android.view.MotionEvent;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Continuous APP-transition capture for HyperOS 3.0.7 / Launcher 4.50.
 *
 * Historical note: this class used to freeze the last APP bitmap. Device testing showed that was
 * the wrong model: whichever frame happened to land immediately before the freeze (wallpaper,
 * half-transformed app, icon-flight frame) became permanently visible. The original LiquidDock
 * behavior was continuous capture. The 4.50 vendor lifecycle is now used only to start/stop a
 * bounded 60 FPS capture burst; it never blocks capture requests or frame installation.
 */
final class AppGestureBackdropHoldHook {
    private static final String TAG = "[DC][GH]";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final String GESTURE_INPUT_HELPER = "com.miui.home.recents.GestureInputHelper";
    private static final String GESTURE_MODE_APP = "com.miui.home.recents.GestureModeApp";
    private static final String GESTURE_MODE_APP_CANCEL_LISTENER = "com.miui.home.recents.GestureModeApp$6";
    private static final String GESTURE_MODE_APP_HOME_LISTENER = "com.miui.home.recents.GestureModeApp$8";

    private static boolean vendorTransitionActive;
    private static boolean systemUiTransitionActive;
    private static boolean captureBurstRunning;
    private static long captureBurstSession;

    private AppGestureBackdropHoldHook() {}

    static void install(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        int rawHooks = installVendorInputObservation(classLoader);
        int lifecycleHooks = installVendorGestureLifecycle(classLoader);
        installExactOverviewBridge();
        Api101Bridge.log(TAG + " continuous transition capture installed raw=" + rawHooks
                + " lifecycle=" + lifecycleHooks);
    }

    private static int installVendorInputObservation(ClassLoader classLoader) {
        try {
            Class<?> helper = Class.forName(GESTURE_INPUT_HELPER, false, classLoader);
            int hooked = 0;
            Set<String> seen = new HashSet<>();
            for (Class<?> cursor = helper; cursor != null && cursor != Object.class;
                 cursor = cursor.getSuperclass()) {
                for (Method method : cursor.getDeclaredMethods()) {
                    if (!"onInputEvent".equals(method.getName())
                            || Modifier.isStatic(method.getModifiers())) {
                        continue;
                    }
                    String signature = method.toGenericString();
                    if (!seen.add(signature)) continue;
                    HookUtil.hook(method, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        MotionEvent event = findMotionEvent(args);
                        if (event != null && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                            DockLiquidGlassView glass = boundGlass();
                            if (glass != null) {
                                // Preserve the old useful behavior: obtain one clean APP candidate
                                // before 4.50 starts transforming the task. This is a pre-capture,
                                // never a freeze/hold boundary.
                                HookUtil.invoke(glass, "armAppBackdropForGestureDown");
                                noteInteraction(glass);
                                glass.requestCapture("miuix307-app-gesture-raw-down");
                            }
                        }
                        return chain.proceed(args);
                    });
                    hooked++;
                }
            }
            return hooked;
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " vendor input observation unavailable: " + error);
            return 0;
        }
    }

    /** 4.50 vendor lifecycle: onStartGesture precedes task transforms; $6/$8 settle them. */
    private static int installVendorGestureLifecycle(ClassLoader classLoader) {
        int hooked = 0;
        try {
            Class<?> mode = Class.forName(GESTURE_MODE_APP, false, classLoader);
            HookUtil.hookMethod(mode, "onStartGesture", new Class<?>[0], chain -> {
                setVendorTransitionActive(true, "GestureModeApp.onStartGesture");
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            hooked++;
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " GestureModeApp.onStartGesture unavailable: " + error);
        }
        hooked += hookAnimationEnd(classLoader, GESTURE_MODE_APP_CANCEL_LISTENER,
                "GestureModeApp$6");
        hooked += hookAnimationEnd(classLoader, GESTURE_MODE_APP_HOME_LISTENER,
                "GestureModeApp$8");
        return hooked;
    }

    private static int hookAnimationEnd(ClassLoader classLoader, String className, String label) {
        try {
            Class<?> listener = Class.forName(className, false, classLoader);
            int hooked = 0;
            for (Method method : listener.getDeclaredMethods()) {
                if (!"onAnimationEnd".equals(method.getName())
                        || Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                HookUtil.hook(method, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    setVendorTransitionActive(false, label + ".onAnimationEnd");
                    return result;
                });
                hooked++;
            }
            return hooked;
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " " + label + " animation-end unavailable: " + error);
            return 0;
        }
    }

    private static MotionEvent findMotionEvent(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof MotionEvent) return (MotionEvent) arg;
        }
        return null;
    }

    static void setSystemUiTransitionActive(boolean active, String reason) {
        if (systemUiTransitionActive == active) {
            if (active) ensureCaptureBurst(reason);
            return;
        }
        systemUiTransitionActive = active;
        updateCaptureBurst("systemui-" + reason);
    }

    private static void setVendorTransitionActive(boolean active, String reason) {
        if (vendorTransitionActive == active) {
            if (active) ensureCaptureBurst(reason);
            return;
        }
        vendorTransitionActive = active;
        updateCaptureBurst("vendor-" + reason);
    }

    static void stopAllTransitionCapture(String reason) {
        vendorTransitionActive = false;
        systemUiTransitionActive = false;
        updateCaptureBurst(reason);
    }

    static boolean isTransitionCaptureActive() {
        return vendorTransitionActive || systemUiTransitionActive;
    }

    private static void updateCaptureBurst(String reason) {
        if (isTransitionCaptureActive()) {
            ensureCaptureBurst(reason);
        } else {
            stopCaptureBurst(reason);
        }
    }

    private static void ensureCaptureBurst(String reason) {
        DockLiquidGlassView glass = boundGlass();
        if (glass == null || !isAppOrRecentsScene(glass)) return;
        if (captureBurstRunning) {
            noteInteraction(glass);
            glass.requestCapture("miuix307-transition-refresh-" + reason);
            return;
        }

        captureBurstRunning = true;
        final long session = ++captureBurstSession;
        // Reuse the existing APP prearm visibility exception rather than bypassing hard gates.
        // isCaptureAllowed() checks SystemUI panel, screen power, workstation and drag gates first.
        keepAppVisibilityPrearm(glass, true);
        Api101Bridge.log(TAG + " transition capture burst start session=" + session
                + " reason=" + reason);
        scheduleCaptureFrame(glass, session);
    }

    private static void scheduleCaptureFrame(DockLiquidGlassView glass, long session) {
        if (glass == null) return;
        glass.postOnAnimation(() -> {
            if (!captureBurstRunning || session != captureBurstSession
                    || !isTransitionCaptureActive() || boundGlass() != glass) {
                return;
            }
            keepAppVisibilityPrearm(glass, true);
            noteInteraction(glass);
            glass.requestCapture("miuix307-transition-continuous");
            scheduleCaptureFrame(glass, session);
        });
    }

    private static void stopCaptureBurst(String reason) {
        if (!captureBurstRunning) return;
        DockLiquidGlassView glass = boundGlass();
        captureBurstRunning = false;
        captureBurstSession++;
        if (glass != null) {
            keepAppVisibilityPrearm(glass, false);
            glass.requestCapture("miuix307-transition-settle-" + reason);
        }
        Api101Bridge.log(TAG + " transition capture burst stop reason=" + reason);
    }

    private static void keepAppVisibilityPrearm(DockLiquidGlassView glass, boolean active) {
        if (glass == null) return;
        try {
            if (active) {
                // Invalidate the short raw-DOWN timeout token and keep the same bounded visibility
                // bypass alive for the duration of the real vendor/SystemUI transition.
                int token = HookUtil.getIntField(glass, "appBackdropPrearmToken");
                HookUtil.setIntField(glass, "appBackdropPrearmToken", token + 1);
                HookUtil.setField(glass, "appBackdropPrearmActive", true);
            } else {
                int token = HookUtil.getIntField(glass, "appBackdropPrearmToken");
                HookUtil.setIntField(glass, "appBackdropPrearmToken", token + 1);
                HookUtil.setField(glass, "appBackdropPrearmActive", false);
            }
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " APP visibility prearm update unavailable: " + error);
        }
    }

    private static void noteInteraction(DockLiquidGlassView glass) {
        if (glass == null) return;
        try {
            Object cadence = HookUtil.getField(glass, "captureCadence");
            if (cadence != null) HookUtil.invoke(cadence, "noteInteraction", System.nanoTime());
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " transition cadence renewal unavailable: " + error);
        }
    }

    private static void installExactOverviewBridge() {
        try {
            HookUtil.hookMethod(DockLiquidGlassView.class, "setOverviewActive",
                    new Class<?>[]{boolean.class, String.class}, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (args.length > 0 && Boolean.TRUE.equals(args[0])) {
                            // Exact Overview has its own continuous RECENTS loop. Transfer authority
                            // instead of running two independent request loops.
                            stopAllTransitionCapture("exact-overview");
                        }
                        return chain.proceed(args);
                    });
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " Overview bridge unavailable: " + error);
        }
    }

    private static boolean isAppOrRecentsScene(DockLiquidGlassView glass) {
        try {
            Object sceneState = HookUtil.getField(glass, "sceneState");
            Object desired = sceneState == null ? null : HookUtil.invoke(sceneState, "desired");
            String scene = String.valueOf(desired);
            return "APP".equals(scene) || "RECENTS".equals(scene);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static DockLiquidGlassView boundGlass() {
        try {
            Object value = HookUtil.getField(MiuixGlassHook.class, "glassRef");
            return value instanceof DockLiquidGlassView ? (DockLiquidGlassView) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
