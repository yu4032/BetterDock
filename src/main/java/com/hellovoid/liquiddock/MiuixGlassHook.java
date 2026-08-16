package com.hellovoid.liquiddock;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.reflect.Field;

/**
 * MiuiX-specific glass installer for OS3.0.307+ docks.
 *
 * The vendor background remains intact and owns the realtime backdrop blur/gradient. This
 * class only overlays LiquidDock's existing Prismal glass stack above it. No Launcher
 * HOME/APP/RECENTS capture lifecycle is installed for this path.
 */
final class MiuixGlassHook {
    private static final String TAG = "[DC][MG]";
    private static final float SQUIRCLE_CP = 0.58f;

    private static DockLiquidGlassView glassRef;
    private static DockLiquidGlassHostView hostRef;
    private static View backgroundRef;

    private MiuixGlassHook() {}

    static boolean install(View dockBg, View workspace, LiquidDockConfig config,
                           Object launcher, ClassLoader cl) {
        if (dockBg == null || config == null) return false;
        ViewGroup parent = dockBg.getParent() instanceof ViewGroup
                ? (ViewGroup) dockBg.getParent() : null;
        if (parent == null) return false;

        if (backgroundRef == dockBg && hostRef != null && hostRef.getParent() == parent) {
            syncSize(dockBg);
            syncGeometry(dockBg, config);
            return true;
        }

        // Detached/old host: never stack a second glass layer on a recreated Dock hierarchy.
        if (hostRef != null && hostRef.getParent() instanceof ViewGroup) {
            ((ViewGroup) hostRef.getParent()).removeView(hostRef);
        }
        hostRef = null;
        glassRef = null;
        backgroundRef = null;

        float density = dockBg.getResources().getDisplayMetrics().density;
        int blurPx = Math.round(config.glass.blur * density);
        boolean passOk = MiBlurBridge.applyPassWindowBlur(dockBg, blurPx);
        MainHook.log(TAG + " passWindowBlur radius=" + blurPx + " ok=" + passOk);
        if (!passOk) {
            boolean contentOk = MiBlurBridge.applyContentBlur(dockBg, blurPx, 0.5f);
            MainHook.log(TAG + " fallback to content blur ok=" + contentOk);
        }

        float radius = readRadius(dockBg);
        int dockW = readDimension(dockBg, "mWidth", true);
        int dockH = readDimension(dockBg, "mHeight", false);
        MainHook.log(TAG + " detected radius=" + radius
                + " dock size=" + dockW + "x" + dockH);

        DockLiquidGlassView glass = LiquidGlassFactory.create(
                dockBg, workspace, config.glass, config.dock, false, SQUIRCLE_CP);
        glass.setId(View.generateViewId());

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

        // Preserve the MiuiX drawable. Stroke is an overlay/foreground only.
        DockStrokeRenderer.configure(dockBg, config.dock, radius);

        backgroundRef = dockBg;
        glassRef = glass;
        hostRef = host;
        MainHook.log(TAG + " Prismal glass installed above MiuiX background");
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

        float radius = readRadius(dockBg);
        host.setVisibility(dockBg.getVisibility());
        host.setGeometry(radius, false, SQUIRCLE_CP);
        host.reloadOverlay(config.dock, config.glass);
        DockStrokeRenderer.configure(dockBg, config.dock, radius);
        host.invalidate();
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
