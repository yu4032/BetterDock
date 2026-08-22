package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;

/** Builds the feedback-safe HyperOS 307 PassBlur -> OES -> TextureView material composition. */
final class Miuix307ZeroCopyRenderer {
    private static final String TAG = "[DC][ZC]";
    private static final long FIRST_FRAME_TIMEOUT_MS = 1500L;
    private static final int MAX_STALLED_PRODUCER_RECOVERIES = 2;

    private static WeakReference<Miuix307PassBlurTextureView> gpuBackdropRef =
            new WeakReference<>(null);
    private static WeakReference<DockLiquidGlassHostView> hostRef =
            new WeakReference<>(null);
    private static WeakReference<View> materialHostRef = new WeakReference<>(null);
    private static Runnable firstFrameWatchdog;
    private static int watchdogGeneration;
    private static int stalledProducerRecoveries;

    private Miuix307ZeroCopyRenderer() {}

    static boolean install(ViewGroup materialHost, DockLiquidGlassHostView host,
                           LiquidDockConfig.Glass glassConfig, int blurRadiusPx) {
        if (materialHost == null || host == null || glassConfig == null) return false;

        // The current zero-copy backend binds SurfaceFlinger's PassBlur producer directly to the
        // Floating Dock root through SetPassBlurSurface. It does not depend on the themed
        // BlurBackground2#setBackgroundBlur path, so both supported HotSeats material owners must
        // reach the same TextureView renderer.
        Miuix307PassBlurTextureView gpuBackdrop = new Miuix307PassBlurTextureView(
                materialHost.getContext(), materialHost);
        gpuBackdrop.setGlassConfig(glassConfig);
        gpuBackdrop.setId(View.generateViewId());

        // Prismal optics are evaluated in Dock-local UV space over the zero-copy OES backdrop.
        // The shell's safe foreground stroke may remain above the TextureView because it does not
        // alter producer geometry or backdrop sampling.
        host.removeAllViews();
        host.addView(gpuBackdrop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        cancelFirstFrameWatchdog();
        stalledProducerRecoveries = 0;
        gpuBackdropRef = new WeakReference<>(gpuBackdrop);
        hostRef = new WeakReference<>(host);
        materialHostRef = new WeakReference<>(materialHost);
        armFirstFrameWatchdog(gpuBackdrop, "install");
        MainHook.log(TAG + " PassBlur TextureView EGL Prismal material installed; awaiting first GPU frame"
                + " requestedBlur=" + blurRadiusPx
                + " source=" + materialHost.getClass().getSimpleName());
        return true;
    }

    static boolean isInstalled() {
        return gpuBackdropRef.get() != null;
    }

    static boolean isActive() {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        boolean active = gpuBackdrop != null && gpuBackdrop.isGpuBackdropActive();
        if (active) {
            stalledProducerRecoveries = 0;
            cancelFirstFrameWatchdog();
        } else if (gpuBackdrop != null && !gpuBackdrop.isActivationExhausted()) {
            armFirstFrameWatchdog(gpuBackdrop, "inactive-check");
        }
        return active;
    }

    static boolean isActivationExhausted() {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        return gpuBackdrop == null || gpuBackdrop.isActivationExhausted();
    }

    static int activeWidth() {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        return gpuBackdrop != null ? gpuBackdrop.getWidth() : 0;
    }

    static int activeHeight() {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        return gpuBackdrop != null ? gpuBackdrop.getHeight() : 0;
    }

    static void sync(LiquidDockConfig.Glass glassConfig, int blurRadiusPx) {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        if (gpuBackdrop != null && glassConfig != null) {
            gpuBackdrop.setGlassConfig(glassConfig);
            if (!gpuBackdrop.isGpuBackdropActive() && !gpuBackdrop.isActivationExhausted()) {
                armFirstFrameWatchdog(gpuBackdrop, "geometry-sync");
            }
        }
    }

    static void rebindProducer(String reason) {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        if (gpuBackdrop == null) return;
        cancelFirstFrameWatchdog();
        gpuBackdrop.rebindProducer(reason);
        armFirstFrameWatchdog(gpuBackdrop, reason);
    }

    private static synchronized void armFirstFrameWatchdog(
            Miuix307PassBlurTextureView gpuBackdrop, String reason) {
        if (gpuBackdrop == null || gpuBackdrop != gpuBackdropRef.get()) return;
        if (gpuBackdrop.isGpuBackdropActive() || gpuBackdrop.isActivationExhausted()) return;
        if (firstFrameWatchdog != null) return;

        final int generation = ++watchdogGeneration;
        Runnable watchdog = () -> {
            synchronized (Miuix307ZeroCopyRenderer.class) {
                if (generation != watchdogGeneration || gpuBackdrop != gpuBackdropRef.get()) return;
                firstFrameWatchdog = null;
            }
            if (gpuBackdrop.isGpuBackdropActive()) {
                stalledProducerRecoveries = 0;
                return;
            }
            if (gpuBackdrop.isActivationExhausted()
                    || stalledProducerRecoveries >= MAX_STALLED_PRODUCER_RECOVERIES) {
                MainHook.log(TAG + " producer-stall-exhausted reason=" + reason
                        + " recoveries=" + stalledProducerRecoveries);
                clear();
                return;
            }

            stalledProducerRecoveries++;
            MainHook.log(TAG + " producer first-frame timeout; bounded rebind "
                    + stalledProducerRecoveries + "/" + MAX_STALLED_PRODUCER_RECOVERIES
                    + " reason=" + reason);
            rebindProducer("producer-stall-" + stalledProducerRecoveries);
        };
        firstFrameWatchdog = watchdog;
        gpuBackdrop.postDelayed(watchdog, FIRST_FRAME_TIMEOUT_MS);
    }

    private static synchronized void cancelFirstFrameWatchdog() {
        watchdogGeneration++;
        Runnable watchdog = firstFrameWatchdog;
        firstFrameWatchdog = null;
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        if (watchdog != null && gpuBackdrop != null) gpuBackdrop.removeCallbacks(watchdog);
    }

    static void clear() {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        cancelFirstFrameWatchdog();
        stalledProducerRecoveries = 0;
        gpuBackdropRef = new WeakReference<>(null);
        hostRef = new WeakReference<>(null);
        materialHostRef = new WeakReference<>(null);
        if (gpuBackdrop != null) gpuBackdrop.shutdown();
    }
}
