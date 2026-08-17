package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Method;
import java.util.ArrayList;

/** Cached bridge to HyperOS/MIUI blur entry points used by both legacy and MiuiX docks. */
final class MiBlurBridge {
    private static final int SELF_BLUR_ENHANCE_FLAG = 0x200;

    // Legacy self/content blur.
    private static final Method SET_MI_SELF_BLUR;
    private static final Method SET_PASS_TEXTURE_SCALE;
    private static final Method SET_MI_SELF_BLUR_ENHANCE_FLAG;
    private static final boolean LEGACY_AVAILABLE;

    // Realtime pass-window/background blur used by the MiuiX dock.
    private static final Method SET_PASS_WINDOW_BLUR_ENABLED;
    private static final Method SET_MI_VIEW_BLUR_MODE;
    private static final Method SET_MI_BACKGROUND_BLUR_RADIUS;
    private static final boolean PASS_BLUR_AVAILABLE;

    static {
        Method selfBlur = null;
        Method textureScale = null;
        Method enhanceFlag = null;
        boolean legacyAvailable = false;
        try {
            selfBlur = View.class.getMethod("setMiSelfBlur", int.class, ArrayList.class);
            textureScale = View.class.getMethod("setPassTextureScale", float.class);
            enhanceFlag = View.class.getMethod(
                    "setMiSelfBlurEnhanceFlag", int.class, int.class);
            legacyAvailable = true;
        } catch (Throwable ignored) {
            // Fail closed; shader fallback remains available on legacy docks.
        }
        SET_MI_SELF_BLUR = selfBlur;
        SET_PASS_TEXTURE_SCALE = textureScale;
        SET_MI_SELF_BLUR_ENHANCE_FLAG = enhanceFlag;
        LEGACY_AVAILABLE = legacyAvailable;

        Method passEnabled = null;
        Method viewBlurMode = null;
        Method backgroundRadius = null;
        boolean passAvailable = false;
        try {
            passEnabled = View.class.getMethod("setPassWindowBlurEnabled", boolean.class);
            viewBlurMode = View.class.getMethod("setMiViewBlurMode", int.class);
            backgroundRadius = View.class.getMethod("setMiBackgroundBlurRadius", int.class);
            passAvailable = true;
        } catch (Throwable ignored) {
            // Some older builds expose only self blur. MiuiX caller will fall back cleanly.
        }
        SET_PASS_WINDOW_BLUR_ENABLED = passEnabled;
        SET_MI_VIEW_BLUR_MODE = viewBlurMode;
        SET_MI_BACKGROUND_BLUR_RADIUS = backgroundRadius;
        PASS_BLUR_AVAILABLE = passAvailable;
    }

    private MiBlurBridge() {}

    static boolean isAvailable() {
        return LEGACY_AVAILABLE;
    }

    static boolean applyContentBlur(View view, int radiusPx, float textureScale) {
        if (!LEGACY_AVAILABLE || view == null) return false;
        int safeRadius = Math.max(0, Math.min(400, radiusPx));
        float safeScale = Math.max(0.05f, Math.min(1f, textureScale));
        try {
            SET_MI_SELF_BLUR_ENHANCE_FLAG.invoke(
                    view, SELF_BLUR_ENHANCE_FLAG, SELF_BLUR_ENHANCE_FLAG);
            SET_MI_SELF_BLUR.invoke(view, safeRadius, null);
            Object result = SET_PASS_TEXTURE_SCALE.invoke(view, safeScale);
            if (result instanceof Boolean && !((Boolean) result)) {
                clearContentBlur(view);
                return false;
            }
            return true;
        } catch (Throwable e) {
            clearContentBlur(view);
            MainHook.log("[DC] advanced material blur unavailable; shader fallback: " + e);
            return false;
        }
    }

    /**
     * Restore only the compositor backdrop radius without replaying the vendor blur mode or
     * pass-window enable state. HyperOS 307 rewrites this radius during HOME/RECENTS transitions.
     */
    static boolean setPassWindowBlurRadius(View view, int radiusPx) {
        if (!PASS_BLUR_AVAILABLE || view == null) return false;
        int safeRadius = Math.max(0, Math.min(400, radiusPx));
        try {
            Object result = SET_MI_BACKGROUND_BLUR_RADIUS.invoke(view, safeRadius);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (Throwable e) {
            // Radius repair is deliberately non-destructive: do not disable the vendor material
            // when one repair attempt fails.
            MainHook.log("[DC] pass window blur radius repair failed: " + e);
            return false;
        }
    }

    /** Apply realtime blur to content behind {@code view}; this is not self/content blur. */
    static boolean applyPassWindowBlur(View view, int radiusPx) {
        if (!PASS_BLUR_AVAILABLE || view == null) return false;
        int safeRadius = Math.max(0, Math.min(400, radiusPx));
        try {
            SET_PASS_WINDOW_BLUR_ENABLED.invoke(view, true);
            SET_MI_VIEW_BLUR_MODE.invoke(view, 1);
            Object result = SET_MI_BACKGROUND_BLUR_RADIUS.invoke(view, safeRadius);
            if (result instanceof Boolean && !((Boolean) result)) {
                clearPassWindowBlur(view);
                return false;
            }
            return true;
        } catch (Throwable e) {
            clearPassWindowBlur(view);
            MainHook.log("[DC] pass window blur failed: " + e);
            return false;
        }
    }

    static void clearPassWindowBlur(View view) {
        if (!PASS_BLUR_AVAILABLE || view == null) return;
        try {
            SET_PASS_WINDOW_BLUR_ENABLED.invoke(view, false);
        } catch (Throwable ignored) {}
        try {
            SET_MI_VIEW_BLUR_MODE.invoke(view, 0);
        } catch (Throwable ignored) {}
        try {
            SET_MI_BACKGROUND_BLUR_RADIUS.invoke(view, 0);
        } catch (Throwable ignored) {}
    }

    /** Legacy self/content cleanup. 307 pass-window cleanup is explicit at its adapter boundary. */
    static void clearContentBlur(View view) {
        if (!LEGACY_AVAILABLE || view == null) return;
        try {
            SET_MI_SELF_BLUR.invoke(view, 0, null);
        } catch (Throwable ignored) {}
        try {
            SET_MI_SELF_BLUR_ENHANCE_FLAG.invoke(view, 0, SELF_BLUR_ENHANCE_FLAG);
        } catch (Throwable ignored) {}
        try {
            SET_PASS_TEXTURE_SCALE.invoke(view, 1f);
        } catch (Throwable ignored) {}
    }
}
