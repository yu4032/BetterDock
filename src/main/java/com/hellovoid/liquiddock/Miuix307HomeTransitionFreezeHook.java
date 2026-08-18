package com.hellovoid.liquiddock;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Flash-free APP -> HOME hand-off for the specialized MiuiX 307 pipeline.
 *
 * HyperOS emits GestureToHome / StateNotifyUtils "toHome" before the icon-flight transition has
 * finished. Switching DockLiquidGlassView to HOME at that early boundary exposes the wallpaper
 * for one or more frames. Keep the last valid APP bitmap installed instead, invalidate any
 * in-flight readback, and suppress new captures until the existing focus-home settle request is
 * actually due. No new timer is introduced here: HomeOwnershipRuntime + onLauncherFocused remain
 * authoritative for the HOME boundary and settle delay.
 */
final class Miuix307HomeTransitionFreezeHook {
    private static final String TAG = "[DC][MG]";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private static volatile boolean frozen;
    private static WeakReference<DockLiquidGlassView> frozenGlass = new WeakReference<>(null);

    private Miuix307HomeTransitionFreezeHook() {}

    static void install() {
        if (!INSTALLED.compareAndSet(false, true)) return;
        installNativeHomeBoundaryOverride();
        installCaptureRequestGate();
        MainHook.log(TAG + " 307 HOME transition backdrop freeze installed");
    }

    /** Replace the old immediate setGestureCaptureTarget("HOME") body on 307 only. */
    private static void installNativeHomeBoundaryOverride() {
        try {
            HookUtil.hookMethod(MiuixGlassHook.class, "onHomeTransitionStart",
                    new Class<?>[0], chain -> {
                        if (!Miuix307MaterialPipeline.isInstalled()) {
                            return chain.proceed(chain.getArgs().toArray(new Object[0]));
                        }
                        DockLiquidGlassView glass = boundGlass();
                        if (glass == null) {
                            return chain.proceed(chain.getArgs().toArray(new Object[0]));
                        }
                        freezeLastAppBackdrop(glass, "native-toHome");
                        // Suppress MiuixGlassHook's legacy immediate HOME/wallpaper target.
                        return null;
                    });
            MainHook.log(TAG + " native toHome immediate-wallpaper override installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " native toHome freeze override unavailable: " + error);
        }
    }

    /**
     * Freeze capture scheduling, not rendering. The currently installed bitmap remains owned by
     * DockLiquidGlassView and continues drawing while the app task/icon animates toward HOME.
     */
    private static void installCaptureRequestGate() {
        try {
            HookUtil.hookMethod(DockLiquidGlassView.class, "requestStateCapture",
                    new Class<?>[]{String.class}, chain -> {
                        Object owner = chain.getThisObject();
                        if (!(owner instanceof DockLiquidGlassView)
                                || !Miuix307MaterialPipeline.isInstalled()) {
                            return chain.proceed(chain.getArgs().toArray(new Object[0]));
                        }

                        DockLiquidGlassView glass = (DockLiquidGlassView) owner;
                        if (!isFrozenFor(glass)) {
                            return chain.proceed(chain.getArgs().toArray(new Object[0]));
                        }

                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        String reason = args.length > 0 && args[0] instanceof String
                                ? (String) args[0] : "";
                        if (shouldReleaseFor(glass, reason)) {
                            releaseFrozenBackdrop(glass, reason);
                            return chain.proceed(args);
                        }

                        // Do not call the original: even scheduling a capture here can replace the
                        // preserved APP frame as soon as the next main-loop turn runs.
                        return null;
                    });
            MainHook.log(TAG + " HOME transition capture-request gate installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " HOME transition capture-request gate unavailable: " + error);
        }
    }

    private static void freezeLastAppBackdrop(DockLiquidGlassView glass, String reason) {
        if (glass == null) return;
        if (isFrozenFor(glass)) return;

        frozenGlass = new WeakReference<>(glass);
        frozen = true;
        // This invalidates queued/in-flight attempts by generation/attempt ownership, but the
        // method deliberately does not recycle capture/captureShader. The last APP frame stays.
        HookUtil.invoke(glass, "cancelPendingCaptureWork");
        MainHook.log(TAG + " HOME transition backdrop frozen reason=" + reason
                + "; preserving installed APP frame");
    }

    private static boolean shouldReleaseFor(DockLiquidGlassView glass, String reason) {
        if (reason == null) return false;

        // Normal path: HomeOwnershipRuntime first confirms HOME, then onLauncherFocused posts the
        // existing configurable settle delay. Ignore an old/stale focus-home runnable while APP
        // is still authoritative.
        if ("focus-home".equals(reason)) {
            return launcherResumed(glass);
        }

        // Redirect/cancellation safety. Exact Overview may replace HOME, and a brand-new Dock
        // touch while APP is still authoritative must not inherit a stale HOME freeze.
        if (reason.startsWith("overview-enter-")) return true;
        return "dock-touch".equals(reason) && !launcherResumed(glass);
    }

    private static boolean launcherResumed(DockLiquidGlassView glass) {
        try {
            return HookUtil.getBooleanField(glass, "launcherLifecycleKnown")
                    && HookUtil.getBooleanField(glass, "launcherResumed");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void releaseFrozenBackdrop(DockLiquidGlassView glass, String reason) {
        if (!isFrozenFor(glass)) return;
        frozen = false;
        frozenGlass = new WeakReference<>(null);
        MainHook.log(TAG + " HOME transition backdrop released reason=" + reason);
    }

    private static boolean isFrozenFor(DockLiquidGlassView glass) {
        return frozen && glass != null && frozenGlass.get() == glass;
    }

    private static DockLiquidGlassView boundGlass() {
        try {
            Object value = HookUtil.findField(MiuixGlassHook.class, "glassRef").get(null);
            return value instanceof DockLiquidGlassView ? (DockLiquidGlassView) value : null;
        } catch (Throwable error) {
            MainHook.log(TAG + " HOME transition bound glass unavailable: " + error);
            return null;
        }
    }
}
