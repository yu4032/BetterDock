package com.hellovoid.liquiddock;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Flash-free APP -> HOME hand-off for the specialized MiuiX 307 pipeline.
 *
 * HyperOS emits GestureToHome / StateNotifyUtils "toHome" before the icon-flight transition has
 * finished. Switching DockLiquidGlassView to HOME at that early boundary exposes the wallpaper
 * for one or more frames. Keep the last valid APP bitmap installed instead, invalidate any
 * in-flight readback, and suppress new captures while SystemUI ownership converges to HOME.
 */
final class Miuix307HomeTransitionFreezeHook {
    private static final String TAG = "[DC][MG]";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private static volatile boolean frozen;
    private static volatile int freezeGeneration;
    private static WeakReference<DockLiquidGlassView> frozenGlass = new WeakReference<>(null);

    private Miuix307HomeTransitionFreezeHook() {}

    static void install() {
        if (!INSTALLED.compareAndSet(false, true)) return;
        installNativeHomeBoundaryOverride();
        installHomeFocusSettleFallback();
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
                        boolean started = freezeLastAppBackdrop(glass, "native-toHome");
                        if (started) {
                            // The old immediate HOME target supplied a scene change but bypassed
                            // authoritative ownership. Once it is suppressed, explicitly refresh
                            // the request/response SystemUI baseline at the same native boundary.
                            HomeOwnershipRuntime.request("miuix307-toHome");
                        }
                        // Suppress MiuixGlassHook's legacy immediate HOME/wallpaper target.
                        return null;
                    });
            MainHook.log(TAG + " native toHome immediate-wallpaper override installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " native toHome freeze override unavailable: " + error);
        }
    }

    /**
     * Preferred release remains DockLiquidGlassView's existing focus-home request. That runnable
     * first checks isCaptureAllowed(); if the gate is temporarily closed it returns before issuing
     * focus-home, which used to leave this freeze permanent. Observe the same onLauncherFocused
     * boundary and schedule a same-delay fallback that only thaws the bitmap. The normal focus-home
     * path wins whenever it actually runs.
     */
    private static void installHomeFocusSettleFallback() {
        try {
            HookUtil.hookMethod(DockLiquidGlassView.class, "onLauncherFocused",
                    new Class<?>[0], chain -> {
                        Object owner = chain.getThisObject();
                        boolean wasAway = false;
                        if (owner instanceof DockLiquidGlassView) {
                            try {
                                wasAway = HookUtil.getBooleanField(owner, "launcherWasAway");
                            } catch (Throwable ignored) {}
                        }

                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (!(owner instanceof DockLiquidGlassView)
                                || !Miuix307MaterialPipeline.isInstalled()) {
                            return result;
                        }

                        DockLiquidGlassView glass = (DockLiquidGlassView) owner;
                        if (!isFrozenFor(glass)) return result;

                        long delay = wasAway
                                ? LiquidDockConfig.load().glass.homeSettleDelayMs
                                : 500L;
                        final int generation = freezeGeneration;
                        glass.postDelayed(() -> {
                            if (!Miuix307MaterialPipeline.isInstalled()
                                    || !isFrozenFor(glass)
                                    || freezeGeneration != generation) {
                                return;
                            }
                            releaseFrozenBackdrop(glass, "home-settle-fallback");
                            // This is only a trigger. If capture is still disallowed the normal
                            // view gate refuses it, but the stale APP bitmap is no longer locked.
                            HookUtil.invoke(glass, "requestStateCapture",
                                    "home-settle-fallback");
                        }, Math.max(0L, delay));
                        return result;
                    });
            MainHook.log(TAG + " HOME settle freeze fallback installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " HOME settle freeze fallback unavailable: " + error);
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

    private static boolean freezeLastAppBackdrop(DockLiquidGlassView glass, String reason) {
        if (glass == null || isFrozenFor(glass)) return false;

        freezeGeneration++;
        frozenGlass = new WeakReference<>(glass);
        frozen = true;
        // This invalidates queued/in-flight attempts by generation/attempt ownership, but the
        // method deliberately does not recycle capture/captureShader. The last APP frame stays.
        HookUtil.invoke(glass, "cancelPendingCaptureWork");
        MainHook.log(TAG + " HOME transition backdrop frozen reason=" + reason
                + " generation=" + freezeGeneration
                + "; preserving installed APP frame");
        return true;
    }

    private static boolean shouldReleaseFor(DockLiquidGlassView glass, String reason) {
        if (reason == null) return false;

        // Normal path: SystemUI has confirmed HOME and the existing configured focus settle is due.
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
        MainHook.log(TAG + " HOME transition backdrop released reason=" + reason
                + " generation=" + freezeGeneration);
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
