package com.hellovoid.liquiddock;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Protects the last clean APP backdrop while HyperOS 4.50 transforms the foreground task.
 *
 * Reverse engineering of the target 4.50 launcher shows that raw GestureInputHelper UP/CANCEL is
 * not a visual completion boundary: the vendor stream can emit CANCEL before the final UP and
 * before WMShell APP_TO_LAUNCHER.  The authoritative pre-transform boundary is
 * GestureModeApp.onStartGesture(), before startGestureAppMode()/actionMoveAppDrag().  Destination
 * completion is owned by the vendor animation listeners, exact Overview, or WMShell.
 */
final class Miuix307GestureBackdropHoldHook {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private static volatile boolean appGestureHold;
    private static volatile long gestureSession;
    private static WeakReference<DockLiquidGlassView> heldGlass = new WeakReference<>(null);

    private Miuix307GestureBackdropHoldHook() {}

    static void install(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        installVendorAppLifecycle(classLoader);
        installCaptureRequestGate();
        installCaptureInstallGate();
        installOverviewHandoff();
        installSystemUiHandoff();
        Api101Bridge.log("[DC][GH] 4.50 vendor gesture lifecycle hold installed");
    }

    /**
     * 4.50 decompilation anchors:
     *   GestureModeApp.onStartGesture() -> startGestureAppMode() -> later actionMoveAppDrag()
     *   GestureModeApp$8.onAnimationEnd() -> finishAppToHomeNew() -> onEnterHomeAnimFinish()
     *   GestureModeApp$6.onAnimationEnd() -> performAppToAppAnimationEnd()
     * Recents remains owned by exact EnterOverviewStateEvent.
     */
    private static void installVendorAppLifecycle(ClassLoader classLoader) {
        try {
            Class<?> appMode = Class.forName(
                    "com.miui.home.recents.GestureModeApp", false, classLoader);

            HookUtil.hookMethod(appMode, "onStartGesture", new Class<?>[0], chain -> {
                // BEFORE vendor startGestureAppMode(): task geometry is still the clean APP frame.
                armFromVendorAppGesture();
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });

            HookUtil.hookMethod(appMode, "onRecentsAnimationCanceled",
                    new Class<?>[]{boolean.class}, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        releaseToApp("recents-animation-canceled");
                        return result;
                    });

            int appEndHooks = hookAnimationEnd(
                    Class.forName("com.miui.home.recents.GestureModeApp$6", false, classLoader),
                    () -> releaseToApp("app-to-app-animation-end"));
            int homeEndHooks = hookAnimationEnd(
                    Class.forName("com.miui.home.recents.GestureModeApp$8", false, classLoader),
                    Miuix307GestureBackdropHoldHook::releaseAfterVendorHomeAnimation);

            Api101Bridge.log("[DC][GH] 4.50 lifecycle hooks appStart=1 appEnd="
                    + appEndHooks + " homeEnd=" + homeEndHooks);
        } catch (Throwable error) {
            Api101Bridge.log("[DC][GH] 4.50 vendor gesture lifecycle unavailable", error);
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

    private static void armFromVendorAppGesture() {
        if (!Miuix307MaterialPipeline.isInstalled()) return;
        DockLiquidGlassView glass = boundGlass();
        if (glass == null) return;
        if (appGestureHold && heldGlass.get() == glass) return;

        CaptureScene installedBefore = installedScene(glass);
        CaptureScene desiredBefore = desiredScene(glass);

        // A very fast gesture can begin before the first mode-1 APP frame replaces the previous
        // HOME wallpaper. Never freeze that wallpaper as if it were a valid APP backdrop. The
        // renderer's existing invalidation path drops the stale scene and exposes MIUI's native
        // Dock background until a stable destination capture is allowed again.
        if (installedBefore != CaptureScene.APP) {
            HookUtil.invoke(glass, "invalidateInstalledBackdropForApp",
                    "miuix307-vendor-app-gesture-start");
        }

        gestureSession++;
        appGestureHold = true;
        heldGlass = new WeakReference<>(glass);

        // Invalidate queued/in-flight readbacks but keep a valid installed APP bitmap untouched.
        // Request and install gates below stay closed until a real visual authority resolves it.
        HookUtil.invoke(glass, "cancelPendingCaptureWork");
        Api101Bridge.log("[DC][GH] hold armed from GestureModeApp.onStartGesture session="
                + gestureSession + " desired=" + desiredBefore + " installed=" + installedBefore);
    }

    private static void releaseToApp(String reason) {
        DockLiquidGlassView glass = heldGlass.get();
        if (!appGestureHold || glass == null) return;
        clearHold();
        HookUtil.invoke(glass, "cancelPendingCaptureWork");
        glass.prearmAppBackdrop("miuix307-" + reason);
        Api101Bridge.log("[DC][GH] hold resolved to APP reason=" + reason);
    }

    private static void releaseAfterVendorHomeAnimation() {
        DockLiquidGlassView glass = heldGlass.get();
        if (!appGestureHold || glass == null) return;

        // Normally WMShell APP_TO_LAUNCHER has already taken authority. If so, simply remove the
        // gesture gate; SystemUiTransitionRuntime remains closed until its own compositor barrier.
        if (SystemUiTransitionRuntime.isVisualHoldActive(glass)) {
            clearHold();
            Api101Bridge.log("[DC][GH] vendor HOME end handed to active SystemUI hold");
            return;
        }

        // Fallback for a missed/late WMShell callback. The vendor listener is authoritative here:
        // GestureModeApp$8.onAnimationEnd() has already called finishAppToHomeNew() and the target
        // receives onEnterHomeAnimFinish(). Mark HOME stable, cross one compositor frame, then
        // reopen capture. No guessed millisecond delay is used.
        clearHold();
        HookUtil.setField(glass, "launcherLifecycleKnown", true);
        HookUtil.setField(glass, "launcherResumed", true);
        try {
            Object sceneState = HookUtil.getField(glass, "sceneState");
            if (sceneState != null) HookUtil.invoke(sceneState, "clearGestureTarget");
        } catch (Throwable ignored) {}
        HookUtil.invoke(glass, "updateDesiredScene");
        HookUtil.invoke(glass, "cancelPendingCaptureWork");
        glass.postOnAnimation(() -> glass.requestCapture("miuix307-vendor-home-animation-end"));
        Api101Bridge.log("[DC][GH] hold resolved by vendor HOME animation end");
    }

    private static void clearForOverview(DockLiquidGlassView glass) {
        if (!appGestureHold || glass == null || heldGlass.get() != glass) return;
        clearHold();
        Api101Bridge.log("[DC][GH] hold handed to exact Overview");
    }

    private static void clearForSystemUi(DockLiquidGlassView glass, long expectedSession) {
        if (!appGestureHold || glass == null || heldGlass.get() != glass
                || gestureSession != expectedSession) return;
        if (!SystemUiTransitionRuntime.isVisualHoldActive(glass)) return;
        clearHold();
        Api101Bridge.log("[DC][GH] hold handed to SystemUI authority session=" + expectedSession);
    }

    private static void clearHold() {
        gestureSession++;
        appGestureHold = false;
        heldGlass = new WeakReference<>(null);
    }

    private static void installCaptureRequestGate() {
        HookUtil.hookMethod(DockLiquidGlassView.class, "requestStateCapture",
                new Class<?>[]{String.class}, chain -> {
                    if (appGestureHold && chain.getThisObject() == heldGlass.get()) return null;
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                });
    }

    private static void installCaptureInstallGate() {
        int hooked = 0;
        for (Method method : DockLiquidGlassView.class.getDeclaredMethods()) {
            if (!"installCapture".equals(method.getName()) || method.getParameterCount() != 3) {
                continue;
            }
            HookUtil.hook(method, chain -> {
                Object owner = chain.getThisObject();
                if (appGestureHold && owner == heldGlass.get()) {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    if (args.length > 0 && args[0] != null) {
                        try { HookUtil.invoke(args[0], "recycle"); }
                        catch (Throwable ignored) {}
                    }
                    return null;
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            hooked++;
        }
        Api101Bridge.log("[DC][GH] APP gesture install gates=" + hooked);
    }

    private static void installOverviewHandoff() {
        HookUtil.hookMethod(DockLiquidGlassView.class, "setOverviewActive",
                new Class<?>[]{boolean.class, String.class}, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    Object result = chain.proceed(args);
                    if (args.length > 0 && Boolean.TRUE.equals(args[0])
                            && chain.getThisObject() instanceof DockLiquidGlassView) {
                        clearForOverview((DockLiquidGlassView) chain.getThisObject());
                    }
                    return result;
                });
    }

    private static void installSystemUiHandoff() {
        HookUtil.hookMethod(SystemUiTransitionRuntime.class, "beginAppToLauncherVisualHold",
                new Class<?>[]{long.class, long.class, int.class}, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    DockLiquidGlassView glass = heldGlass.get();
                    if (glass != null && appGestureHold) {
                        final long session = gestureSession;
                        // Binder dispatch posts the real visualHold mutation to Launcher main.
                        // Queue behind it so there is never an open install/request frame.
                        glass.post(() -> clearForSystemUi(glass, session));
                    }
                    return result;
                });
    }

    private static CaptureScene installedScene(DockLiquidGlassView glass) {
        if (glass == null) return CaptureScene.UNKNOWN;
        try {
            Object value = HookUtil.getField(glass, "installedCaptureScene");
            return value instanceof CaptureScene ? (CaptureScene) value : CaptureScene.UNKNOWN;
        } catch (Throwable ignored) {
            return CaptureScene.UNKNOWN;
        }
    }

    private static CaptureScene desiredScene(DockLiquidGlassView glass) {
        if (glass == null) return CaptureScene.UNKNOWN;
        try {
            Object state = HookUtil.getField(glass, "sceneState");
            Object desired = HookUtil.invoke(state, "desired");
            return desired instanceof CaptureScene ? (CaptureScene) desired : CaptureScene.UNKNOWN;
        } catch (Throwable ignored) {
            return CaptureScene.UNKNOWN;
        }
    }

    private static DockLiquidGlassView boundGlass() {
        Object value = HookUtil.invokeStatic(Miuix307RecentsInputHook.class, "boundGlass");
        return value instanceof DockLiquidGlassView ? (DockLiquidGlassView) value : null;
    }
}
