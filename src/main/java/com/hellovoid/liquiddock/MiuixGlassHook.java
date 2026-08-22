package com.hellovoid.liquiddock;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

/**
 * MiuiX-specific zero-copy glass installer for HyperOS 3.0.307+ docks.
 *
 * The vendor background remains the authoritative Dock geometry shell. Parent compositor blur is
 * suppressed for both supported HotSeats owners. The themed BlurBackground2 material body is made
 * transparent because Prismal fully replaces it; the default MiuiX material body remains visible
 * as failure protection while LiquidDock renders PassBlur -> OES -> Prismal in a child TextureView.
 * There is deliberately no screen-capture fallback.
 */
final class MiuixGlassHook {
    private static final String TAG = "[DC][MG]";
    private static final String ZERO_COPY_TAG = "[DC][ZC]";
    private static final float SQUIRCLE_CP = 0.58f;
    private static final int ZERO_COPY_VALIDATION_FRAMES = 90;
    private static final String NATIVE_BACKGROUND_CLASS =
            "com.miui.home.launcher.hotseats.HotSeatsListContentMiuiXBlurBackground";
    private static final String COMPAT_BACKGROUND_CLASS =
            "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2";

    private static WeakReference<DockLiquidGlassHostView> hostRef = new WeakReference<>(null);
    private static WeakReference<View> backgroundRef = new WeakReference<>(null);
    private static WeakReference<ViewTreeObserver> vendorBlurObserver = new WeakReference<>(null);
    private static ViewTreeObserver.OnPreDrawListener vendorBlurSuppressor;
    private static WeakReference<View> vendorGpuBlurLoggedFor = new WeakReference<>(null);
    private static WeakReference<View> compatBackgroundBlurLoggedFor = new WeakReference<>(null);
    private static WeakReference<View> transparentMaterialOwner = new WeakReference<>(null);
    private static GradientDrawable transparentMaterialBody;
    private static float transparentMaterialRadius = Float.NaN;
    private static WeakReference<View> materialBodyLoggedFor = new WeakReference<>(null);
    private static WeakReference<View> zeroCopyActiveLoggedFor = new WeakReference<>(null);

    private MiuixGlassHook() {}

    private static DockLiquidGlassHostView currentHost() { return hostRef.get(); }
    private static View currentBackground() { return backgroundRef.get(); }

    private static void clearTrackedViews() {
        hostRef = new WeakReference<>(null);
        backgroundRef = new WeakReference<>(null);
        vendorGpuBlurLoggedFor = new WeakReference<>(null);
        compatBackgroundBlurLoggedFor = new WeakReference<>(null);
        transparentMaterialOwner = new WeakReference<>(null);
        transparentMaterialBody = null;
        transparentMaterialRadius = Float.NaN;
        materialBodyLoggedFor = new WeakReference<>(null);
        zeroCopyActiveLoggedFor = new WeakReference<>(null);
    }

    static void onHostDetached(DockLiquidGlassHostView detachedHost) {
        if (detachedHost == null || detachedHost != currentHost()) return;
        removeVendorGpuBlurSuppressor();
        Miuix307ZeroCopyRenderer.clear();
        clearTrackedViews();
    }

    static boolean isBoundTo(View dockBg) {
        if (dockBg == null || dockBg != currentBackground()) return false;
        DockLiquidGlassHostView host = currentHost();
        return host != null && host.getParent() == dockBg;
    }

    static boolean isZeroCopyActive() {
        DockLiquidGlassHostView host = currentHost();
        return host != null && host.getParent() == currentBackground()
                && Miuix307ZeroCopyRenderer.isActive();
    }

    static boolean hasReadyNativeGeometry(View dockBg) {
        if (dockBg == null || !isNativeVisualOwner(dockBg)) return false;
        if (!dockBg.isAttachedToWindow() || !(dockBg.getParent() instanceof ViewGroup)) return false;
        if (dockBg.getWidth() <= 0 || dockBg.getHeight() <= 0) return false;
        float radius = readRadius(dockBg);
        return !Float.isNaN(radius) && !Float.isInfinite(radius) && radius > 0.5f;
    }

    static float readNativeOpticsRadius(View dockBg) {
        return readRadius(dockBg);
    }

    static int suppressCompatBackgroundBlurRadius(View dockBg, int requestedRadius) {
        if (dockBg == null || requestedRadius <= 0) return requestedRadius;
        if (!COMPAT_BACKGROUND_CLASS.equals(dockBg.getClass().getName())) return requestedRadius;
        if (compatBackgroundBlurLoggedFor.get() != dockBg) {
            compatBackgroundBlurLoggedFor = new WeakReference<>(dockBg);
            MainHook.log(TAG + " compat BlurBackground2 parent GPU blur suppressed "
                    + requestedRadius + " -> 0");
        }
        return 0;
    }

    static boolean install(View dockBg, LiquidDockConfig config) {
        if (!(dockBg instanceof ViewGroup) || config == null) return false;
        ViewGroup materialHost = (ViewGroup) dockBg;
        boolean nativeVisualOwner = isNativeVisualOwner(dockBg);

        DockLiquidGlassHostView existingHost = currentHost();
        if (currentBackground() == dockBg && existingHost != null
                && existingHost.getParent() == materialHost) {
            syncSize(dockBg);
            syncGeometry(dockBg, config);
            return true;
        }
        if (!hasReadyNativeGeometry(dockBg)) return false;

        removeVendorGpuBlurSuppressor();
        Miuix307ZeroCopyRenderer.clear();
        DockLiquidGlassHostView previousHost = currentHost();
        if (previousHost != null && previousHost.getParent() instanceof ViewGroup) {
            ((ViewGroup) previousHost.getParent()).removeView(previousHost);
        }
        clearTrackedViews();

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

        boolean zeroCopyCandidate = Miuix307ZeroCopyRenderer.install(
                materialHost, host, config.glass, Math.round(config.glass.blur));

        FrameLayout.LayoutParams hostLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        materialHost.addView(host, materialHost.getChildCount(), hostLp);
        host.bringToFront();

        backgroundRef = new WeakReference<>(dockBg);
        hostRef = new WeakReference<>(host);

        if (nativeVisualOwner) suppressVendorGpuBlur(dockBg);
        installVendorGpuBlurSuppressor(dockBg);

        if (zeroCopyCandidate) {
            scheduleZeroCopyValidation(dockBg, host, 0);
        } else {
            MainHook.log(ZERO_COPY_TAG + " zero-copy unavailable; " + inactiveVisualState(dockBg));
        }

        DockStrokeRenderer.configureReplacingForeground(dockBg, config.dock, nativeRadius);
        MainHook.syncDockShadow(dockBg, config.dock);
        MainHook.log(TAG + " glass composed inside native 307 material shell class="
                + dockBg.getClass().getSimpleName()
                + " renderer=" + (zeroCopyCandidate ? "passblur-gles-pending" : "none"));
        return true;
    }

    private static void scheduleZeroCopyValidation(
            View dockBg, DockLiquidGlassHostView host, int frame) {
        if (dockBg != currentBackground() || host != currentHost()) return;

        if (Miuix307ZeroCopyRenderer.isActive()) {
            if (zeroCopyActiveLoggedFor.get() != dockBg) {
                zeroCopyActiveLoggedFor = new WeakReference<>(dockBg);
                MainHook.log(ZERO_COPY_TAG + " zero-copy active backend=passblur-gles"
                        + " size=" + Miuix307ZeroCopyRenderer.activeWidth()
                        + "x" + Miuix307ZeroCopyRenderer.activeHeight());
            }
            return;
        }

        if (Miuix307ZeroCopyRenderer.isActivationExhausted()
                || frame >= ZERO_COPY_VALIDATION_FRAMES) {
            MainHook.log(ZERO_COPY_TAG + " zero-copy inactive; " + inactiveVisualState(dockBg)
                    + " reason="
                    + (Miuix307ZeroCopyRenderer.isActivationExhausted()
                    ? "activation-exhausted" : "validation-timeout"));
            return;
        }

        host.postOnAnimation(() -> scheduleZeroCopyValidation(dockBg, host, frame + 1));
    }

    private static String inactiveVisualState(View dockBg) {
        return shouldSuppressVendorMaterialBody(dockBg)
                ? "glass remains transparent"
                : "default vendor shell remains visible";
    }

    static void syncSize(View dockBg) {
        if (dockBg == null || dockBg != currentBackground()) return;
        DockLiquidGlassHostView host = currentHost();
        if (host == null || host.getParent() != dockBg) return;
        if (isNativeVisualOwner(dockBg)) {
            suppressVendorGpuBlur(dockBg);
            suppressVendorMaterialBody(dockBg, readRadius(dockBg));
        }
        host.bringToFront();
        host.requestLayout();
        host.invalidate();
    }

    static void syncGeometry(View dockBg, LiquidDockConfig config) {
        if (dockBg == null || config == null || dockBg != currentBackground()) return;
        DockLiquidGlassHostView host = currentHost();
        if (host == null || host.getParent() != dockBg) return;

        if (isNativeVisualOwner(dockBg)) suppressVendorGpuBlur(dockBg);

        float nativeRadius = readRadius(dockBg);
        suppressVendorMaterialBody(dockBg, nativeRadius);
        host.setGeometry(nativeRadius, false, SQUIRCLE_CP);
        Miuix307ZeroCopyRenderer.sync(config.glass, Math.round(config.glass.blur));
        DockStrokeRenderer.configureReplacingForeground(dockBg, config.dock, nativeRadius);
        MainHook.syncDockShadow(dockBg, config.dock);
        host.bringToFront();
        host.invalidate();
    }

    private static boolean isNativeVisualOwner(View dockBg) {
        if (dockBg == null) return false;
        String name = dockBg.getClass().getName();
        return NATIVE_BACKGROUND_CLASS.equals(name) || COMPAT_BACKGROUND_CLASS.equals(name);
    }

    private static boolean shouldSuppressVendorMaterialBody(View dockBg) {
        return dockBg != null
                && COMPAT_BACKGROUND_CLASS.equals(dockBg.getClass().getName());
    }

    static void suppressVendorGpuBlur(View dockBg) {
        if (dockBg == null || !isNativeVisualOwner(dockBg)) return;
        MiBlurBridge.setPassWindowBlurRadius(dockBg, 0);
        MiBlurBridge.clearPassWindowBlur(dockBg);
        if (vendorGpuBlurLoggedFor.get() != dockBg) {
            vendorGpuBlurLoggedFor = new WeakReference<>(dockBg);
            MainHook.log(TAG + " vendor parent GPU blur disabled class="
                    + dockBg.getClass().getSimpleName());
        }
    }

    private static void installVendorGpuBlurSuppressor(View dockBg) {
        removeVendorGpuBlurSuppressor();
        View root = dockBg.getRootView();
        ViewTreeObserver observer = root != null ? root.getViewTreeObserver() : null;
        if (observer == null || !observer.isAlive()) return;

        WeakReference<View> watchedBackground = new WeakReference<>(dockBg);
        ViewTreeObserver.OnPreDrawListener listener = () -> {
            View background = watchedBackground.get();
            if (background != null && currentBackground() == background) {
                suppressVendorGpuBlur(background);
                suppressVendorMaterialBody(background, readRadius(background));
            }
            return true;
        };
        observer.addOnPreDrawListener(listener);
        vendorBlurObserver = new WeakReference<>(observer);
        vendorBlurSuppressor = listener;

        WeakReference<View> postedBackground = new WeakReference<>(dockBg);
        dockBg.post(() -> {
            View background = postedBackground.get();
            if (background != null && currentBackground() == background) {
                suppressVendorGpuBlur(background);
                suppressVendorMaterialBody(background, readRadius(background));
            }
        });
    }

    private static void removeVendorGpuBlurSuppressor() {
        ViewTreeObserver observer = vendorBlurObserver.get();
        ViewTreeObserver.OnPreDrawListener listener = vendorBlurSuppressor;
        vendorBlurObserver = new WeakReference<>(null);
        vendorBlurSuppressor = null;
        if (observer == null || listener == null) return;
        try {
            if (observer.isAlive()) observer.removeOnPreDrawListener(listener);
        } catch (Throwable ignored) {}
    }

    private static void suppressVendorMaterialBody(View dockBg, float nativeRadius) {
        if (!shouldSuppressVendorMaterialBody(dockBg)) return;
        float radius = Math.max(0f, nativeRadius);
        if (transparentMaterialOwner.get() != dockBg || transparentMaterialBody == null) {
            transparentMaterialOwner = new WeakReference<>(dockBg);
            transparentMaterialBody = new GradientDrawable();
            transparentMaterialBody.setShape(GradientDrawable.RECTANGLE);
            transparentMaterialBody.setColor(android.graphics.Color.TRANSPARENT);
            transparentMaterialRadius = Float.NaN;
        }
        if (Float.compare(transparentMaterialRadius, radius) != 0) {
            transparentMaterialRadius = radius;
            transparentMaterialBody.setCornerRadius(radius);
        }
        if (dockBg.getBackground() != transparentMaterialBody) dockBg.setBackground(transparentMaterialBody);
        if (materialBodyLoggedFor.get() != dockBg) {
            materialBodyLoggedFor = new WeakReference<>(dockBg);
            MainHook.log(TAG + " themed vendor material body transparent; native optics radius="
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
                if (value instanceof Number) return Math.max(0f, ((Number) value).floatValue());
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
