package com.hellovoid.liquiddock;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Continuous APP-transition capture for HyperOS 3.0.7 / Launcher 4.50.
 *
 * 4.50 decompilation gives us reliable lifecycle boundaries, but those boundaries control
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

    private Miuix307GestureBackdropHoldHook() {}

    static void install(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        installVendorAppLifecycle(classLoader);
        installOverviewHandoff();
        Api101Bridge.log(TAG + " 4.50 continuous gesture capture installed");
    }

    /**
     * 4.50 decompilation anchors:
     *   GestureModeApp.onStartGesture() -> startGestureAppMode() -> actionMoveAppDrag()
     *   GestureModeApp$8.onAnimationEnd() -> finishAppToHomeNew() -> onEnterHomeAnimFinish()
     *   GestureModeApp$6.onAnimationEnd() -> performAppToAppAnimationEnd()
     * Recents remains owned by exact EnterOverviewStateEvent.
     */
    private static void installVendorAppLifecycle(ClassLoader classLoader) {
        try {
            Class<?> appMode = Class.forName(
                    "com.miui.home.recents.GestureModeApp", false, classLoader);

            HookUtil.hookMethod(appMode, "onStartGesture", new Class<?>[0], chain -> {
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

    static void setSystemUiTransitionActive(boolean active, String reason) {
        if (systemUiTransitionActive == active) {
            if (active) ensureCaptureBurst("systemui-refresh-" + reason);
            return;
        }
        systemUiTransitionActive = active;
        updateCaptureBurst("systemui-" + reason);
    }

    private static void setVendorTransitionActive(boolean active, String reason) {
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
        if (isTransitionCaptureActive()) {
            ensureCaptureBurst(reason);
        } else {
            stopCaptureBurst(reason);
        }
    }

    private static void ensureCaptureBurst(String reason) {
        if (!Miuix307MaterialPipeline.isInstalled()) return;
        DockLiquidGlassView glass = boundGlass();
        if (glass == null) return;

        // Ownership/focus can report HOME as soon as the finger is released, while the APP→HOME
        // animation is still running. Transition capture must stay FULL_DISPLAY until WMShell
        // finish (or exact Overview) commits the destination, so pin the scene back to APP here.
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
                    || !isTransitionCaptureActive() || boundGlass() != glass) return;
            keepAppVisibilityPrearm(glass, true);
            renewTransitionCapture(glass, "miuix307-transition-continuous");
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

    private static void renewTransitionCapture(DockLiquidGlassView glass, String reason) {
        if (glass == null) return;
        // Reassert APP on every animation frame. HomeOwnership/Launcher lifecycle may already say
        // HOME after ACTION_UP; that is ownership information, not a visual completion boundary.
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
            if (state != null) {
                HookUtil.invoke(state, "setGestureTarget", "APP", System.nanoTime());
            }
            HookUtil.invoke(glass, "updateDesiredScene");
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " transition APP scene pin unavailable", error);
        }
    }

    /**
     * Reuse the existing safe APP visibility bypass while the Floating Dock is collapsed. Hard
     * gates are evaluated before this flag in DockLiquidGlassView.isCaptureAllowed(), so screen
     * power, SystemUI panel, workstation and drag exclusions remain authoritative.
     */
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
                        // Exact Overview has a self-sustained RECENTS capture loop already.
                        stopAllTransitionCapture("exact-overview");
                    }
                    return chain.proceed(args);
                });
    }

    private static DockLiquidGlassView boundGlass() {
        Object value = HookUtil.invokeStatic(Miuix307RecentsInputHook.class, "boundGlass");
        return value instanceof DockLiquidGlassView ? (DockLiquidGlassView) value : null;
    }
}
