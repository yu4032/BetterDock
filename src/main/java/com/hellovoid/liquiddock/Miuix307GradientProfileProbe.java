package com.hellovoid.liquiddock;

import android.os.Parcel;
import android.view.View;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Passive discovery of compositor gradient-blur profiles already used by MiuiX.
 *
 * DEX inspection of Launcher 4.50 shows that MiuiBlurUtils.setBlurConfig() calls
 * setBackgroundGradientBlurParams() only when BlurConfig.blurType == 2 and
 * blurExtraParams is non-null. The shape of blurExtraParams is intentionally not guessed here:
 * it is serialized with a variable length. Instead, observe profiles that MIUI itself decodes or
 * applies and log an immutable copy for the next zero-copy optics step.
 */
final class Miuix307GradientProfileProbe {
    private static final String TAG = "[DC][ZC]";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private static volatile float[] lastParams;
    private static volatile int lastSubtype;
    private static volatile int lastHash;
    private static volatile String lastSource;

    private Miuix307GradientProfileProbe() {}

    static void install(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        installGradientCallObserver(classLoader);
        installMaterialConfigObserver(classLoader);
        installMaterialTokenObserver(classLoader);
    }

    private static void installGradientCallObserver(ClassLoader classLoader) {
        try {
            Class<?> blurUtils = Class.forName("miuix.core.util.MiuiBlurUtils", false, classLoader);
            HookUtil.hookMethod(blurUtils, "setBackgroundGradientBlurParams",
                    new Class<?>[]{View.class, float[].class, int.class}, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object result = chain.proceed(args);
                        if (args.length >= 3 && args[1] instanceof float[] && args[2] instanceof Number) {
                            View sourceView = args[0] instanceof View ? (View) args[0] : null;
                            String source = "MiuiBlurUtils:" + (sourceView != null
                                    ? sourceView.getClass().getSimpleName() : "null-view");
                            observe(source, ((Number) args[2]).intValue(), (float[]) args[1]);
                        }
                        return result;
                    });
            MainHook.log(TAG + " native gradient call observer installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " native gradient call observer unavailable: " + error);
        }
    }

    private static void installMaterialConfigObserver(ClassLoader classLoader) {
        try {
            Class<?> materialConfig = Class.forName(
                    "miuix.core.util.MaterialConfig", false, classLoader);
            int hooked = 0;
            for (Constructor<?> ctor : materialConfig.getDeclaredConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length != 1 || params[0] != Parcel.class) continue;
                HookUtil.hook(ctor, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    inspectMaterialConfig(chain.getThisObject());
                    return result;
                });
                hooked++;
            }
            MainHook.log(TAG + " MaterialConfig gradient observer hooked=" + hooked);
        } catch (Throwable error) {
            MainHook.log(TAG + " MaterialConfig gradient observer unavailable: " + error);
        }
    }

    private static void installMaterialTokenObserver(ClassLoader classLoader) {
        try {
            Class<?> materialToken = Class.forName(
                    "miuix.theme.token.MaterialToken", false, classLoader);
            int hooked = 0;
            for (Constructor<?> ctor : materialToken.getDeclaredConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length != 1 || params[0] != Parcel.class) continue;
                HookUtil.hook(ctor, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    inspectBlurHolder(chain.getThisObject(), "MaterialToken");
                    return result;
                });
                hooked++;
            }
            MainHook.log(TAG + " MaterialToken gradient observer hooked=" + hooked);
        } catch (Throwable error) {
            MainHook.log(TAG + " MaterialToken gradient observer unavailable: " + error);
        }
    }

    private static void inspectMaterialConfig(Object materialConfig) {
        if (materialConfig == null) return;
        try {
            Method getter = findNoArg(materialConfig.getClass(), "getBlurConfig");
            if (getter == null) return;
            Object blurConfig = getter.invoke(materialConfig);
            inspectBlurHolder(blurConfig, "MaterialConfig");
        } catch (Throwable error) {
            MainHook.log(TAG + " MaterialConfig gradient inspection unavailable: " + error);
        }
    }

    private static void inspectBlurHolder(Object holder, String source) {
        if (holder == null) return;
        try {
            int type = readIntField(holder, "blurType", -1);
            if (type != 2) return;
            int subtype = readIntField(holder, "blurSubType", 0);
            Object value = readField(holder, "blurExtraParams");
            if (!(value instanceof float[])) return;
            float[] params = (float[]) value;
            observe(source + ":" + holder.getClass().getSimpleName(), subtype, params);
        } catch (Throwable error) {
            MainHook.log(TAG + " native gradient holder inspection unavailable: " + error);
        }
    }

    private static void observe(String source, int subtype, float[] params) {
        if (!isValid(params)) return;
        float[] copy = params.clone();
        int hash = 31 * subtype + Arrays.hashCode(copy);
        if (hash == lastHash && source.equals(lastSource)) return;
        lastParams = copy;
        lastSubtype = subtype;
        lastHash = hash;
        lastSource = source;
        MainHook.log(TAG + " native gradient profile observed source=" + source
                + " subtype=" + subtype + " len=" + copy.length
                + " params=" + Arrays.toString(copy));
    }

    private static boolean isValid(float[] params) {
        if (params == null || params.length == 0 || params.length > 128) return false;
        for (float value : params) {
            if (Float.isNaN(value) || Float.isInfinite(value)) return false;
        }
        return true;
    }

    private static int readIntField(Object owner, String name, int fallback) throws Exception {
        Object value = readField(owner, name);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static Object readField(Object owner, String name) throws Exception {
        Field field = findField(owner.getClass(), name);
        if (field == null) return null;
        return field.get(owner);
    }

    private static Field findField(Class<?> start, String name) {
        for (Class<?> current = start; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (Throwable ignored) {}
        }
        return null;
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

    static float[] lastParamsForTests() {
        float[] params = lastParams;
        return params != null ? params.clone() : null;
    }

    static int lastSubtypeForTests() {
        return lastSubtype;
    }
}
