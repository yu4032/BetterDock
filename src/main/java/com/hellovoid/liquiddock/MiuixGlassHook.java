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
 * The vendor background remains the authoritative Dock visual shell. Its compositor/pass-window
 * blur is disabled, while LiquidDock's existing Prismal host is composed inside that shell.
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
    private static View transparentMaterialOwner;
    private static GradientDrawable transparentMaterialBody;
    private static float transparentMaterialRadius = Float.NaN;
    private static View materialBodyLoggedFor;

    private MiuixGlassHook() {}

    /** True only when this exact vendor background instance still owns the live Prismal host. */
    static boolean isBoundTo(View dockBg) {
        if (dockBg == null || dockBg != backgroundRef) return false;
        DockLiquidGlassHostView host = hostRef;
        return host != null && host.getParent() == dockBg;
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
        if (!(dockBg instanceof ViewGroup) || config == null) return false;
        ViewGroup materialHost = (ViewGroup) dockBg;
        boolean nativeVisualOwner = isNativeVisualOwner(dockBg);

        if (backgroundRef == dockBg && hostRef != null && hostRef.getParent() == materialHost) {
            syncSize(dockBg);
            syncGeometry(dockBg, config);
            return true;
        }

        // Remove only LiquidDock's previous child host. Never replace or hide the vendor shell.
        removeVendorGpuBlurSuppressor();
        if (hostRef != null && hostRef.getParent() instanceof ViewGroup) {
            ((ViewGroup) hostRef.getParent()).removeView(hostRef);
        }
        hostRef = null;
        glassRef = null;
        backgroundRef = null;
        vendorGpuBlurLoggedFor = null;
        compatBackgroundBlurLoggedFor = null;
        transparentMaterialOwner = null;
        transparentMaterialBody = null;
        transparentMaterialRadius = Float.NaN;
        materialBodyLoggedFor = null;

        // The vendor View keeps outline/MiShadow/foreground ownership, but its compositor blur
        // must stay disabled because SurfaceFlinger would otherwise post-process the whole Dock.
        if (nativeVisualOwner) suppressVendorGpuBlur(dockBg);

        float nativeRadius = readRadius(dockBg);
        suppressVendorMaterialBody(dockBg, nativeRadius);
        int dockW = readDimension(dockBg, "mWidth", true);
        int dockH = readDimension(dockBg, "mHeight", false);
        MainHook.log(TAG + " in-place material nativeOpticsRadius=" + nativeRadius
                + " dock size=" + dockW + "x" + dockH);

        DockLiquidGlassView glass = LiquidGlassFactory.create(
                dockBg, workspace, config.glass, config.dock,
                false, SQUIRCLE_CP);
        glass.setId(View.generateViewId());
        glass.setFullscreenCapture(true);
        glass.setCaptureScale(config.glass.captureScale);
        glass.setCapturePowerLimitFps(config.glass.captureFps);
        // This geometry source is also the parent shell; hiding it would hide glass, stroke,
        // outline and MiShadow together. Keep the shell alpha alive after every valid capture.
        glass.setPreserveGeometrySourceVisuals(true);

        DockLiquidGlassHostView host = new DockLiquidGlassHostView(dockBg.getContext());
        host.setId(View.generateViewId());
        host.setLayers(glass);
        host.setGeometry(nativeRadius, false, SQUIRCLE_CP);
        host.reloadOpticsPreservingGeometry(config.glass);

        FrameLayout.LayoutParams hostLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        materialHost.addView(host, materialHost.getChildCount(), hostLp);
        host.bringToFront();

        backgroundRef = dockBg;
        glassRef = glass;
        hostRef = host;
        if (nativeVisualOwner) suppressVendorGpuBlur(dockBg);
        installVendorGpuBlurSuppressor(dockBg);
        HomeOwnershipRuntime.bind(glass, glass.getContext());

        // Stroke + stroke-shadow deliberately live on the vendor foreground, which Android draws
        // after child dispatch, so they remain sharp and above the in-place glass.
        DockStrokeRenderer.configureReplacingForeground(dockBg, config.dock, nativeRadius);
        MainHook.log(TAG + " Prismal composed inside native 307 material shell class="
                + dockBg.getClass().getSimpleName());
        return true;
    }

    /** Width/height animation path: no config I/O or stroke/material rebuild. */
    static void syncSize(View dockBg) {
        if (dockBg == null || dockBg != backgroundRef) return;
        DockLiquidGlassHostView host = hostRef;
        if (host == null || host.getParent() != dockBg) return;
        if (isNativeVisualOwner(dockBg)) {
            suppressVendorGpuBlur(dockBg);
            suppressVendorMaterialBody(dockBg, readRadius(dockBg));
        }
        // MATCH_PARENT follows the authoritative vendor material geometry automatically.
        host.bringToFront();
        host.requestLayout();
        host.invalidate();
    }

    /** Radius/material path: only called when the vendor radius changes. */
    static void syncGeometry(View dockBg, LiquidDockConfig config) {
        if (dockBg == null || config == null || dockBg != backgroundRef) return;
        DockLiquidGlassHostView host = hostRef;
        if (host == null || host.getParent() != dockBg) return;

        if (isNativeVisualOwner(dockBg)) suppressVendorGpuBlur(dockBg);

        float nativeRadius = readRadius(dockBg);
        suppressVendorMaterialBody(dockBg, nativeRadius);
        host.setGeometry(nativeRadius, false, SQUIRCLE_CP);
        host.reloadOpticsPreservingGeometry(config.glass);
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
     * Reassert only GPU-blur suppression before draw. The vendor shell itself stays visible;
     * LiquidDock is a child composition and must never force the shell alpha to zero.
     */
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
     * Keep the vendor View itself alive for layout, outline and MiShadow, but remove the
     * opaque/material body that otherwise creates a second visible Dock edge around Prismal.
     * The vendor's private radius state remains untouched and continues to drive optics.
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
