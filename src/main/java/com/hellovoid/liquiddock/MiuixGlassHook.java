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
 * The vendor background remains intact and owns the realtime backdrop blur/gradient. This
 * class overlays LiquidDock's existing Prismal glass stack above it. The generic Launcher
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
    private static ViewTreeObserver nativeBackgroundObserver;
    private static ViewTreeObserver.OnPreDrawListener nativeBackgroundPreserver;
    private static int nativeBlurRadiusPx = -1;
    private static boolean nativeBlurRadiusFailureLogged;
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
     * BlurBackground2.addBlur() hard-codes a 100-unit background blur before delegating to
     * BlurUtilities.setBackgroundBlur(View,int,float[],int[][]). The full default->theme device
     * trace shows this is the themed-only visual difference: default MiuiX uses the configured
     * radius while both implementations intentionally keep the same HotSeats MiShadow. Clamp
     * only positive themed utility radii; preserve vendor disable semantics and blend arrays.
     * This deliberately does not require a live Prismal binding because the vendor can call the
     * utility while constructing the replacement background, before hierarchy rebind completes.
     */
    static int clampCompatBackgroundBlurRadius(
            View dockBg, int requestedRadius, LiquidDockConfig config) {
        if (dockBg == null || config == null || requestedRadius <= 0) return requestedRadius;
        if (!COMPAT_BACKGROUND_CLASS.equals(dockBg.getClass().getName())) return requestedRadius;
        int targetRadius = Math.round(
                config.glass.blur * dockBg.getResources().getDisplayMetrics().density);
        if (requestedRadius != targetRadius && compatBackgroundBlurLoggedFor != dockBg) {
            compatBackgroundBlurLoggedFor = dockBg;
            MainHook.log(TAG + " compat BlurBackground2 background blur clamped "
                    + requestedRadius + " -> " + targetRadius);
        }
        return targetRadius;
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
        removeNativeBackgroundPreserver();
        if (hostRef != null && hostRef.getParent() instanceof ViewGroup) {
            ((ViewGroup) hostRef.getParent()).removeView(hostRef);
        }
        hostRef = null;
        glassRef = null;
        backgroundRef = null;
        compatBackgroundBlurLoggedFor = null;
        nativeBlurRadiusPx = -1;
        nativeBlurRadiusFailureLogged = false;

        float density = dockBg.getResources().getDisplayMetrics().density;
        int blurPx = Math.round(config.glass.blur * density);
        nativeBlurRadiusPx = nativeVisualOwner ? blurPx : -1;
        if (nativeMaterial) {
            boolean passOk = MiBlurBridge.applyPassWindowBlur(dockBg, blurPx);
            MainHook.log(TAG + " passWindowBlur radius=" + blurPx + " ok=" + passOk);
            if (!passOk) {
                // The true MiuiX material normally owns compositor pass-window blur.
                boolean contentOk = MiBlurBridge.applyContentBlur(
                        dockBg, blurPx, config.glass.captureScale);
                MainHook.log(TAG + " fallback to content blur ok=" + contentOk);
            }
        } else if (nativeVisualOwner) {
            // BlurBackground2 owns its vendor backdrop/outline stack. Its separate utility
            // background-blur radius is clamped at BlurUtilities; keep the normal HotSeats
            // MiShadow because the working default MiuiX path uses the same shadow parameters.
            MainHook.log(TAG + " compat BlurBackground2 keeps vendor blur/outline radius="
                    + blurPx);
        }

        float radius = readRadius(dockBg);
        int dockW = readDimension(dockBg, "mWidth", true);
        int dockH = readDimension(dockBg, "mHeight", false);
        MainHook.log(TAG + " detected radius=" + radius
                + " dock size=" + dockW + "x" + dockH);

        DockLiquidGlassView glass = LiquidGlassFactory.create(
                dockBg, workspace, config.glass, config.dock, false, SQUIRCLE_CP);
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
        if (nativeVisualOwner) enforcePrismalOpticalOnly(glass);
        MainHook.log(TAG + " capture tuning fps=" + config.glass.captureFps
                + " scale=" + config.glass.captureScale);

        DockLiquidGlassHostView host = new DockLiquidGlassHostView(parent.getContext());
        host.setId(View.generateViewId());
        host.setLayers(glass);
        host.setGeometry(radius, false, SQUIRCLE_CP);
        host.reloadOverlay(config.dock, config.glass);

        FrameLayout.LayoutParams hostLp = new FrameLayout.LayoutParams(
                dockW > 0 ? dockW : ViewGroup.LayoutParams.MATCH_PARENT,
                dockH > 0 ? dockH : ViewGroup.LayoutParams.MATCH_PARENT);
        hostLp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;

        int bgIdx = parent.indexOfChild(dockBg);
        int insertIndex = bgIdx < 0 ? parent.getChildCount()
                : Math.min(parent.getChildCount(), bgIdx + 1);
        parent.addView(host, insertIndex, hostLp);

        // Preserve the MiuiX drawable/pass-window blur. DockLiquidGlassView normally hides its
        // geometrySource after the first captured frame; on 307 that source is the actual native
        // material background and must stay visible underneath the Prismal layer.
        backgroundRef = dockBg;
        glassRef = glass;
        hostRef = host;
        if (nativeVisualOwner) enforceNativeBlurRadius(dockBg);
        installNativeBackgroundPreserver(dockBg, glass, nativeVisualOwner);
        HomeOwnershipRuntime.bind(glass, glass.getContext());

        DockStrokeRenderer.configure(dockBg, config.dock, radius);
        MainHook.log(TAG + " Prismal optical layer installed above "
                + (nativeMaterial ? "MiuiX native material" : "BlurBackground2 vendor visual")
                + " background with live ownership");
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
        host.setVisibility(dockBg.getVisibility());
        host.invalidate();
    }

    /** Radius/material path: only called when the vendor radius changes. */
    static void syncGeometry(View dockBg, LiquidDockConfig config) {
        if (dockBg == null || config == null || dockBg != backgroundRef) return;
        DockLiquidGlassHostView host = hostRef;
        if (host == null || host.getParent() == null) return;

        if (isNativeVisualOwner(dockBg)) {
            float density = dockBg.getResources().getDisplayMetrics().density;
            nativeBlurRadiusPx = Math.round(config.glass.blur * density);
            enforceNativeBlurRadius(dockBg);
        } else {
            nativeBlurRadiusPx = -1;
        }

        float radius = readRadius(dockBg);
        host.setVisibility(dockBg.getVisibility());
        host.setGeometry(radius, false, SQUIRCLE_CP);
        host.reloadOverlay(config.dock, config.glass);
        DockStrokeRenderer.configure(dockBg, config.dock, radius);
        host.invalidate();
    }

    private static boolean isNativeMaterialBackground(View dockBg) {
        return dockBg != null && NATIVE_BACKGROUND_CLASS.equals(dockBg.getClass().getName());
    }

    /** Both 307 implementations own the native backdrop/outline/shadow visual stack. */
    private static boolean isNativeVisualOwner(View dockBg) {
        if (dockBg == null) return false;
        String name = dockBg.getClass().getName();
        return NATIVE_BACKGROUND_CLASS.equals(name) || COMPAT_BACKGROUND_CLASS.equals(name);
    }

    /**
     * MiuiX already owns the actual backdrop blur. Prismal must only refract the raw sampled
     * backdrop and draw its optical highlight; otherwise its shader/self-blur stacks on top of
     * the native pass-window blur and produces the heavy second blur seen after HOME/RECENTS
     * transitions. This is intentionally re-applied after config hot reloads.
     */
    private static void enforcePrismalOpticalOnly(DockLiquidGlassView glass) {
        if (glass == null) return;
        glass.setBlurMode(LiquidBlurMode.SHADER);
        glass.setBlurRadiusPx(0);
    }

    /**
     * HyperOS 307 reapplies its own MaterialConfig on APP/HOME/RECENTS transitions and can
     * overwrite the Dock radius (device log: 14 -> 201) without replacing the background View.
     * Reassert only the user-configured radius; leave vendor blur mode/blend colors untouched.
     */
    private static void enforceNativeBlurRadius(View dockBg) {
        if (dockBg == null || dockBg != backgroundRef || nativeBlurRadiusPx < 0) return;
        boolean ok = MiBlurBridge.setPassWindowBlurRadius(dockBg, nativeBlurRadiusPx);
        if (!ok && !nativeBlurRadiusFailureLogged) {
            nativeBlurRadiusFailureLogged = true;
            MainHook.log(TAG + " native blur radius clamp unavailable");
        } else if (ok) {
            nativeBlurRadiusFailureLogged = false;
        }
    }

    /**
     * Keep the native MiuiX material visible without changing DockLiquidGlassView's legacy
     * behavior. Its installCapture() sets geometrySource alpha=0 and marks the private
     * nativeBackgroundHiddenByGlass latch. Because this listener is registered after the glass
     * attaches its own pre-draw listener, it clears that latch and restores alpha before drawing.
     */
    private static void installNativeBackgroundPreserver(
            View dockBg, DockLiquidGlassView glass, boolean nativeVisualOwner) {
        removeNativeBackgroundPreserver();
        final Field hiddenField;
        try {
            hiddenField = findField(DockLiquidGlassView.class, "nativeBackgroundHiddenByGlass");
            hiddenField.setAccessible(true);
        } catch (Throwable error) {
            MainHook.log(TAG + " native background latch unavailable: " + error);
            dockBg.setAlpha(1f);
            return;
        }

        View root = dockBg.getRootView();
        ViewTreeObserver observer = root != null ? root.getViewTreeObserver() : null;
        if (observer == null || !observer.isAlive()) {
            dockBg.setAlpha(1f);
            return;
        }

        ViewTreeObserver.OnPreDrawListener listener = () -> {
            if (backgroundRef != dockBg || glassRef != glass) return true;
            // Keep the complete vendor geometry/background stack alive underneath Prismal.
            // The themed hard-coded radius is clamped at BlurUtilities before it reaches View.
            if (nativeVisualOwner) {
                enforcePrismalOpticalOnly(glass);
                enforceNativeBlurRadius(dockBg);
            }
            try {
                hiddenField.setBoolean(glass, false);
            } catch (Throwable ignored) {}
            if (dockBg.getAlpha() != 1f) dockBg.setAlpha(1f);
            return true;
        };
        observer.addOnPreDrawListener(listener);
        nativeBackgroundObserver = observer;
        nativeBackgroundPreserver = listener;

        // A capture/vendor material callback may complete between setupViews and the next
        // pre-draw. Restore once on the main queue as well so the native material never remains
        // hidden or keeps a vendor-overwritten blur radius for a full frame.
        dockBg.post(() -> {
            if (backgroundRef != dockBg || glassRef != glass) return;
            if (nativeVisualOwner) {
                enforcePrismalOpticalOnly(glass);
                enforceNativeBlurRadius(dockBg);
            }
            try {
                hiddenField.setBoolean(glass, false);
            } catch (Throwable ignored) {}
            dockBg.setAlpha(1f);
        });
    }

    private static void removeNativeBackgroundPreserver() {
        ViewTreeObserver observer = nativeBackgroundObserver;
        ViewTreeObserver.OnPreDrawListener listener = nativeBackgroundPreserver;
        nativeBackgroundObserver = null;
        nativeBackgroundPreserver = null;
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
