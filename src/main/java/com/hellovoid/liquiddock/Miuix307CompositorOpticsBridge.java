package com.hellovoid.liquiddock;

import android.graphics.Rect;
import android.view.View;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reuses the vendor Dock compositor configuration on LiquidDock's dedicated zero-copy backdrop.
 *
 * Launcher 4.50 has two material implementations. The compatibility
 * HotSeatsListContentBlurBackground2 path does not expose MaterialConfig; instead its private
 * addBlur(View,float) method builds the exact four-corner geometry and blend colors, then calls
 * View.setBackgroundBlur(...) through Launcher BlurUtilities. Reusing that method keeps the
 * vendor's native compositor setup without reconstructing hidden parameters or reading pixels
 * back. Newer MiuiX material implementations can still use the MaterialConfig fallback below.
 */
final class Miuix307CompositorOpticsBridge {
    private static final String TAG = "[DC][ZC]";
    private static final String COMPAT_BLUR_BACKGROUND2 =
            "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2";
    private static final String BLUR_UTILITIES =
            "com.miui.home.launcher.common.BlurUtilities";

    private static Class<?> loggedAppliedClass;
    private static Class<?> loggedUnavailableClass;

    /**
     * Read-only probe scope. It is populated only while Launcher addBlur() is configuring our
     * zero-copy backdrop, so normal Launcher background-blur traffic is never logged as ours.
     */
    private static final ThreadLocal<View> compatProbeTarget = new ThreadLocal<>();
    private static boolean compatProbeInstalled;
    private static boolean compatProbeUnavailableLogged;
    private static View compatProbeLoggedTarget;
    private static String compatProbeLoggedSignature;

    /**
     * Second-stage read-only probe for hidden View optics calls reached from addBlur(). Signatures
     * are logged once per target so repeated geometry synchronization does not flood logcat.
     */
    private static boolean compatViewSetterProbeInstalled;
    private static View compatViewSetterLoggedTarget;
    private static final Set<String> compatViewSetterLoggedSignatures = new HashSet<>();

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

    /**
     * Exact Launcher 4.50 compatibility path. addBlur() internally creates the vendor corner
     * array and theme blend-color table and applies them through View.setBackgroundBlur(). The
     * vendor method uses its own blur radius, so restore LiquidDock's GUI-selected radius
     * immediately afterwards through the same setMiBackgroundBlurRadius API family.
     */
    private static boolean applyCompatBlurBackground2(
            View vendorMaterial, View target, float cornerRadiusPx, int blurRadiusPx) {
        Class<?> sourceClass = vendorMaterial.getClass();
        try {
            installCompatBlurProbe(sourceClass.getClassLoader());
            installCompatViewSetterProbe(sourceClass.getClassLoader());
            Method addBlur = sourceClass.getDeclaredMethod("addBlur", View.class, float.class);
            addBlur.setAccessible(true);

            View previousProbeTarget = compatProbeTarget.get();
            compatProbeTarget.set(target);
            try {
                addBlur.invoke(vendorMaterial, target, cornerRadiusPx);
            } finally {
                if (previousProbeTarget != null) {
                    compatProbeTarget.set(previousProbeTarget);
                } else {
                    compatProbeTarget.remove();
                }
            }

            if (!MiBlurBridge.setPassWindowBlurRadius(target, blurRadiusPx)) {
                logUnavailableOnce(sourceClass, "restore-radius");
                return false;
            }
            if (loggedAppliedClass != sourceClass) {
                loggedAppliedClass = sourceClass;
                MainHook.log(TAG + " compat compositor optics active source="
                        + sourceClass.getSimpleName()
                        + " cornerRadius=" + cornerRadiusPx
                        + " blurRadius=" + blurRadiusPx);
            }
            return true;
        } catch (Throwable error) {
            logUnavailableOnce(sourceClass, "addBlur-" + error.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Observe, but never modify, the exact BlurBackground2 arguments generated by Launcher 4.50.
     * This distinguishes a blur+blend compositor contract from any additional spatial-optics API
     * without guessing vendor magic numbers. The hook is process-wide but logging is scoped by the
     * ThreadLocal above to the target currently being configured through addBlur().
     */
    static synchronized void installCompatBlurProbe(ClassLoader classLoader) {
        if (compatProbeInstalled || classLoader == null) return;
        try {
            HookUtil.hookMethod(classLoader, BLUR_UTILITIES, "setBackgroundBlur", chain -> {
                Object targetArg = chain.getArgs().size() > 0 ? chain.getArgs().get(0) : null;
                View expectedTarget = compatProbeTarget.get();
                if (expectedTarget != null && targetArg == expectedTarget) {
                    int vendorRadius = chain.getArgs().size() > 1
                            && chain.getArgs().get(1) instanceof Number
                            ? ((Number) chain.getArgs().get(1)).intValue() : -1;
                    float[] cornerRadii = chain.getArgs().size() > 2
                            && chain.getArgs().get(2) instanceof float[]
                            ? (float[]) chain.getArgs().get(2) : null;
                    int[][] blendConfig = chain.getArgs().size() > 3
                            && chain.getArgs().get(3) instanceof int[][]
                            ? (int[][]) chain.getArgs().get(3) : null;

                    String signature = vendorRadius
                            + "|" + Arrays.toString(cornerRadii)
                            + "|" + formatBlendConfig(blendConfig);
                    if (compatProbeLoggedTarget != expectedTarget
                            || !signature.equals(compatProbeLoggedSignature)) {
                        compatProbeLoggedTarget = expectedTarget;
                        compatProbeLoggedSignature = signature;
                        MainHook.log(TAG + " compat blur args target="
                                + expectedTarget.getClass().getSimpleName()
                                + " vendorRadius=" + vendorRadius
                                + " cornerRadii=" + Arrays.toString(cornerRadii)
                                + " blendConfig=" + formatBlendConfig(blendConfig));
                    }
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            }, View.class, int.class, float[].class, int[][].class);
            compatProbeInstalled = true;
            MainHook.log(TAG + " compat blur argument probe installed");
        } catch (Throwable error) {
            if (!compatProbeUnavailableLogged) {
                compatProbeUnavailableLogged = true;
                MainHook.log(TAG + " compat blur argument probe unavailable: " + error);
            }
        }
    }

    /**
     * Installs narrow hooks for hidden View optics setters known to exist on HyperOS variants.
     * Every hook is observational: it only logs calls whose receiver is the current ThreadLocal
     * target while vendor addBlur() is on the stack, then proceeds with the original call unchanged.
     */
    static synchronized void installCompatViewSetterProbe(ClassLoader classLoader) {
        if (compatViewSetterProbeInstalled || classLoader == null) return;

        ArrayList<String> unavailable = new ArrayList<>();
        int installed = 0;
        installed += hookCompatViewSetter(classLoader, unavailable,
                "setMiBackgroundBlurType", int.class) ? 1 : 0;
        installed += hookCompatViewSetter(classLoader, unavailable,
                "setMiBackgroundBlurScaleRatio", float.class) ? 1 : 0;
        installed += hookCompatViewSetter(classLoader, unavailable,
                "setMiBackgroundBlurEnhanceFlag", int.class, int.class) ? 1 : 0;
        installed += hookCompatViewSetter(classLoader, unavailable,
                "setBackgroundBlurAlpha", float.class) ? 1 : 0;
        installed += hookCompatViewSetter(classLoader, unavailable,
                "setBackgroundBlurCrop", boolean.class, Rect.class) ? 1 : 0;
        installed += hookCompatViewSetter(classLoader, unavailable,
                "setBackgroundGradientBlurParams", float[].class, int.class) ? 1 : 0;
        installed += hookCompatViewSetter(classLoader, unavailable,
                "setMiColorAdjust", ArrayList.class) ? 1 : 0;
        installed += hookCompatViewSetter(classLoader, unavailable,
                "setMiBackgroundBlendColors", ArrayList.class) ? 1 : 0;
        installed += hookCompatViewSetter(classLoader, unavailable,
                "setMixEffectEnabled", boolean.class) ? 1 : 0;
        installed += hookCompatViewSetter(classLoader, unavailable,
                "setPassTextureScale", float.class) ? 1 : 0;
        installed += hookCompatViewSetter(classLoader, unavailable,
                "setMiBloomStroke", float[].class) ? 1 : 0;

        compatViewSetterProbeInstalled = true;
        MainHook.log(TAG + " compat view setter probe installed hooks=" + installed
                + " unavailable=" + unavailable);
    }

    private static boolean hookCompatViewSetter(
            ClassLoader classLoader, List<String> unavailable,
            String methodName, Class<?>... parameterTypes) {
        try {
            HookUtil.hookMethod(classLoader, View.class.getName(), methodName, chain -> {
                View expectedTarget = compatProbeTarget.get();
                Object actualTarget = chain.getThisObject();
                if (expectedTarget != null && actualTarget == expectedTarget) {
                    String signature = methodName + formatProbeArgs(chain.getArgs());
                    synchronized (Miuix307CompositorOpticsBridge.class) {
                        if (compatViewSetterLoggedTarget != expectedTarget) {
                            compatViewSetterLoggedTarget = expectedTarget;
                            compatViewSetterLoggedSignatures.clear();
                        }
                        if (compatViewSetterLoggedSignatures.add(signature)) {
                            MainHook.log(TAG + " compat view setter target="
                                    + expectedTarget.getClass().getSimpleName()
                                    + " method=" + methodName
                                    + " args=" + formatProbeArgs(chain.getArgs()));
                        }
                    }
                }
                return chain.proceed();
            }, parameterTypes);
            return true;
        } catch (Throwable error) {
            unavailable.add(methodName + ":" + error.getClass().getSimpleName());
            return false;
        }
    }

    private static String formatProbeArgs(List<Object> args) {
        StringBuilder out = new StringBuilder("[");
        for (int index = 0; index < args.size(); index++) {
            if (index > 0) out.append(',');
            Object arg = args.get(index);
            if (arg instanceof float[]) {
                out.append(Arrays.toString((float[]) arg));
            } else if (arg instanceof int[]) {
                out.append(Arrays.toString((int[]) arg));
            } else if (arg instanceof int[][]) {
                out.append(formatBlendConfig((int[][]) arg));
            } else if (arg instanceof Object[]) {
                out.append(Arrays.deepToString((Object[]) arg));
            } else {
                out.append(String.valueOf(arg));
            }
        }
        return out.append(']').toString();
    }

    private static String formatBlendConfig(int[][] blendConfig) {
        if (blendConfig == null) return "null";
        StringBuilder out = new StringBuilder("[");
        for (int rowIndex = 0; rowIndex < blendConfig.length; rowIndex++) {
            if (rowIndex > 0) out.append(',');
            int[] row = blendConfig[rowIndex];
            if (row == null) {
                out.append("null");
                continue;
            }
            out.append('[');
            for (int column = 0; column < row.length; column++) {
                if (column > 0) out.append(',');
                out.append(String.format(Locale.ROOT, "0x%08X", row[column]));
            }
            out.append(']');
        }
        return out.append(']').toString();
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
