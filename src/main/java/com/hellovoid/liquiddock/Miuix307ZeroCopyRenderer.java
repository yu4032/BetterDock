package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

/** Builds the optically neutral HyperOS 307 PassBlur -> OES diagnostic composition. */
final class Miuix307ZeroCopyRenderer {
    private static final String TAG = "[DC][ZC]";

    private static WeakReference<Miuix307PassBlurGpuView> gpuBackdropRef =
            new WeakReference<>(null);
    private static WeakReference<DockLiquidGlassHostView> hostRef =
            new WeakReference<>(null);
    private static WeakReference<View> materialHostRef = new WeakReference<>(null);

    private Miuix307ZeroCopyRenderer() {}

    static boolean install(ViewGroup materialHost, DockLiquidGlassHostView host,
                           LiquidDockConfig.Glass glassConfig, int blurRadiusPx) {
        if (materialHost == null || host == null || glassConfig == null) return false;

        if (!Miuix307CompositorOpticsBridge.usesExactBackgroundBlur(materialHost)) {
            MainHook.log(TAG + " PassBlur GLES demo unsupported source="
                    + materialHost.getClass().getSimpleName());
            return false;
        }

        Miuix307PassBlurGpuView gpuBackdrop = new Miuix307PassBlurGpuView(
                materialHost.getContext(), materialHost);
        gpuBackdrop.setId(View.generateViewId());
        gpuBackdrop.setGlassRadius(readHostRadius(host));

        // The neutral path keeps tone/tint and the old advanced optical highlight disabled. The
        // shell's replacement foreground stroke is allowed back above the independent GPU surface;
        // MiuixGlassHook configures it with no vendor/material foreground underneath.
        host.removeAllViews();
        host.addView(gpuBackdrop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        gpuBackdropRef = new WeakReference<>(gpuBackdrop);
        hostRef = new WeakReference<>(host);
        materialHostRef = new WeakReference<>(materialHost);
        MainHook.log(TAG + " PassBlur GLES neutral lens installed; awaiting first GPU frame"
                + " requestedBlur=" + blurRadiusPx);
        return true;
    }

    static boolean isInstalled() {
        return gpuBackdropRef.get() != null;
    }

    static boolean isActive() {
        Miuix307PassBlurGpuView gpuBackdrop = gpuBackdropRef.get();
        return gpuBackdrop != null && gpuBackdrop.isGpuBackdropActive();
    }

    static boolean isActivationExhausted() {
        Miuix307PassBlurGpuView gpuBackdrop = gpuBackdropRef.get();
        return gpuBackdrop == null || gpuBackdrop.isActivationExhausted();
    }

    static int activeWidth() {
        Miuix307PassBlurGpuView gpuBackdrop = gpuBackdropRef.get();
        return gpuBackdrop != null ? gpuBackdrop.getWidth() : 0;
    }

    static int activeHeight() {
        Miuix307PassBlurGpuView gpuBackdrop = gpuBackdropRef.get();
        return gpuBackdrop != null ? gpuBackdrop.getHeight() : 0;
    }

    static void sync(LiquidDockConfig.Glass glassConfig, int blurRadiusPx) {
        Miuix307PassBlurGpuView gpuBackdrop = gpuBackdropRef.get();
        DockLiquidGlassHostView host = hostRef.get();
        if (gpuBackdrop != null) {
            gpuBackdrop.setGlassRadius(host != null ? readHostRadius(host) : 0f);
        }
    }

    static void clear() {
        Miuix307PassBlurGpuView gpuBackdrop = gpuBackdropRef.get();
        gpuBackdropRef = new WeakReference<>(null);
        hostRef = new WeakReference<>(null);
        materialHostRef = new WeakReference<>(null);
        if (gpuBackdrop != null) gpuBackdrop.shutdown();
    }

    private static float readHostRadius(DockLiquidGlassHostView host) {
        if (host == null) return 0f;
        try {
            Field field = DockLiquidGlassHostView.class.getDeclaredField("radius");
            field.setAccessible(true);
            Object value = field.get(host);
            if (value instanceof Number) return Math.max(0f, ((Number) value).floatValue());
        } catch (Throwable error) {
            MainHook.log(TAG + " host radius reflection unavailable: " + error);
        }
        int min = Math.min(host.getWidth(), host.getHeight());
        return min > 0 ? min * 0.5f : 0f;
    }
}
