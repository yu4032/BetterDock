package com.hellovoid.liquiddock;

import android.view.MotionEvent;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * APP gesture backdrop protection for HyperOS 3.0.7 / Launcher 4.50.
 *
 * The raw GestureInputHelper stream is useful only as an early pre-capture hint. Vendor gesture
 * lifecycle methods own the hold because 4.50 may emit internal ACTION_CANCEL before the gesture
 * finally resolves to HOME. GestureModeApp.onStartGesture() runs before actionMoveAppDrag(), so it
 * is the last stable boundary before the app surface starts transforming.
 */
final class AppGestureBackdropHoldHook {
    private static final String TAG = "[DC][GH]";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final String GESTURE_INPUT_HELPER = "com.miui.home.recents.GestureInputHelper";
    private static final String GESTURE_MODE_APP = "com.miui.home.recents.GestureModeApp";
    private static final String GESTURE_MODE_APP_CANCEL_LISTENER = "com.miui.home.recents.GestureModeApp$6";
    private static final String GESTURE_MODE_APP_HOME_LISTENER = "com.miui.home.recents.GestureModeApp$8";

    private static boolean gestureHold;
    private static long gestureSession;

    private AppGestureBackdropHoldHook() {}

    static void install(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        int rawHooks = installVendorInputObservation(classLoader);
        int lifecycleHooks = installVendorGestureLifecycle(classLoader);
        installCaptureRequestGate();
        installCaptureInstallGate();
        installExactOverviewBridge();
        installSystemUiTakeoverBridge();
        Api101Bridge.log(TAG + " APP gesture install raw=" + rawHooks
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
                    if (!"onInputEvent".equals(method.getName()) || Modifier.isStatic(method.getModifiers())) {
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
                                // Raw DOWN happens before GestureModeApp commits to an APP gesture.
                                // Use it only to refresh one clean APP candidate; never hold here.
                                HookUtil.invoke(glass, "armAppBackdropForGestureDown");
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

    /** 4.50 vendor lifecycle: onStartGesture -> MOVE transforms; $6/$8 end callbacks settle. */
    private static int installVendorGestureLifecycle(ClassLoader classLoader) {
        int hooked = 0;
        try {
            Class<?> mode = Class.forName(GESTURE_MODE_APP, false, classLoader);
            HookUtil.hookMethod(mode, "onStartGesture", new Class<?>[0], chain -> {
                armGestureHoldFromVendorLifecycle("GestureModeApp.onStartGesture");
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
                if (!"onAnimationEnd".equals(method.getName()) || Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                HookUtil.hook(method, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    releaseGestureHold(label + ".onAnimationEnd");
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

    private static void armGestureHoldFromVendorLifecycle(String reason) {
        DockLiquidGlassView glass = boundGlass();
        if (glass == null || !isAppScene(glass)) return;
        // If the currently installed bitmap is stale HOME/UNKNOWN, do not freeze wallpaper.
        // Drop it to the native Dock background, then block transformed replacement frames.
        Object installed = HookUtil.getField(glass, "installedCaptureScene");
        if (installed != null && !"APP".equals(String.valueOf(installed))) {
            HookUtil.invoke(glass, "invalidateInstalledBackdropForApp", "gesture-lifecycle-start");
        }
        gestureSession++;
        gestureHold = true;
        HookUtil.invoke(glass, "cancelPendingCaptureWork");
        Api101Bridge.log(TAG + " hold armed from " + reason + " session=" + gestureSession
                + " installed=" + installed);
    }

    private static void releaseGestureHold(String reason) {
        if (!gestureHold) return;
        DockLiquidGlassView glass = boundGlass();
        long session = gestureSession;
        gestureHold = false;
        if (glass != null) {
            HookUtil.invoke(glass, "cancelPendingCaptureWork");
            glass.requestCapture("miuix307-app-gesture-release-" + reason);
        }
        Api101Bridge.log(TAG + " hold released session=" + session + " reason=" + reason);
    }

    private static void installCaptureRequestGate() {
        try {
            HookUtil.hookMethod(DockLiquidGlassView.class, "requestStateCapture",
                    new Class<?>[]{String.class}, chain -> {
                        if (gestureHold && chain.getThisObject() == boundGlass()) return null;
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " request gate unavailable: " + error);
        }
    }

    private static void installCaptureInstallGate() {
        try {
            for (Method method : DockLiquidGlassView.class.getDeclaredMethods()) {
                if (!"installCapture".equals(method.getName()) || method.getParameterCount() != 3) continue;
                HookUtil.hook(method, chain -> {
                    if (gestureHold && chain.getThisObject() == boundGlass()) {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (args.length > 0 && args[0] != null) {
                            try { HookUtil.invoke(args[0], "recycle"); } catch (Throwable ignored) {}
                        }
                        return null;
                    }
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                });
            }
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " install gate unavailable: " + error);
        }
    }

    private static void installExactOverviewBridge() {
        try {
            HookUtil.hookMethod(DockLiquidGlassView.class, "setOverviewActive",
                    new Class<?>[]{boolean.class, String.class}, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (args.length > 0 && Boolean.TRUE.equals(args[0])) {
                            releaseGestureHold("exact-overview");
                        }
                        return chain.proceed(args);
                    });
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " Overview bridge unavailable: " + error);
        }
    }

    private static void installSystemUiTakeoverBridge() {
        try {
            HookUtil.hookMethod(SystemUiTransitionRuntime.class, "beginAppToLauncherVisualHold",
                    new Class<?>[]{long.class, long.class, int.class}, chain -> {
                        releaseGestureHold("systemui-app-to-launcher");
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " SystemUI takeover bridge unavailable: " + error);
        }
    }

    private static boolean isAppScene(DockLiquidGlassView glass) {
        try {
            Object sceneState = HookUtil.getField(glass, "sceneState");
            Object desired = sceneState == null ? null : HookUtil.invoke(sceneState, "desired");
            return "APP".equals(String.valueOf(desired));
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
