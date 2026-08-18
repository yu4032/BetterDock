package com.hellovoid.liquiddock;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import java.lang.reflect.Field;

/**
 * MiuiX-specific glass installer for OS3.0.307+ docks.
 *
 * The vendor background remains the authoritative Dock visual shell. Its parent-level
 * compositor blur stays disabled so it cannot post-process LiquidDock children. The primary 307
 * renderer is a dedicated zero-copy pass-window child; the proven capture renderer is retained as
 * a runtime fallback when that child cannot activate.
 */
final class MiuixGlassHook {
    private static final String TAG = "[DC][MG]";
    private static final String ZERO_COPY_TAG = "[DC][ZC]";
    private static final float SQUIRCLE_CP = 0.58f;
    private static final int ZERO_COPY_VALIDATION_FRAMES = 12;
    private static final String NATIVE_BACKGROUND_CLASS =
            "com.miui.home.launcher.hotseats.HotSeatsListContentMiuiXBlurBackground";
    private static final String COMPAT_BACKGROUND_CLASS =
            "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2";

    private static DockLiquidGlassView glassRef;
    private static Miuix307ZeroCopyBackdropView zeroCopyRef;
    private static DockLiquidGlassHostView hostRef;
    private static View backgroundRef;
    private static ViewTreeObserver vendorBlurObserver;
    private static ViewTreeObserver.OnPreDrawListener vendorBlurSuppressor;
    private static View vendorGpuBlurLoggedFor;
    private static View compatBackgroundBlurLoggedFor;
    private static View transparentMaterialOwner;
    private static GradientDrawable transparentMaterialBody;
    private static float transparentMaterialRadius = Float.NaN;
    private static View materialBodyLoggedFor;
    private static View zeroCopyActiveLoggedFor;

    private MiuixGlassHook() {}

    /** True only when this exact vendor background instance still owns the live host. */
    static boolean isBoundTo(View dockBg) {
        if (dockBg == null || dockBg != backgroundRef) return false;
        DockLiquidGlassHostView host = hostRef;
        return host != null && host.getParent() == dockBg;
    }

    static boolean isZeroCopyActive() {
        Miuix307ZeroCopyBackdropView backdrop = zeroCopyRef;
        DockLiquidGlassHostView host = hostRef;
        return backdrop != null && backdrop.isBlurActive()
                && host != null && host.getParent() == backgroundRef;
    }

    static Miuix307ZeroCopyBackdropView currentZeroCopyBackdrop() {
        return zeroCopyRef;
    }

    /**
     * A 307 material View exists before its real Dock geometry is committed. During Launcher
     * startup the themed BlurBackground2 can be attached with width=0 and mCornerRadius=0, then
     * receive its final geometry through the normal radius/measure callbacks. Never hand glass
     * ownership to that placeholder state.
     */
    static boolean hasReadyNativeGeometry(View dockBg) {
        if (dockBg == null || !isNativeVisualOwner(dockBg)) return false;
        if (!dockBg.isAttachedToWindow() || !(dockBg.getParent() instanceof ViewGroup)) {
            return false;
        }
        if (dockBg.getWidth() <= 0 || dockBg.getHeight() <= 0) return false;
        float radius = readRadius(dockBg);
        return !Float.isNaN(radius) && !Float.isInfinite(radius) && radius > 0.5f;
    }

    static float readNativeOpticsRadius(View dockBg) {
        return readRadius(dockBg);
    }

    /**
     * The themed 4.50 background can still request a parent-level region blur through this
     * utility. Zero-copy owns blur on its dedicated child instead, so the parent request remains
     * suppressed exactly like the capture renderer did.
     */
    static int suppressCompatBackgroundBlurRadius(View dockBg, int requestedRadius) {
        if (dockBg == null || requestedRadius <= 0) return requestedRadius;
        if (!COMPAT_BACKGROUND_CLASS.equals(dockBg.getClass().getName())) return requestedRadius;
        if (compatBackgroundBlurLoggedFor != dockBg) {
            compatBackgroundBlurLoggedFor = dockBg;
            MainHook.log(TAG + " compat BlurBackground2 parent GPU blur suppressed "
                    + requestedRadius + " -> 0");
        }
        return 0;
    }

    static boolean install(View dockBg, View workspace, LiquidDockConfig config,
                           Object launcher, ClassLoader cl) {
        if (!(dockBg instanceof ViewGroup) || config == null) return false;
        ViewGroup materialHost = (ViewGroup) dockBg;
        boolean nativeVisualOwner = isNativeVisualOwner(dockBg);

        if (backgroundRef == dockBg && hostRef != null && hostRef.getParent() == materialHost) {
            syncSize(dockBg);
            syncGeometry(dockBg, config);
            return true;
        }

        if (!hasReadyNativeGeometry(dockBg)) return false;

        removeVendorGpuBlurSuppressor();
        Miuix307ZeroCopyRenderer.clear();
        if (hostRef != null && hostRef.getParent() instanceof ViewGroup) {
            ((ViewGroup) hostRef.getParent()).removeView(hostRef);
        }
        hostRef = null;
        glassRef = null;
        zeroCopyRef = null;
        backgroundRef = null;
        vendorGpuBlurLoggedFor = null;
        compatBackgroundBlurLoggedFor = null;
        transparentMaterialOwner = null;
        transparentMaterialBody = null;
        transparentMaterialRadius = Float.NaN;
        materialBodyLoggedFor = null;
        zeroCopyActiveLoggedFor = null;

        if (nativeVisualOwner) suppressVendorGpuBlur(dockBg);

        float nativeRadius = readRadius(dockBg);
        suppressVendorMaterialBody(dockBg, nativeRadius);
        int dockW = readDimension(dockBg, "mWidth", true);
        int dockH = readDimension(dockBg, "mHeight", false);
        MainHook.log(TAG + " in-place material nativeOpticsRadius=" + nativeRadius
                + " dock size=" + dockW + "x" + dockH);

        DockLiquidGlassHostView host = new DockLiquidGlassHostView(dockBg.getContext());
        host.setId(View.generateViewId());
        host.setGeometry(nativeRadius, false, SQUIRCLE_CP);
        host.reloadOpticsPreservingGeometry(config.glass);

        boolean zeroCopyCandidate = Miuix307ZeroCopyRenderer.install(
                materialHost, host, config.glass, Math.round(config.glass.blur));

        FrameLayout.LayoutParams hostLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        materialHost.addView(host, materialHost.getChildCount(), hostLp);
        host.bringToFront();

        backgroundRef = dockBg;
        hostRef = host;
        zeroCopyRef = zeroCopyCandidate ? Miuix307ZeroCopyRenderer.currentBackdrop() : null;

        if (nativeVisualOwner) suppressVendorGpuBlur(dockBg);
        installVendorGpuBlurSuppressor(dockBg);

        if (zeroCopyCandidate && zeroCopyRef != null) {
            scheduleZeroCopyValidation(dockBg, workspace, config, host, zeroCopyRef, 0);
        } else {
            MainHook.log(ZERO_COPY_TAG + " zero-copy unavailable; capture fallback reason=install");
            installCaptureFallback(dockBg, workspace, config, host);
        }

        DockStrokeRenderer.configureReplacingForeground(dockBg, config.dock, nativeRadius);
        MainHook.log(TAG + " glass composed inside native 307 material shell class="
                + dockBg.getClass().getSimpleName()
                + " renderer=" + (zeroCopyRef != null ? "zero-copy-pending" : "capture"));
        return true;
    }

    private static void scheduleZeroCopyValidation(
            View dockBg, View workspace, LiquidDockConfig config,
            DockLiquidGlassHostView host, Miuix307ZeroCopyBackdropView backdrop, int frame) {
        if (dockBg != backgroundRef || host != hostRef || backdrop != zeroCopyRef) return;

        if (backdrop.isBlurActive()) {
            if (zeroCopyActiveLoggedFor != dockBg) {
                zeroCopyActiveLoggedFor = dockBg;
                MainHook.log(ZERO_COPY_TAG + " zero-copy active radius=" + backdrop.blurRadiusPx()
                        + " size=" + backdrop.getWidth() + "x" + backdrop.getHeight());
            }
            return;
        }

        if (backdrop.isActivationExhausted() || frame >= ZERO_COPY_VALIDATION_FRAMES) {
            MainHook.log(ZERO_COPY_TAG + " zero-copy unavailable; capture fallback reason="
                    + (backdrop.isActivationExhausted() ? "activation-exhausted" : "validation-timeout"));
            Miuix307ZeroCopyRenderer.clear();
            zeroCopyRef = null;
            installCaptureFallback(dockBg, workspace, config, host);
            return;
        }

        backdrop.postOnAnimation(() -> scheduleZeroCopyValidation(
                dockBg, workspace, config, host, backdrop, frame + 1));
    }

    /** The archived renderer is retained unchanged as the runtime fallback. */
    private static void installCaptureFallback(
            View dockBg, View workspace, LiquidDockConfig config, DockLiquidGlassHostView host) {
        if (dockBg == null || config == null || host == null || dockBg != backgroundRef) return;
        Miuix307ZeroCopyRenderer.clear();
        zeroCopyRef = null;

        DockLiquidGlassView glass = LiquidGlassFactory.create(
                dockBg, workspace, config.glass, config.dock,
                false, SQUIRCLE_CP);
        glass.setId(View.generateViewId());
        glass.setFullscreenCapture(true);
        glass.setCaptureScale(config.glass.captureScale);
        glass.setCapturePowerLimitFps(config.glass.captureFps);
        glass.setPreserveGeometrySourceVisuals(true);

        host.setLayers(glass);
        host.setGeometry(readRadius(dockBg), false, SQUIRCLE_CP);
        host.reloadOpticsPreservingGeometry(config.glass);
        glassRef = glass;
        HomeOwnershipRuntime.bind(glass, glass.getContext());
        host.bringToFront();
        host.invalidate();
    }

    /** Width/height animation path: no config I/O or renderer rebuild. */
    static void syncSize(View dockBg) {
        if (dockBg == null || dockBg != backgroundRef) return;
        DockLiquidGlassHostView host = hostRef;
        if (host == null || host.getParent() != dockBg) return;
        if (isNativeVisualOwner(dockBg)) {
            suppressVendorGpuBlur(dockBg);
            suppressVendorMaterialBody(dockBg, readRadius(dockBg));
        }
        host.bringToFront();
        host.requestLayout();
        host.invalidate();
    }

    /** Radius/material path: keep native geometry and the active renderer synchronized. */
    static void syncGeometry(View dockBg, LiquidDockConfig config) {
        if (dockBg == null || config == null || dockBg != backgroundRef) return;
        DockLiquidGlassHostView host = hostRef;
        if (host == null || host.getParent() != dockBg) return;

        if (isNativeVisualOwner(dockBg)) suppressVendorGpuBlur(dockBg);

        float nativeRadius = readRadius(dockBg);
        suppressVendorMaterialBody(dockBg, nativeRadius);
        host.setGeometry(nativeRadius, false, SQUIRCLE_CP);
        host.reloadOpticsPreservingGeometry(config.glass);
        Miuix307ZeroCopyRenderer.sync(
                config.glass, Math.round(config.glass.blur));
        DockStrokeRenderer.configureReplacingForeground(
                dockBg, config.dock, nativeRadius);
        host.bringToFront();
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
     * Disable every parent/vendor compositor blur stage on the bound 307 background. Zero-copy
     * applies pass-window blur only to its dedicated bottom child, so this parent must remain
     * clear or SurfaceFlinger would blur the optical overlay a second time.
     */
    static void suppressVendorGpuBlur(View dockBg) {
        if (dockBg == null || !isNativeVisualOwner(dockBg)) return;
        MiBlurBridge.setPassWindowBlurRadius(dockBg, 0);
        MiBlurBridge.clearPassWindowBlur(dockBg);
        if (vendorGpuBlurLoggedFor != dockBg) {
            vendorGpuBlurLoggedFor = dockBg;
            MainHook.log(TAG + " vendor parent GPU blur disabled class="
                    + dockBg.getClass().getSimpleName());
        }
    }

    /** Reassert only parent blur/material suppression before draw. */
    private static void installVendorGpuBlurSuppressor(View dockBg) {
        removeVendorGpuBlurSuppressor();
        View root = dockBg.getRootView();
        ViewTreeObserver observer = root != null ? root.getViewTreeObserver() : null;
        if (observer == null || !observer.isAlive()) return;

        ViewTreeObserver.OnPreDrawListener listener = () -> {
            if (backgroundRef == dockBg) {
                suppressVendorGpuBlur(dockBg);
                suppressVendorMaterialBody(dockBg, readRadius(dockBg));
            }
            return true;
        };
        observer.addOnPreDrawListener(listener);
        vendorBlurObserver = observer;
        vendorBlurSuppressor = listener;

        dockBg.post(() -> {
            if (backgroundRef == dockBg) {
                suppressVendorGpuBlur(dockBg);
                suppressVendorMaterialBody(dockBg, readRadius(dockBg));
            }
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

    /**
     * Keep the vendor View alive for layout, outline and MiShadow, but remove its opaque material
     * body so the zero-copy child/capture fallback is the only visible backdrop inside the shape.
     */
    private static void suppressVendorMaterialBody(View dockBg, float nativeRadius) {
        if (dockBg == null || !isNativeVisualOwner(dockBg)) return;
        float radius = Math.max(0f, nativeRadius);
        if (transparentMaterialOwner != dockBg || transparentMaterialBody == null) {
            transparentMaterialOwner = dockBg;
            transparentMaterialBody = new GradientDrawable();
            transparentMaterialBody.setShape(GradientDrawable.RECTANGLE);
            transparentMaterialBody.setColor(android.graphics.Color.TRANSPARENT);
            transparentMaterialRadius = Float.NaN;
        }
        if (Float.compare(transparentMaterialRadius, radius) != 0) {
            transparentMaterialRadius = radius;
            transparentMaterialBody.setCornerRadius(radius);
        }
        if (dockBg.getBackground() != transparentMaterialBody) {
            dockBg.setBackground(transparentMaterialBody);
        }
        if (materialBodyLoggedFor != dockBg) {
            materialBodyLoggedFor = dockBg;
            MainHook.log(TAG + " vendor material body transparent; native optics radius="
                    + radius + " class=" + dockBg.getClass().getSimpleName());
        }
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
        if (dockBg != null && COMPAT_BACKGROUND_CLASS.equals(dockBg.getClass().getName())) {
            try {
                Field field = findField(dockBg.getClass(), "mCornerRadius");
                field.setAccessible(true);
                Object value = field.get(dockBg);
                if (value instanceof Number) {
                    return Math.max(0f, ((Number) value).floatValue());
                }
            } catch (Throwable ignored) {}
        }

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
