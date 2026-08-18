package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

/** Builds the experimental MiuiX 307 zero-readback composition. */
final class Miuix307ZeroCopyRenderer {
    private static final String TAG = "[DC][ZC]";
    private static WeakReference<Miuix307ZeroCopyBackdropView> backdropRef =
            new WeakReference<>(null);

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

        host.removeAllViews();
        host.addView(backdrop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        host.reloadOpticsPreservingGeometry(glassConfig);
        if (!enableSharpOptics(host)) {
            host.removeView(backdrop);
            backdrop.clearBlur();
            MainHook.log(TAG + " sharp optical overlay activation unavailable");
            return false;
        }

        backdropRef = new WeakReference<>(backdrop);
        return true;
    }

    static Miuix307ZeroCopyBackdropView currentBackdrop() {
        return backdropRef.get();
    }

    static void clear() {
        Miuix307ZeroCopyBackdropView backdrop = backdropRef.get();
        backdropRef = new WeakReference<>(null);
        if (backdrop != null) backdrop.clearBlur();
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
