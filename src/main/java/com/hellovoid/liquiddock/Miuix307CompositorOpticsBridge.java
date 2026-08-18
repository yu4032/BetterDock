package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Reuses the vendor Dock compositor configuration on LiquidDock's dedicated zero-copy backdrop.
 *
 * JADX of Launcher 4.50 confirms HotSeatsListContentBlurBackground2.addBlur() calls
 * BlurUtilities.setBackgroundBlur(view, 100, cornerRadii, blendConfig) and then
 * setBackgroundBlurAlpha(view, getParentAlpha()). The previous experiment incorrectly tried to
 * replace that 100 through setMiBackgroundBlurRadius(), which is a different API channel.
 */
final class Miuix307CompositorOpticsBridge {
    private static final String TAG = "[DC][ZC]";
    private static final String COMPAT_BLUR_BACKGROUND2 =
            "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2";
    private static final String BLUR_UTILITIES =
            "com.miui.home.launcher.common.BlurUtilities";
    private static final String COLOR_RESOURCES = "com.miui.home.R$color";

    private static Class<?> loggedAppliedClass;
    private static Class<?> loggedUnavailableClass;

    private Miuix307CompositorOpticsBridge() {}

    static boolean applyVendorBlurConfig(
            View vendorMaterial, View target, float cornerRadiusPx, int blurRadiusPx) {
        if (vendorMaterial == null || target == null) return false;
        Class<?> sourceClass = vendorMaterial.getClass();

        if (COMPAT_BLUR_BACKGROUND2.equals(sourceClass.getName())) {
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
            ClassLoader loader = sourceClass.getClassLoader();
            Class<?> blurUtilities = Class.forName(BLUR_UTILITIES, false, loader);
            Method setBackgroundBlur = blurUtilities.getDeclaredMethod("setBackgroundBlur",
                    View.class, Integer.TYPE, float[].class, int[][].class);
            setBackgroundBlur.setAccessible(true);
            Method setBackgroundBlurAlpha = blurUtilities.getDeclaredMethod(
                    "setBackgroundBlurAlpha", View.class, Float.TYPE);
            setBackgroundBlurAlpha.setAccessible(true);

            Class<?> colors = Class.forName(COLOR_RESOURCES, false, loader);
            int darkResId = readStaticInt(colors,
                    "hotseats_list_content_background_blur_color_dark");
            int lightResId = readStaticInt(colors,
                    "hotseats_list_content_background_blur_color_light");
            int darkColor = vendorMaterial.getContext().getColor(darkResId);
            int lightColor = vendorMaterial.getContext().getColor(lightResId);

            float[] cornerRadii = new float[]{
                    cornerRadiusPx, cornerRadiusPx, cornerRadiusPx, cornerRadiusPx};
            int[][] blendConfig = new int[][]{
                    new int[]{106, darkColor},
                    new int[]{100, lightColor}
            };

            setBackgroundBlur.invoke(null, target,
                    Integer.valueOf(blurRadiusPx), cornerRadii, blendConfig);

            Method getParentAlpha = sourceClass.getDeclaredMethod("getParentAlpha");
            getParentAlpha.setAccessible(true);
            Object alphaValue = getParentAlpha.invoke(vendorMaterial);
            float parentAlpha = alphaValue instanceof Number
                    ? ((Number) alphaValue).floatValue() : 1.0f;
            setBackgroundBlurAlpha.invoke(null, target, Float.valueOf(parentAlpha));

            if (loggedAppliedClass != sourceClass) {
                loggedAppliedClass = sourceClass;
                MainHook.log(TAG + " exact Launcher background blur active source="
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

    /** MaterialConfig path retained for native MiuiX material implementations. */
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

    private static int readStaticInt(Class<?> cls, String name) throws Exception {
        Field field = cls.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(null);
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
