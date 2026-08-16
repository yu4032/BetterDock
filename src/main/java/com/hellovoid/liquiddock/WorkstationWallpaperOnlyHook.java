package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewParent;

import java.lang.ref.WeakReference;

/**
 * Workstation compatibility bridge.
 *
 * DockLiquidGlassView owns workstation scene/capture state.  This hook only fixes
 * legacy MainHook visibility decisions around that state machine:
 *
 * - keep the LiquidDock host attached/visible in workstation mode;
 * - keep the native Dock background visible while a fresh workstation frame is pending;
 * - reveal the glass only after installCapture() installs a valid frame;
 * - after a workstation suspension, fall back to native background and restart the
 *   workstation HOME glass when the workstation itself is still active.
 *
 * No vendor Mingou snapshot/live-blur policy is forced here.  All Apps/Recents source
 * selection and capture cadence remain owned by DockLiquidGlassView.
 */
final class WorkstationWallpaperOnlyHook {
    private static WeakReference<DockLiquidGlassView> lastGlass =
            new WeakReference<>(null);

    private WorkstationWallpaperOnlyHook() {}

    static void install(ClassLoader classLoader) {
        installModeBridge();
        installBurstHandoff();
        installSuspendFallback();
        installCaptureReveal();
        installMainSyncHostGuard();
        MainHook.log("[DC] workstation LiquidGlass ownership bridge installed");
    }

    private static void installModeBridge() {
        try {
            HookUtil.hookMethod(DockLiquidGlassView.class,
                    "setWorkstationMode", new Class<?>[]{boolean.class}, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        Object target = chain.getThisObject();
                        if (!(target instanceof DockLiquidGlassView)) return result;

                        DockLiquidGlassView glass = (DockLiquidGlassView) target;
                        lastGlass = new WeakReference<>(glass);
                        boolean enabled = Boolean.TRUE.equals(chain.getArgs().get(0));
                        if (!enabled || !MainHook.isWorkstationMode()) {
                            ensureHostVisible(glass);
                            return result;
                        }

                        // MainHook.setupViews() may still set the parent host GONE after this
                        // method returns.  Post the activation so it runs after that legacy
                        // setup stack has completed.
                        glass.post(() -> activateWorkstationHomeGlass(glass, "workstation-mode"));
                        return result;
                    });
        } catch (Throwable error) {
            MainHook.log("[DC] workstation mode LiquidGlass bridge unavailable: " + error);
        }
    }

    private static void installBurstHandoff() {
        try {
            HookUtil.hookMethod(DockLiquidGlassView.class,
                    "startWorkstationCaptureBurst", new Class<?>[]{String.class}, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        Object target = chain.getThisObject();
                        if (MainHook.isWorkstationMode() && target instanceof DockLiquidGlassView) {
                            DockLiquidGlassView glass = (DockLiquidGlassView) target;
                            lastGlass = new WeakReference<>(glass);
                            String reason = String.valueOf(chain.getArgs().get(0));

                            // The burst itself may hide geometrySource before SurfaceFlinger
                            // has returned a new frame.  Keep the native background visible and
                            // the glass child hidden for that gap. installCapture() will swap
                            // them atomically once a valid frame exists.
                            ensureHostVisible(glass);
                            glass.setVisibility(View.INVISIBLE);
                            revealNativeBackdrop(glass, "burst-wait-" + reason);
                        }
                        return result;
                    });
        } catch (Throwable error) {
            MainHook.log("[DC] workstation burst handoff unavailable: " + error);
        }
    }

    private static void installSuspendFallback() {
        try {
            HookUtil.hookMethod(DockLiquidGlassView.class,
                    "suspendWorkstationGlass", new Class<?>[]{String.class}, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        Object target = chain.getThisObject();
                        if (!MainHook.isWorkstationMode() || !(target instanceof DockLiquidGlassView)) {
                            return result;
                        }

                        DockLiquidGlassView glass = (DockLiquidGlassView) target;
                        lastGlass = new WeakReference<>(glass);
                        String reason = String.valueOf(chain.getArgs().get(0));
                        ensureHostVisible(glass);
                        glass.setVisibility(View.INVISIBLE);
                        revealNativeBackdrop(glass, reason);

                        // Initial workstation entry is immediately handled by the
                        // setWorkstationMode() bridge above.  Later suspensions mean an
                        // All Apps/Recents transition has settled back to HOME; restart HOME
                        // glass on the next main-loop turn instead of leaving workstation
                        // permanently wallpaper-only.
                        if (!"workstation-enter".equals(reason)) {
                            glass.post(() -> activateWorkstationHomeGlass(
                                    glass, "resume-home-after-" + reason));
                        }
                        return result;
                    });
        } catch (Throwable error) {
            MainHook.log("[DC] workstation suspend fallback unavailable: " + error);
        }
    }

    private static void installCaptureReveal() {
        try {
            ClassLoader loader = DockLiquidGlassView.class.getClassLoader();
            Class<?> croppedFrame = Class.forName(
                    "com.hellovoid.liquiddock.DockLiquidGlassView$CroppedFrame",
                    false, loader);
            HookUtil.hookMethod(DockLiquidGlassView.class,
                    "installCapture",
                    new Class<?>[]{croppedFrame, String.class, CaptureScene.class},
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        Object target = chain.getThisObject();
                        if (MainHook.isWorkstationMode() && target instanceof DockLiquidGlassView) {
                            DockLiquidGlassView glass = (DockLiquidGlassView) target;
                            lastGlass = new WeakReference<>(glass);
                            ensureHostVisible(glass);
                            // installCapture() already made geometrySource transparent only
                            // after a real frame was installed. Reveal that frame now.
                            glass.setVisibility(View.VISIBLE);
                        }
                        return result;
                    });
        } catch (Throwable error) {
            MainHook.log("[DC] workstation capture reveal unavailable: " + error);
        }
    }

    private static void installMainSyncHostGuard() {
        try {
            HookUtil.hookMethod(MainHook.class,
                    "syncAll", new Class<?>[]{View.class}, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (MainHook.isWorkstationMode()) {
                            DockLiquidGlassView glass = lastGlass.get();
                            if (glass != null) ensureHostVisible(glass);
                        }
                        return result;
                    });
        } catch (Throwable error) {
            MainHook.log("[DC] workstation host visibility guard unavailable: " + error);
        }
    }

    private static void activateWorkstationHomeGlass(DockLiquidGlassView glass, String reason) {
        if (glass == null || !MainHook.isWorkstationMode()) return;
        ensureHostVisible(glass);
        try {
            Object sceneState = HookUtil.getField(glass, "sceneState");
            Object suspended = HookUtil.invoke(sceneState, "workstationSuspended");
            if (Boolean.FALSE.equals(suspended) && glass.getVisibility() == View.VISIBLE) {
                return;
            }

            HookUtil.invoke(glass, "startWorkstationCaptureBurst", reason);
            glass.requestCapture(reason);
            MainHook.log("[DC] workstation HOME LiquidGlass activation requested reason=" + reason);
        } catch (Throwable error) {
            revealNativeBackdrop(glass, "activation-failed-" + reason);
            MainHook.log("[DC] workstation HOME LiquidGlass activation failed: " + error);
        }
    }

    private static void ensureHostVisible(DockLiquidGlassView glass) {
        try {
            ViewParent parent = glass.getParent();
            if (parent instanceof View) {
                View host = (View) parent;
                if (host.getVisibility() != View.VISIBLE) host.setVisibility(View.VISIBLE);
            }
        } catch (Throwable error) {
            MainHook.log("[DC] workstation glass host restore failed: " + error);
        }
    }

    private static void revealNativeBackdrop(Object glass, String reason) {
        try {
            Object geometrySource = HookUtil.getField(glass, "geometrySource");
            if (geometrySource instanceof View) {
                ((View) geometrySource).setAlpha(1f);
            }
            HookUtil.setField(glass, "nativeBackgroundHiddenByGlass", false);
            MainHook.log("[DC] workstation native backdrop fallback reason=" + reason);
        } catch (Throwable error) {
            MainHook.log("[DC] workstation native backdrop fallback failed: " + error);
        }
    }
}
