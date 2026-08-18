package com.hellovoid.liquiddock;

import android.view.MotionEvent;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Protects the last clean APP backdrop during the small gap between Launcher gesture motion and
 * the later WMShell APP_TO_LAUNCHER transition notification.
 *
 * Device traces on HyperOS 307 show mode-1 capture still running after the foreground task has
 * already begun shrinking, while APP_TO_LAUNCHER is not pushed until ACTION_UP. Freezing at the
 * real vendor GestureInputHelper ACTION_DOWN keeps those transformed task frames from replacing
 * the clean APP image. The older LiquidDock input observer remains only as a compatibility fallback.
 * This hold does not predict HOME versus RECENTS: exact Overview or the existing SystemUI visual
 * hold remains the destination authority.
 */
final class Miuix307GestureBackdropHoldHook {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private static volatile boolean appGestureHold;
    private static volatile boolean releaseScheduled;
    private static volatile long gestureSession;
    private static WeakReference<DockLiquidGlassView> heldGlass = new WeakReference<>(null);

    private Miuix307GestureBackdropHoldHook() {}

    static void install(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        installVendorGestureInput(classLoader);
        installCompatInputBoundary();
        installCaptureRequestGate();
        installCaptureInstallGate();
        installOverviewHandoff();
        installSystemUiHandoff();
        Api101Bridge.log("[DC][GH] APP gesture backdrop hold installed");
    }

    /**
     * Authoritative pre-WMShell boundary on the tested HyperOS 307 launcher. The device trace
     * identifies com.miui.home.recents.GestureInputHelper.onInputEvent(InputEvent) as the source
     * receiving ACTION_DOWN before the first transformed APP capture. Scan overloads instead of
     * pinning a hidden signature so minor vendor changes do not silently disable the protection.
     */
    private static void installVendorGestureInput(ClassLoader classLoader) {
        try {
            Class<?> gestureInputClass = Class.forName(
                    "com.miui.home.recents.GestureInputHelper", false, classLoader);
            int hooked = 0;
            Class<?> cursor = gestureInputClass;
            while (cursor != null && cursor != Object.class) {
                for (Method method : cursor.getDeclaredMethods()) {
                    if (!"onInputEvent".equals(method.getName())
                            || Modifier.isStatic(method.getModifiers())) {
                        continue;
                    }
                    HookUtil.hook(method, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        MotionEvent event = findMotionEvent(args);
                        int action = event != null
                                ? event.getActionMasked() : MotionEvent.ACTION_CANCEL;
                        float rawX = event != null ? event.getRawX() : Float.NaN;
                        float rawY = event != null ? event.getRawY() : Float.NaN;

                        if (event != null && action == MotionEvent.ACTION_DOWN) {
                            // GestureInputHelper is the dedicated bottom-gesture input consumer;
                            // treat this stream as authoritative instead of re-testing Dock bounds.
                            maybeArm(rawX, rawY, true);
                            Api101Bridge.log("[DC][GH] vendor input DOWN hold=" + appGestureHold);
                        }

                        Object result = chain.proceed(args);

                        if (event != null && (action == MotionEvent.ACTION_UP
                                || action == MotionEvent.ACTION_CANCEL)) {
                            DockLiquidGlassView glass = heldGlass.get();
                            if (glass != null && appGestureHold) scheduleRelease(glass, action);
                            Api101Bridge.log("[DC][GH] vendor input end action=" + action
                                    + " hold=" + appGestureHold);
                        }
                        return result;
                    });
                    hooked++;
                }
                cursor = cursor.getSuperclass();
            }
            Api101Bridge.log("[DC][GH] vendor GestureInputHelper hooks=" + hooked);
        } catch (Throwable error) {
            Api101Bridge.log("[DC][GH] vendor GestureInputHelper unavailable", error);
        }
    }

    /** Compatibility fallback for launcher builds that still route through our 307 observer. */
    private static void installCompatInputBoundary() {
        HookUtil.hookMethod(Miuix307RecentsInputHook.class, "onInputMotion",
                new Class<?>[]{int.class, float.class, float.class, boolean.class}, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    int action = args.length > 0 && args[0] instanceof Integer
                            ? (Integer) args[0] : MotionEvent.ACTION_CANCEL;
                    float rawX = args.length > 1 && args[1] instanceof Float
                            ? (Float) args[1] : Float.NaN;
                    float rawY = args.length > 2 && args[2] instanceof Float
                            ? (Float) args[2] : Float.NaN;
                    boolean dockWindow = args.length > 3 && Boolean.TRUE.equals(args[3]);

                    if (action == MotionEvent.ACTION_DOWN) {
                        maybeArm(rawX, rawY, dockWindow);
                    }

                    Object result = chain.proceed(args);

                    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                        DockLiquidGlassView glass = heldGlass.get();
                        if (glass != null && appGestureHold) scheduleRelease(glass, action);
                    }
                    return result;
                });
    }

    private static MotionEvent findMotionEvent(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof MotionEvent) return (MotionEvent) arg;
        }
        return null;
    }

    private static void maybeArm(float rawX, float rawY, boolean authoritativeGestureInput) {
        if (!Miuix307MaterialPipeline.isInstalled()) return;
        DockLiquidGlassView glass = boundGlass();
        if (glass == null || desiredScene(glass) != CaptureScene.APP) return;
        if (appGestureHold && heldGlass.get() == glass) return;
        if (!authoritativeGestureInput && !glass.isTouchInDockArea(rawX, rawY)) return;

        gestureSession++;
        appGestureHold = true;
        releaseScheduled = false;
        heldGlass = new WeakReference<>(glass);

        // Invalidate any readback started just before ACTION_DOWN, but preserve the installed APP
        // bitmap. The request/install gates below then keep it stable through the gesture motion.
        HookUtil.invoke(glass, "cancelPendingCaptureWork");
        Api101Bridge.log("[DC][GH] hold armed session=" + gestureSession
                + " scene=APP authoritative=" + authoritativeGestureInput);
    }

    private static void scheduleRelease(DockLiquidGlassView glass, int action) {
        if (releaseScheduled || glass == null || heldGlass.get() != glass) return;
        releaseScheduled = true;
        final long session = gestureSession;
        glass.postOnAnimation(() -> {
            if (!appGestureHold || session != gestureSession || heldGlass.get() != glass) return;

            appGestureHold = false;
            releaseScheduled = false;
            heldGlass = new WeakReference<>(null);

            if (SystemUiTransitionRuntime.isVisualHoldActive(glass)) {
                Api101Bridge.log("[DC][GH] hold handed to SystemUI session=" + session);
                return;
            }

            CaptureScene scene = desiredScene(glass);
            Api101Bridge.log("[DC][GH] hold released after VSYNC session=" + session
                    + " action=" + action + " scene=" + scene);
            // No transition authority appeared: this was a cancelled/returned APP gesture. Refresh
            // only APP. Never guess HOME here; HOME remains WMShell-owned.
            if (scene == CaptureScene.APP) {
                glass.requestCapture("miuix307-app-gesture-release");
            }
        });
    }

    private static void clearForOverview(DockLiquidGlassView glass) {
        if (!appGestureHold || glass == null || heldGlass.get() != glass) return;
        gestureSession++;
        appGestureHold = false;
        releaseScheduled = false;
        heldGlass = new WeakReference<>(null);
        Api101Bridge.log("[DC][GH] hold handed to exact Overview");
    }

    private static void clearForSystemUi(DockLiquidGlassView glass, long expectedSession) {
        if (!appGestureHold || glass == null || heldGlass.get() != glass
                || gestureSession != expectedSession) return;
        if (!SystemUiTransitionRuntime.isVisualHoldActive(glass)) return;
        gestureSession++;
        appGestureHold = false;
        releaseScheduled = false;
        heldGlass = new WeakReference<>(null);
        Api101Bridge.log("[DC][GH] hold handed to SystemUI authority session=" + expectedSession);
    }

    private static void installCaptureRequestGate() {
        HookUtil.hookMethod(DockLiquidGlassView.class, "requestStateCapture",
                new Class<?>[]{String.class}, chain -> {
                    if (appGestureHold && chain.getThisObject() == heldGlass.get()) {
                        return null;
                    }
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
                        // beginAppToLauncherVisualHold() posts to the main thread when Binder calls
                        // it off-main. Queue behind that post, then hand authority over only after
                        // the real SystemUI visual hold is observable.
                        glass.post(() -> clearForSystemUi(glass, session));
                    }
                    return result;
                });
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
