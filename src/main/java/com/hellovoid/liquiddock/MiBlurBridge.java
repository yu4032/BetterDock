package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * Cached direct bridge to HyperOS/MIUI SurfaceFlinger self-blur.
 *
 * This intentionally bypasses HyperMaterialUtils.isEnable(): the launcher process has
 * already been validated to expose the public-reflectable View.setMi* entry points.
 */
final class MiBlurBridge {
    private static final int SELF_BLUR_ENHANCE_FLAG = 0x200;

    private static final Method SET_MI_SELF_BLUR;
    private static final Method SET_PASS_TEXTURE_SCALE;
    private static final Method SET_MI_SELF_BLUR_ENHANCE_FLAG;
    private static final boolean AVAILABLE;

    static {
        Method selfBlur = null;
        Method textureScale = null;
        Method enhanceFlag = null;
        boolean available = false;
        try {
            selfBlur = View.class.getMethod("setMiSelfBlur", int.class, ArrayList.class);
            textureScale = View.class.getMethod("setPassTextureScale", float.class);
            enhanceFlag = View.class.getMethod(
                    "setMiSelfBlurEnhanceFlag", int.class, int.class);
            available = true;
        } catch (Throwable ignored) {
            // Fail closed. DockLiquidGlassView keeps the shader backend active.
        }
        SET_MI_SELF_BLUR = selfBlur;
        SET_PASS_TEXTURE_SCALE = textureScale;
        SET_MI_SELF_BLUR_ENHANCE_FLAG = enhanceFlag;
        AVAILABLE = available;
    }

    private MiBlurBridge() {}

    static boolean isAvailable() {
        return AVAILABLE;
    }

    static boolean applyContentBlur(View view, int radiusPx, float textureScale) {
        if (!AVAILABLE || view == null) return false;
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

    static void clearContentBlur(View view) {
        if (!AVAILABLE || view == null) return;
        try {
            SET_MI_SELF_BLUR.invoke(view, 0, null);
        } catch (Throwable ignored) {}
        try {
            SET_PASS_TEXTURE_SCALE.invoke(view, 1f);
        } catch (Throwable ignored) {}
    }
}
