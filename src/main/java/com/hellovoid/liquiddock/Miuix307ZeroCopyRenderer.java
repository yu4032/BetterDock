package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Builds the experimental MiuiX 307 zero-readback composition. */
final class Miuix307ZeroCopyRenderer {
    private static final String TAG = "[DC][ZC]";
    private static WeakReference<Miuix307ZeroCopyBackdropView> backdropRef =
            new WeakReference<>(null);
    private static WeakReference<Miuix307ZeroCopyToneView> toneRef =
            new WeakReference<>(null);
    private static WeakReference<DockLiquidGlassHostView> hostRef =
            new WeakReference<>(null);
    private static WeakReference<View> materialHostRef = new WeakReference<>(null);

    private Miuix307ZeroCopyRenderer() {}

    static boolean install(ViewGroup materialHost, DockLiquidGlassHostView host,
                           LiquidDockConfig.Glass glassConfig, int blurRadiusPx) {
        if (materialHost == null || host == null || glassConfig == null) return false;
        if (!MiBlurBridge.isPassWindowBlurAvailable()) {
            MainHook.log(TAG + " pass-window blur API unavailable");
            return false;
        }

        Miuix307ZeroCopyBackdropView backdrop = new Miuix307ZeroCopyBackdropView(
                materialHost.getContext(), blurRadiusPx);
        backdrop.setId(View.generateViewId());
        backdrop.setGlassRadius(readHostRadius(host));
        Miuix307ZeroCopyToneView tone = new Miuix307ZeroCopyToneView(
                materialHost.getContext(), glassConfig);
        tone.setId(View.generateViewId());

        FrameLayout.LayoutParams match = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        host.removeAllViews();
        host.addView(backdrop, match);
        host.addView(tone, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        host.reloadOpticsPreservingGeometry(glassConfig);
        if (!enableSharpOptics(host)) {
            host.removeAllViews();
            backdrop.clearBlur();
            MainHook.log(TAG + " sharp optical overlay activation unavailable");
            return false;
        }

        backdropRef = new WeakReference<>(backdrop);
        toneRef = new WeakReference<>(tone);
        hostRef = new WeakReference<>(host);
        materialHostRef = new WeakReference<>(materialHost);

        // The host is attached by MiuixGlassHook immediately after install(). Apply the vendor
        // compositor material on the next frame, then restore the user-selected blur radius so
        // material type/gradient parameters are inherited without stealing GUI radius ownership.
        backdrop.postOnAnimation(() -> {
            if (backdropRef.get() != backdrop) return;
            if (Miuix307CompositorOpticsBridge.applyVendorBlurConfig(materialHost, backdrop)) {
                backdrop.setBlurRadius(blurRadiusPx);
            }
        });
        return true;
    }

    static Miuix307ZeroCopyBackdropView currentBackdrop() {
        return backdropRef.get();
    }

    static void sync(LiquidDockConfig.Glass glassConfig, int blurRadiusPx) {
        Miuix307ZeroCopyBackdropView backdrop = backdropRef.get();
        DockLiquidGlassHostView host = hostRef.get();
        View materialHost = materialHostRef.get();
        if (backdrop != null) {
            if (host != null) backdrop.setGlassRadius(readHostRadius(host));
            if (materialHost != null) {
                Miuix307CompositorOpticsBridge.applyVendorBlurConfig(materialHost, backdrop);
            }
            backdrop.setBlurRadius(blurRadiusPx);
        }
        Miuix307ZeroCopyToneView tone = toneRef.get();
        if (tone != null) tone.setTone(glassConfig);
    }

    static void clear() {
        Miuix307ZeroCopyBackdropView backdrop = backdropRef.get();
        backdropRef = new WeakReference<>(null);
        toneRef = new WeakReference<>(null);
        hostRef = new WeakReference<>(null);
        materialHostRef = new WeakReference<>(null);
        if (backdrop != null) backdrop.clearBlur();
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

    /**
     * Reuse the existing host's tested ADVANCED sharp highlight pass without duplicating its
     * RuntimeShader. This reflection is internal to LiquidDock and can be replaced by a direct
     * package-private host API after the zero-copy device experiment is validated.
     */
    private static boolean enableSharpOptics(DockLiquidGlassHostView host) {
        try {
            Method method = DockLiquidGlassHostView.class.getDeclaredMethod(
                    "setActiveBlurBackend", LiquidBlurMode.class);
            method.setAccessible(true);
            method.invoke(host, LiquidBlurMode.ADVANCED_MATERIAL);
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " host optical backend unavailable: " + error);
            return false;
        }
    }
}
