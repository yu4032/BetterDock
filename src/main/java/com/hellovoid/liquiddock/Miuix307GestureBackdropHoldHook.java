package com.hellovoid.liquiddock;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Continuous APP-transition capture for HyperOS 3.0.7 / Launcher 4.50.
 *
 * 4.50 decompilation gives reliable lifecycle boundaries, but those boundaries control
 * capture cadence/source authority rather than freezing the last bitmap. During APP→HOME/RECENTS
 * the foreground task remains a live SurfaceFlinger composition, so LiquidDock samples every
 * animation frame and keeps the capture scene pinned to APP/FULL_DISPLAY until an exact
 * destination authority (Overview or WMShell finish/abort) takes over.
 */
final class Miuix307GestureBackdropHoldHook {
    private static final String TAG = "[DC][GH]";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private static volatile boolean vendorTransitionActive;
    private static volatile boolean systemUiTransitionActive;
    private static volatile boolean captureBurstRunning;
    private static volatile long captureBurstSession;
    private static WeakReference<DockLiquidGlassView> transitionGlassRef =
            new WeakReference<>(null);

    private Miuix307GestureBackdropHoldHook() {}

    static void install(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        installVendorAppLifecycle(classLoader);
        try {
            installOverviewHandoff();
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " Overview handoff hook unavailable", error);
        }
        Api101Bridge.log(TAG + " 4.50 continuous gesture capture installed");
    }

    private static void installVendorAppLifecycle(ClassLoader classLoader) {
        try {
            Class<?> appMode = Class.forName(
                    "com.miui.home.recents.GestureModeApp", false, classLoader);

            HookUtil.hookMethod(appMode, "onStartGesture", new Class<?>[0], chain -> {
                DockLiquidGlassView glass = boundGlass();
                if (glass != null) transitionGlassRef = new WeakReference<>(glass);
                setVendorTransitionActive(true, "GestureModeApp.onStartGesture");
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });

            HookUtil.hookMethod(appMode, "onRecentsAnimationCanceled",
                    new Class<?>[]{boolean.class}, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        setVendorTransitionActive(false, "recents-animation-canceled");
                        return result;
                    });

            int appEndHooks = hookAnimationEnd(
                    Class.forName("com.miui.home.recents.GestureModeApp$6", false, classLoader),
                    () -> setVendorTransitionActive(false, "app-to-app-animation-end"));
            int homeEndHooks = hookAnimationEnd(
                    Class.forName("com.miui.home.recents.GestureModeApp$8", false, classLoader),
                    () -> setVendorTransitionActive(false, "app-to-home-animation-end"));

            Api101Bridge.log(TAG + " 4.50 lifecycle hooks appStart=1 appEnd="
                    + appEndHooks + " homeEnd=" + homeEndHooks);
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " 4.50 vendor gesture lifecycle unavailable", error);
        }
    }

    private static int hookAnimationEnd(Class<?> listenerClass, Runnable afterEnd) {
        int hooked = 0;
        for (Method method : listenerClass.getDeclaredMethods()) {
            if (!"onAnimationEnd".equals(method.getName())) continue;
            HookUtil.hook(method, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                afterEnd.run();
                return result;
            });
            hooked++;
        }
        return hooked;
    }

    /** SystemUI already owns the exact current glass; never rediscover it through another hook. */
    static void setSystemUiTransitionActive(
            DockLiquidGlassView glass, boolean active, String reason) {
        if (active && glass != null) {
            transitionGlassRef = new WeakReference<>(glass);
            Api101Bridge.log(TAG + " SystemUI transition authority glass="
                    + glass.getClass().getSimpleName() + "@"
                    + Integer.toHexString(System.identityHashCode(glass))
                    + " reason=" + reason);
        }
        if (systemUiTransitionActive == active) {
            if (active) ensureCaptureBurst("systemui-refresh-" + reason);
            return;
        }
        systemUiTransitionActive = active;
        updateCaptureBurst("systemui-" + reason);
    }

    /** Compatibility entry for non-runtime callers; prefer the explicit-view overload above. */
    static void setSystemUiTransitionActive(boolean active, String reason) {
        setSystemUiTransitionActive(active ? transitionGlass() : null, active, reason);
    }

    private static void setVendorTransitionActive(boolean active, String reason) {
        if (active) {
            DockLiquidGlassView glass = boundGlass();
            if (glass != null) transitionGlassRef = new WeakReference<>(glass);
        }
        if (vendorTransitionActive == active) {
            if (active) ensureCaptureBurst("vendor-refresh-" + reason);
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
        if (isTransitionCaptureActive()) ensureCaptureBurst(reason);
        else stopCaptureBurst(reason);
    }

    private static void ensureCaptureBurst(String reason) {
        DockLiquidGlassView glass = transitionGlass();
        if (glass == null) {
            Api101Bridge.log(TAG + " transition capture skipped: no bound glass reason=" + reason);
            return;
        }

        // Destination ownership may flip HOME immediately after ACTION_UP. The transition itself is
        // still APP/FULL_DISPLAY until exact Overview or Shell finish commits the destination.
        pinTransitionSceneToApp(glass);

        if (captureBurstRunning) {
            renewTransitionCapture(glass, "miuix307-transition-refresh-" + reason);
            return;
        }

        captureBurstRunning = true;
        final long session = ++captureBurstSession;
        keepAppVisibilityPrearm(glass, true);
        renewTransitionCapture(glass, "miuix307-transition-start-" + reason);
        Api101Bridge.log(TAG + " transition capture burst start session=" + session
                + " reason=" + reason);
        scheduleCaptureFrame(glass, session);
    }

    private static void scheduleCaptureFrame(DockLiquidGlassView glass, long session) {
        glass.postOnAnimation(() -> {
            if (!captureBurstRunning || session != captureBurstSession
                    || !isTransitionCaptureActive() || transitionGlass() != glass) return;
            keepAppVisibilityPrearm(glass, true);
            renewTransitionCapture(glass, "miuix307-transition-continuous");
            scheduleCaptureFrame(glass, session);
        });
    }

    private static void stopCaptureBurst(String reason) {
        if (!captureBurstRunning) {
            if (!isTransitionCaptureActive()) transitionGlassRef = new WeakReference<>(null);
            return;
        }
        DockLiquidGlassView glass = transitionGlass();
        captureBurstRunning = false;
        captureBurstSession++;
        if (glass != null) {
            keepAppVisibilityPrearm(glass, false);
            glass.requestCapture("miuix307-transition-settle-" + reason);
        }
        Api101Bridge.log(TAG + " transition capture burst stop reason=" + reason);
        if (!isTransitionCaptureActive()) transitionGlassRef = new WeakReference<>(null);
    }

    private static void renewTransitionCapture(DockLiquidGlassView glass, String reason) {
        if (glass == null) return;
        pinTransitionSceneToApp(glass);
        try {
            Object cadence = HookUtil.getField(glass, "captureCadence");
            if (cadence != null) HookUtil.invoke(cadence, "noteInteraction", System.nanoTime());
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " transition cadence renewal unavailable", error);
        }
        glass.requestCapture(reason);
    }

    private static void pinTransitionSceneToApp(DockLiquidGlassView glass) {
        if (glass == null || !isTransitionCaptureActive()) return;
        try {
            Object state = HookUtil.getField(glass, "sceneState");
            if (state != null) HookUtil.invoke(state, "setGestureTarget", "APP", System.nanoTime());
            HookUtil.invoke(glass, "updateDesiredScene");
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " transition APP scene pin unavailable", error);
        }
    }

    private static void keepAppVisibilityPrearm(DockLiquidGlassView glass, boolean active) {
        if (glass == null) return;
        try {
            int token = HookUtil.getIntField(glass, "appBackdropPrearmToken");
            HookUtil.setIntField(glass, "appBackdropPrearmToken", token + 1);
            HookUtil.setField(glass, "appBackdropPrearmActive", active);
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " APP visibility prearm update unavailable", error);
        }
    }

    private static void installOverviewHandoff() {
        HookUtil.hookMethod(DockLiquidGlassView.class, "setOverviewActive",
                new Class<?>[]{boolean.class, String.class}, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    if (args.length > 0 && Boolean.TRUE.equals(args[0])) {
                        stopAllTransitionCapture("exact-overview");
                    }
                    return chain.proceed(args);
                });
    }

    private static DockLiquidGlassView transitionGlass() {
        DockLiquidGlassView glass = transitionGlassRef.get();
        if (glass != null) return glass;
        glass = boundGlass();
        if (glass != null) transitionGlassRef = new WeakReference<>(glass);
        return glass;
    }

    private static DockLiquidGlassView boundGlass() {
        try {
            Object value = HookUtil.invokeStatic(Miuix307RecentsInputHook.class, "boundGlass");
            return value instanceof DockLiquidGlassView ? (DockLiquidGlassView) value : null;
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " transition glass lookup unavailable", error);
            return null;
        }
    }
}
