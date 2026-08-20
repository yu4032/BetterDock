package com.hellovoid.liquiddock;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import java.lang.reflect.Field;

/**
 * MiuiX-specific zero-copy glass installer for HyperOS 3.0.307+ docks.
 *
 * The vendor background remains the authoritative Dock geometry shell. Its own material body and
 * parent compositor blur are suppressed while LiquidDock renders PassBlur -> OES -> Prismal in a
 * child TextureView. There is deliberately no screen-capture fallback.
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

    private static DockLiquidGlassHostView hostRef;
    private static View backgroundRef;
    private static boolean bindingInvalidated;
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

    static boolean isBoundTo(View dockBg) {
        if (bindingInvalidated) return false;
        if (dockBg == null || dockBg != backgroundRef) return false;
        DockLiquidGlassHostView host = hostRef;
        return host != null && host.getParent() == dockBg;
    }

    static boolean isZeroCopyActive() {
        if (bindingInvalidated) return false;
        DockLiquidGlassHostView host = hostRef;
        return host != null && host.getParent() == backgroundRef
                && Miuix307ZeroCopyRenderer.isActive();
    }

    /** Mark a detached material/TextureView pair unusable until install() rebuilds it. */
    static void invalidateBinding(View dockBg) {
        if (dockBg == null || dockBg != backgroundRef) return;
        bindingInvalidated = true;
        removeVendorGpuBlurSuppressor();
        Miuix307ZeroCopyRenderer.clear();
        MainHook.log(TAG + " binding invalidated after material hierarchy detach");
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
        if (compatBackgroundBlurLoggedFor != dockBg) {
            compatBackgroundBlurLoggedFor = dockBg;
            MainHook.log(TAG + " compat BlurBackground2 parent GPU blur suppressed "
                    + requestedRadius + " -> 0");
        }
        return 0;
    }

    static boolean install(View dockBg, LiquidDockConfig config) {
        if (!(dockBg instanceof ViewGroup) || config == null) return false;
        ViewGroup materialHost = (ViewGroup) dockBg;
        boolean nativeVisualOwner = isNativeVisualOwner(dockBg);

        if (!bindingInvalidated && backgroundRef == dockBg
                && hostRef != null && hostRef.getParent() == materialHost) {
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
        backgroundRef = null;
        bindingInvalidated = false;
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

        boolean zeroCopyCandidate = Miuix307ZeroCopyRenderer.install(
                materialHost, host, config.glass, Math.round(config.glass.blur));

        FrameLayout.LayoutParams hostLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        materialHost.addView(host, materialHost.getChildCount(), hostLp);
        host.bringToFront();

        backgroundRef = dockBg;
        hostRef = host;

        if (nativeVisualOwner) suppressVendorGpuBlur(dockBg);
        installVendorGpuBlurSuppressor(dockBg);

        if (zeroCopyCandidate) {
            scheduleZeroCopyValidation(dockBg, host, 0);
        } else {
            MainHook.log(ZERO_COPY_TAG + " zero-copy unavailable; glass remains transparent");
        }

        DockStrokeRenderer.configureReplacingForeground(dockBg, config.dock, nativeRadius);
        MainHook.log(TAG + " glass composed inside native 307 material shell class="
                + dockBg.getClass().getSimpleName()
                + " renderer=" + (zeroCopyCandidate ? "passblur-gles-pending" : "none"));
        return true;
    }

    private static void scheduleZeroCopyValidation(
            View dockBg, DockLiquidGlassHostView host, int frame) {
        if (dockBg != backgroundRef || host != hostRef) return;

        if (Miuix307ZeroCopyRenderer.isActive()) {
            if (zeroCopyActiveLoggedFor != dockBg) {
                zeroCopyActiveLoggedFor = dockBg;
                MainHook.log(ZERO_COPY_TAG + " zero-copy active backend=passblur-gles"
                        + " size=" + Miuix307ZeroCopyRenderer.activeWidth()
                        + "x" + Miuix307ZeroCopyRenderer.activeHeight());
            }
            return;
        }

        if (Miuix307ZeroCopyRenderer.isActivationExhausted()
                || frame >= ZERO_COPY_VALIDATION_FRAMES) {
            MainHook.log(ZERO_COPY_TAG + " zero-copy inactive; glass remains transparent reason="
                    + (Miuix307ZeroCopyRenderer.isActivationExhausted()
                    ? "activation-exhausted" : "validation-timeout"));
            return;
        }

        host.postOnAnimation(() -> scheduleZeroCopyValidation(dockBg, host, frame + 1));
    }

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

    static void syncGeometry(View dockBg, LiquidDockConfig config) {
        if (dockBg == null || config == null || dockBg != backgroundRef) return;
        DockLiquidGlassHostView host = hostRef;
        if (host == null || host.getParent() != dockBg) return;

        if (isNativeVisualOwner(dockBg)) suppressVendorGpuBlur(dockBg);

        float nativeRadius = readRadius(dockBg);
        suppressVendorMaterialBody(dockBg, nativeRadius);
        host.setGeometry(nativeRadius, false, SQUIRCLE_CP);
        Miuix307ZeroCopyRenderer.sync(config.glass, Math.round(config.glass.blur));
        DockStrokeRenderer.configureReplacingForeground(dockBg, config.dock, nativeRadius);
        host.bringToFront();
        host.invalidate();
    }

    private static boolean isNativeVisualOwner(View dockBg) {
        if (dockBg == null) return false;
        String name = dockBg.getClass().getName();
        return NATIVE_BACKGROUND_CLASS.equals(name) || COMPAT_BACKGROUND_CLASS.equals(name);
    }

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
        if (dockBg.getBackground() != transparentMaterialBody) dockBg.setBackground(transparentMaterialBody);
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
