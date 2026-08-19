package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Reuses the vendor Dock compositor configuration on LiquidDock's dedicated zero-copy backdrop.
 *
 * JADX of Launcher 4.50 confirms HotSeatsListContentBlurBackground2.addBlur() ultimately calls the
 * public HyperOS framework methods View.setBackgroundBlur(100, cornerRadii, blendConfig) and
 * View.setBackgroundBlurAlpha(getParentAlpha()). The compat path invokes those framework methods
 * directly so LSPosed does not have to cross the Launcher classloader just to reach BlurUtilities.
 * Native MiuiX material hosts instead reuse their MaterialConfig through MiuiBlurUtils.
 */
final class Miuix307CompositorOpticsBridge {
    private static final String TAG = "[DC][ZC]";
    private static final String DARK_COLOR_NAME =
            "hotseats_list_content_background_blur_color_dark";
    private static final String LIGHT_COLOR_NAME =
            "hotseats_list_content_background_blur_color_light";

    private static Class<?> loggedAppliedClass;
    private static Class<?> loggedUnavailableClass;

    private Miuix307CompositorOpticsBridge() {}

    static boolean supportsZeroCopyBackdrop(View vendorMaterial) {
        return vendorMaterial != null
                && Miuix307MaterialHostPolicy.supportsZeroCopyBackdrop(
                        vendorMaterial.getClass().getName());
    }

    static boolean usesExactBackgroundBlur(View vendorMaterial) {
        return vendorMaterial != null
                && Miuix307MaterialHostPolicy.usesExactBackgroundBlur(
                        vendorMaterial.getClass().getName());
    }

    static boolean applyVendorBlurConfig(
            View vendorMaterial, View target, float cornerRadiusPx, int blurRadiusPx) {
        if (vendorMaterial == null || target == null) return false;

        if (usesExactBackgroundBlur(vendorMaterial)) {
            return applyCompatBlurBackground2(
                    vendorMaterial, target, Math.max(0f, cornerRadiusPx), blurRadiusPx);
        }
        return applyMaterialConfig(vendorMaterial, target);
    }

    /** Exact Launcher 4.50 addBlur semantics, with only the literal blur radius substituted. */
    private static boolean applyCompatBlurBackground2(
            View vendorMaterial, View target, float cornerRadiusPx, int blurRadiusPx) {
        Class<?> sourceClass = vendorMaterial.getClass();
        try {
            Method setBackgroundBlur = View.class.getMethod("setBackgroundBlur",
                    Integer.TYPE, float[].class, int[][].class);
            Method setBackgroundBlurAlpha =
                    View.class.getMethod("setBackgroundBlurAlpha", Float.TYPE);

            String packageName = vendorMaterial.getContext().getPackageName();
            int darkResId = vendorMaterial.getResources().getIdentifier(
                    DARK_COLOR_NAME, "color", packageName);
            int lightResId = vendorMaterial.getResources().getIdentifier(
                    LIGHT_COLOR_NAME, "color", packageName);
            if (darkResId == 0 || lightResId == 0) {
                logUnavailableOnce(sourceClass, "exact-background-blur-color-resource");
                return false;
            }
            int darkColor = vendorMaterial.getContext().getColor(darkResId);
            int lightColor = vendorMaterial.getContext().getColor(lightResId);

            float[] cornerRadii = new float[]{
                    cornerRadiusPx, cornerRadiusPx, cornerRadiusPx, cornerRadiusPx};
            int[][] blendConfig = new int[][]{
                    new int[]{106, darkColor},
                    new int[]{100, lightColor}
            };

            Object blurResult = setBackgroundBlur.invoke(target,
                    Integer.valueOf(blurRadiusPx), cornerRadii, blendConfig);
            if (blurResult instanceof Boolean && !((Boolean) blurResult)) {
                logUnavailableOnce(sourceClass, "exact-background-blur-rejected");
                return false;
            }

            Method getParentAlpha = sourceClass.getDeclaredMethod("getParentAlpha");
            getParentAlpha.setAccessible(true);
            Object alphaValue = getParentAlpha.invoke(vendorMaterial);
            float parentAlpha = alphaValue instanceof Number
                    ? ((Number) alphaValue).floatValue() : 1.0f;
            Object alphaResult = setBackgroundBlurAlpha.invoke(target, Float.valueOf(parentAlpha));
            if (alphaResult instanceof Boolean && !((Boolean) alphaResult)) {
                logUnavailableOnce(sourceClass, "exact-background-blur-alpha-rejected");
                return false;
            }

            if (loggedAppliedClass != sourceClass) {
                loggedAppliedClass = sourceClass;
                MainHook.log(TAG + " exact framework background blur active source="
                        + sourceClass.getSimpleName()
                        + " radius=" + blurRadiusPx
                        + " cornerRadius=" + cornerRadiusPx);
            }
            return true;
        } catch (Throwable error) {
            logUnavailableOnce(sourceClass,
                    "exact-background-blur-" + error.getClass().getSimpleName());
            return false;
        }
    }

    /** MaterialConfig path used by native MiuiX material implementations. */
    private static boolean applyMaterialConfig(View vendorMaterial, View target) {
        Class<?> sourceClass = vendorMaterial.getClass();
        try {
            Method getCurrentMaterial = findNoArg(sourceClass, "getCurrentMaterial");
            if (getCurrentMaterial == null) {
                logUnavailableOnce(sourceClass, "getCurrentMaterial");
                return false;
            }
            Object material = getCurrentMaterial.invoke(vendorMaterial);
            if (material == null) {
                logUnavailableOnce(sourceClass, "material-null");
                return false;
            }

            Method getBlurConfig = findNoArg(material.getClass(), "getBlurConfig");
            if (getBlurConfig == null) {
                logUnavailableOnce(sourceClass, "getBlurConfig");
                return false;
            }
            Object blurConfig = getBlurConfig.invoke(material);
            if (blurConfig == null) {
                logUnavailableOnce(sourceClass, "blur-config-null");
                return false;
            }

            ClassLoader loader = sourceClass.getClassLoader();
            Class<?> blurUtils = Class.forName("miuix.core.util.MiuiBlurUtils", false, loader);
            Method setBlurConfig = findSetBlurConfig(blurUtils, blurConfig.getClass());
            if (setBlurConfig == null) {
                logUnavailableOnce(sourceClass, "setBlurConfig");
                return false;
            }

            float density = target.getResources().getDisplayMetrics().density;
            setBlurConfig.invoke(null, target, density, blurConfig);
            if (loggedAppliedClass != sourceClass) {
                loggedAppliedClass = sourceClass;
                MainHook.log(TAG + " vendor compositor optics active source="
                        + sourceClass.getSimpleName()
                        + " material=" + material.getClass().getSimpleName()
                        + " blurConfig=" + blurConfig.getClass().getSimpleName());
            }
            return true;
        } catch (Throwable error) {
            logUnavailableOnce(sourceClass, error.getClass().getSimpleName());
            return false;
        }
    }

    private static Method findNoArg(Class<?> start, String name) {
        for (Class<?> current = start; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Method findSetBlurConfig(Class<?> blurUtils, Class<?> blurConfigClass) {
        for (Method method : blurUtils.getDeclaredMethods()) {
            if (!"setBlurConfig".equals(method.getName())
                    || !Modifier.isStatic(method.getModifiers())) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 3) continue;
            if (!View.class.isAssignableFrom(params[0]) || params[1] != float.class) continue;
            if (!params[2].isAssignableFrom(blurConfigClass)
                    && !blurConfigClass.isAssignableFrom(params[2])) continue;
            method.setAccessible(true);
            return method;
        }
        return null;
    }

    private static void logUnavailableOnce(Class<?> sourceClass, String reason) {
        if (loggedUnavailableClass == sourceClass) return;
        loggedUnavailableClass = sourceClass;
        MainHook.log(TAG + " vendor compositor optics unavailable source="
                + sourceClass.getSimpleName() + " reason=" + reason);
    }
}
