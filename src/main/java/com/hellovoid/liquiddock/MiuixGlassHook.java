package com.hellovoid.liquiddock;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import java.lang.reflect.Field;

/**
 * MiuiX-specific glass installer for OS3.0.307+ docks.
 *
 * The vendor background remains only as a geometry source. Its compositor/pass-window blur is
 * disabled so LiquidDock's existing Prismal glass stack owns the actual blur and optical pass.
 * The generic Launcher
 * gesture/Recents capture hooks stay disabled; only SystemUI HOME/APP ownership is rebound so
 * the glass can choose FULL_DISPLAY while the floating Dock is shown over an app.
 */
final class MiuixGlassHook {
    private static final String TAG = "[DC][MG]";
    private static final float SQUIRCLE_CP = 0.58f;
    private static final String NATIVE_BACKGROUND_CLASS =
            "com.miui.home.launcher.hotseats.HotSeatsListContentMiuiXBlurBackground";
    private static final String COMPAT_BACKGROUND_CLASS =
            "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2";

    private static DockLiquidGlassView glassRef;
    private static DockLiquidGlassHostView hostRef;
    private static View backgroundRef;
    private static ViewTreeObserver vendorBlurObserver;
    private static ViewTreeObserver.OnPreDrawListener vendorBlurSuppressor;
    private static View vendorGpuBlurLoggedFor;
    // BlurBackground2 can issue the same hard-coded utility blur repeatedly during layout.
    // Keep one concise diagnostic per themed background instance.
    private static View compatBackgroundBlurLoggedFor;

    private MiuixGlassHook() {}

    /** True only when this exact vendor background instance still owns the live Prismal host. */
    static boolean isBoundTo(View dockBg) {
        if (dockBg == null || dockBg != backgroundRef) return false;
        ViewGroup parent = dockBg.getParent() instanceof ViewGroup
                ? (ViewGroup) dockBg.getParent() : null;
        DockLiquidGlassHostView host = hostRef;
        return parent != null && host != null && host.getParent() == parent;
    }

    /**
     * BlurBackground2.addBlur() routes a positive vendor blur radius through this utility before
     * it reaches hidden View background-blur APIs. theme(3) shows that even radius=5 becomes a
     * SurfaceFlinger region blur on the whole Floating Dock, post-processing Prismal. For the
     * exact themed HotSeats background, suppress every positive vendor radius to zero. Disable
     * calls and every other BlurUtilities consumer pass through unchanged.
     */
    static int suppressCompatBackgroundBlurRadius(View dockBg, int requestedRadius) {
        if (dockBg == null || requestedRadius <= 0) return requestedRadius;
        if (!COMPAT_BACKGROUND_CLASS.equals(dockBg.getClass().getName())) return requestedRadius;
        if (compatBackgroundBlurLoggedFor != dockBg) {
            compatBackgroundBlurLoggedFor = dockBg;
            MainHook.log(TAG + " compat BlurBackground2 GPU background blur suppressed "
                    + requestedRadius + " -> 0");
        }
        return 0;
    }

    /**
     * Native 307 emits its toHome state before the Launcher icon-flight animation starts.
     * Reuse DockLiquidGlassView's existing gesture target barrier so HOME becomes wallpaper-
     * backed immediately and every in-flight APP bitmap becomes stale by scene revision.
     */
    static void onHomeTransitionStart() {
        DockLiquidGlassView glass = glassRef;
        if (glass == null) return;
        glass.setGestureCaptureTarget("HOME");
        MainHook.log(TAG + " native toHome -> HOME wallpaper capture target");
    }

    static boolean install(View dockBg, View workspace, LiquidDockConfig config,
                           Object launcher, ClassLoader cl) {
        if (dockBg == null || config == null) return false;
        ViewGroup parent = dockBg.getParent() instanceof ViewGroup
                ? (ViewGroup) dockBg.getParent() : null;
        if (parent == null) return false;
        boolean nativeMaterial = isNativeMaterialBackground(dockBg);
        boolean nativeVisualOwner = isNativeVisualOwner(dockBg);

        if (backgroundRef == dockBg && hostRef != null && hostRef.getParent() == parent) {
            syncSize(dockBg);
            syncGeometry(dockBg, config);
            return true;
        }

        // Detached/old host: never stack a second glass layer on a recreated Dock hierarchy.
        removeVendorGpuBlurSuppressor();
        if (hostRef != null && hostRef.getParent() instanceof ViewGroup) {
            ((ViewGroup) hostRef.getParent()).removeView(hostRef);
        }
        hostRef = null;
        glassRef = null;
        backgroundRef = null;
        vendorGpuBlurLoggedFor = null;
        compatBackgroundBlurLoggedFor = null;

        // Both 307 backgrounds can attach pass-window blur to the whole Floating Dock Surface.
        // Disable that compositor stage before Prismal is installed; the vendor View remains only
        // as geometry until DockLiquidGlassView hides it after the first valid capture.
        if (nativeVisualOwner) suppressVendorGpuBlur(dockBg);

        float radius = readRadius(dockBg);
        int dockW = readDimension(dockBg, "mWidth", true);
        int dockH = readDimension(dockBg, "mHeight", false);
        MainHook.log(TAG + " detected radius=" + radius
                + " dock size=" + dockW + "x" + dockH);

        DockLiquidGlassView glass = LiquidGlassFactory.create(
                dockBg, workspace, config.glass, config.dock, true, SQUIRCLE_CP);
        glass.setId(View.generateViewId());
        // The specialized 307 path bypasses MainHook's legacy capture hooks. Rebind only the
        // authoritative SystemUI HOME/APP ownership so APP resolves to FULL_DISPLAY instead of
        // remaining UNKNOWN -> WALLPAPER. Force the composed-display capability for this mode;
        // historical preferences must not turn an app backdrop back into wallpaper.
        glass.setFullscreenCapture(true);
        // 307 uses the same user-facing capture controls as legacy Liquid Glass. Keep these
        // explicit here so this specialized path can never drift back to demo constants.
        glass.setCaptureScale(config.glass.captureScale);
        glass.setCapturePowerLimitFps(config.glass.captureFps);
        // LiquidGlassFactory already applied config.blur/config.blurMode. Do not override them:
        // the GPU capture is the input and Prismal itself owns blur/refraction/highlight.
        MainHook.log(TAG + " capture tuning fps=" + config.glass.captureFps
                + " scale=" + config.glass.captureScale);

        DockLiquidGlassHostView host = new DockLiquidGlassHostView(parent.getContext());
        host.setId(View.generateViewId());
        host.setLayers(glass);
        host.setGeometry(radius, true, SQUIRCLE_CP);
        host.reloadOverlay(config.dock, config.glass);

        FrameLayout.LayoutParams hostLp = new FrameLayout.LayoutParams(
                dockW > 0 ? dockW : ViewGroup.LayoutParams.MATCH_PARENT,
                dockH > 0 ? dockH : ViewGroup.LayoutParams.MATCH_PARENT);
        hostLp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;

        int bgIdx = parent.indexOfChild(dockBg);
        int insertIndex = bgIdx < 0 ? parent.getChildCount()
                : Math.min(parent.getChildCount(), bgIdx + 1);
        parent.addView(host, insertIndex, hostLp);

        // Keep the vendor View only as DockLiquidGlassView's geometry source. Once a capture is
        // installed, the normal glass lifecycle hides that source; do not restore its alpha/latch.
        backgroundRef = dockBg;
        glassRef = glass;
        hostRef = host;
        if (nativeVisualOwner) suppressVendorGpuBlur(dockBg);
        installVendorGpuBlurSuppressor(dockBg);
        HomeOwnershipRuntime.bind(glass, glass.getContext());

        DockStrokeRenderer.configure(dockBg, config.dock, radius);
        MainHook.log(TAG + " Prismal owns blur; native 307 background is geometry only class="
                + dockBg.getClass().getSimpleName());
        return true;
    }

    /** Width/height animation path: no config I/O or stroke/material rebuild. */
    static void syncSize(View dockBg) {
        if (dockBg == null || dockBg != backgroundRef) return;
        DockLiquidGlassHostView host = hostRef;
        if (host == null || host.getParent() == null) return;

        int dockW = readDimension(dockBg, "mWidth", true);
        int dockH = readDimension(dockBg, "mHeight", false);
        ViewGroup.LayoutParams params = host.getLayoutParams();
        if (params != null) {
            boolean changed = false;
            if (dockW > 0 && params.width != dockW) {
                params.width = dockW;
                changed = true;
            }
            if (dockH > 0 && params.height != dockH) {
                params.height = dockH;
                changed = true;
            }
            if (changed) host.setLayoutParams(params);
        }
        if (isNativeVisualOwner(dockBg)) suppressVendorGpuBlur(dockBg);
        host.setVisibility(dockBg.getVisibility());
        host.invalidate();
    }

    /** Radius/material path: only called when the vendor radius changes. */
    static void syncGeometry(View dockBg, LiquidDockConfig config) {
        if (dockBg == null || config == null || dockBg != backgroundRef) return;
        DockLiquidGlassHostView host = hostRef;
        if (host == null || host.getParent() == null) return;

        if (isNativeVisualOwner(dockBg)) suppressVendorGpuBlur(dockBg);

        float radius = readRadius(dockBg);
        host.setVisibility(dockBg.getVisibility());
        host.setGeometry(radius, true, SQUIRCLE_CP);
        host.reloadOverlay(config.dock, config.glass);
        DockStrokeRenderer.configure(dockBg, config.dock, radius);
        host.invalidate();
    }

    private static boolean isNativeMaterialBackground(View dockBg) {
        return dockBg != null && NATIVE_BACKGROUND_CLASS.equals(dockBg.getClass().getName());
    }

    /** Both supported 307 implementations are vendor geometry sources for the injected host. */
    private static boolean isNativeVisualOwner(View dockBg) {
        if (dockBg == null) return false;
        String name = dockBg.getClass().getName();
        return NATIVE_BACKGROUND_CLASS.equals(name) || COMPAT_BACKGROUND_CLASS.equals(name);
    }

    /**
     * Disable every vendor compositor/pass-window blur stage on the bound 307 background.
     * SurfaceFlinger applies that effect to the Floating Dock Surface after child composition,
     * so leaving even the correct radius active would blur/cover Prismal's rendered output.
     */
    static void suppressVendorGpuBlur(View dockBg) {
        if (dockBg == null || !isNativeVisualOwner(dockBg)) return;
        // Radius-zero first gives a narrow fail-safe even if one hidden disable entry point fails.
        MiBlurBridge.setPassWindowBlurRadius(dockBg, 0);
        MiBlurBridge.clearPassWindowBlur(dockBg);
        if (vendorGpuBlurLoggedFor != dockBg) {
            vendorGpuBlurLoggedFor = dockBg;
            MainHook.log(TAG + " vendor GPU background blur disabled; Prismal owns blur class="
                    + dockBg.getClass().getSimpleName());
        }
    }

    /**
     * HyperOS can reapply its material state during animation without replacing the background.
     * Reassert only GPU-blur suppression before draw. Crucially, this listener never restores
     * dockBg alpha and never touches the glass view hidden-source latch: the
     * ordinary glass lifecycle must be free to hide the native geometry source after capture.
     */
    private static void installVendorGpuBlurSuppressor(View dockBg) {
        removeVendorGpuBlurSuppressor();
        View root = dockBg.getRootView();
        ViewTreeObserver observer = root != null ? root.getViewTreeObserver() : null;
        if (observer == null || !observer.isAlive()) return;

        ViewTreeObserver.OnPreDrawListener listener = () -> {
            if (backgroundRef == dockBg) suppressVendorGpuBlur(dockBg);
            return true;
        };
        observer.addOnPreDrawListener(listener);
        vendorBlurObserver = observer;
        vendorBlurSuppressor = listener;

        dockBg.post(() -> {
            if (backgroundRef == dockBg) suppressVendorGpuBlur(dockBg);
        });
    }

    private static void removeVendorGpuBlurSuppressor() {
        ViewTreeObserver observer = vendorBlurObserver;
        ViewTreeObserver.OnPreDrawListener listener = vendorBlurSuppressor;
        vendorBlurObserver = null;
        vendorBlurSuppressor = null;
        if (observer == null || listener == null) return;
        try {
            if (observer.isAlive()) observer.removeOnPreDrawListener(listener);
        } catch (Throwable ignored) {}
    }

    private static int readDimension(View dockBg, String fieldName, boolean width) {
        int fallback = width ? dockBg.getWidth() : dockBg.getHeight();
        ViewGroup.LayoutParams lp = dockBg.getLayoutParams();
        if (fallback <= 0 && lp != null) {
            int fromLp = width ? lp.width : lp.height;
            if (fromLp > 0) fallback = fromLp;
        }
        try {
            Field field = findField(dockBg.getClass(), fieldName);
            field.setAccessible(true);
            Object value = field.get(dockBg);
            if (value instanceof Integer && (Integer) value > 0) return (Integer) value;
        } catch (Throwable ignored) {}
        return fallback;
    }

    private static float readRadius(View dockBg) {
        try {
            Field field = findField(dockBg.getClass(), "mBackground");
            field.setAccessible(true);
            Object value = field.get(dockBg);
            if (value instanceof GradientDrawable) {
                float radius = ((GradientDrawable) value).getCornerRadius();
                if (radius >= 0f) return radius;
            }
        } catch (Throwable ignored) {}

        Drawable drawable = dockBg.getBackground();
        if (drawable instanceof GradientDrawable) {
            return Math.max(0f, ((GradientDrawable) drawable).getCornerRadius());
        }
        int w = readDimension(dockBg, "mWidth", true);
        int h = readDimension(dockBg, "mHeight", false);
        if (w > 0 && h > 0) return Math.min(w, h) * 0.22f;
        return 30f;
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
