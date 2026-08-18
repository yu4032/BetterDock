package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Reuses the active MiuiX material's compositor blur configuration on the dedicated zero-copy
 * backdrop child. This keeps all sampling inside SurfaceFlinger while inheriting vendor blur type,
 * subtype, gradient parameters and material blend configuration when Launcher exposes them.
 */
final class Miuix307CompositorOpticsBridge {
    private static final String TAG = "[DC][ZC]";
    private static Class<?> loggedAppliedClass;
    private static Class<?> loggedUnavailableClass;

    private Miuix307CompositorOpticsBridge() {}

    static boolean applyVendorBlurConfig(View vendorMaterial, View target) {
        if (vendorMaterial == null || target == null) return false;
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
