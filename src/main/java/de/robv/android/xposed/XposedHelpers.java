package de.robv.android.xposed;

import com.hellovoid.liquiddock.Api101Bridge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.libxposed.api.XposedInterface;

/** Subset of XposedHelpers used by LiquidDock, implemented on top of API101 + reflection. */
public final class XposedHelpers {
    private XposedHelpers() {}

    public static Class<?> findClass(String className, ClassLoader classLoader) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static Class<?> findClassIfExists(String className, ClassLoader classLoader) {
        try { return Class.forName(className, false, classLoader); }
        catch (Throwable ignored) { return null; }
    }

    public static Object findAndHookMethod(String className, ClassLoader classLoader,
                                           String methodName, Object... parameterTypesAndCallback) {
        return findAndHookMethod(findClass(className, classLoader), methodName,
                parameterTypesAndCallback);
    }

    public static Object findAndHookMethod(Class<?> clazz, String methodName,
                                           Object... parameterTypesAndCallback) {
        if (parameterTypesAndCallback.length == 0
                || !(parameterTypesAndCallback[parameterTypesAndCallback.length - 1]
                instanceof XC_MethodHook)) {
            throw new IllegalArgumentException("last argument must be XC_MethodHook");
        }
        XC_MethodHook callback = (XC_MethodHook)
                parameterTypesAndCallback[parameterTypesAndCallback.length - 1];
        Object[] specs = Arrays.copyOf(parameterTypesAndCallback,
                parameterTypesAndCallback.length - 1);
        Class<?>[] parameterTypes = new Class<?>[specs.length];
        for (int i = 0; i < specs.length; i++) {
            Object spec = specs[i];
            if (spec instanceof Class<?>) {
                parameterTypes[i] = (Class<?>) spec;
            } else if (spec instanceof String) {
                parameterTypes[i] = findClass((String) spec, clazz.getClassLoader());
            } else {
                throw new IllegalArgumentException("unsupported parameter type spec: " + spec);
            }
        }
        Method method = findMethodExact(clazz, methodName, parameterTypes);
        return XposedBridge.hookExecutable(method, callback);
    }

    public static Object callMethod(Object target, String methodName, Object... args) {
        if (target == null) throw new NullPointerException("target == null");
        Method method = findMethodBestMatch(target.getClass(), methodName, args, false);
        return invokeOrigin(method, target, args);
    }

    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) {
        Method method = findMethodBestMatch(clazz, methodName, args, true);
        return invokeOrigin(method, null, args);
    }

    private static Object invokeOrigin(Method method, Object receiver, Object[] args) {
        try {
            method.setAccessible(true);
            return Api101Bridge.module().getInvoker(method)
                    .setType(XposedInterface.Invoker.Type.ORIGIN)
                    .invoke(receiver, args);
        } catch (Throwable e) {
            throw new RuntimeException("invoke failed: " + method, e);
        }
    }

    public static Object getObjectField(Object target, String fieldName) {
        try {
            Field field = findField(target.getClass(), fieldName);
            return field.get(target);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static int getIntField(Object target, String fieldName) {
        try {
            Field field = findField(target.getClass(), fieldName);
            return field.getInt(target);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static long getLongField(Object target, String fieldName) {
        try {
            Field field = findField(target.getClass(), fieldName);
            return field.getLong(target);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean getBooleanField(Object target, String fieldName) {
        try {
            Field field = findField(target.getClass(), fieldName);
            return field.getBoolean(target);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void setObjectField(Object target, String fieldName, Object value) {
        try {
            findField(target.getClass(), fieldName).set(target, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void setIntField(Object target, String fieldName, int value) {
        try {
            findField(target.getClass(), fieldName).setInt(target, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void setLongField(Object target, String fieldName, long value) {
        try {
            findField(target.getClass(), fieldName).setLong(target, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Object getStaticObjectField(Class<?> clazz, String fieldName) {
        try { return findField(clazz, fieldName).get(null); }
        catch (Throwable e) { throw new RuntimeException(e); }
    }

    public static void setStaticObjectField(Class<?> clazz, String fieldName, Object value) {
        try { findField(clazz, fieldName).set(null, value); }
        catch (Throwable e) { throw new RuntimeException(e); }
    }

    private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(clazz.getName() + "#" + name);
    }

    private static Method findMethodExact(Class<?> clazz, String name, Class<?>[] parameterTypes) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new RuntimeException(new NoSuchMethodException(clazz.getName() + "#" + name
                + Arrays.toString(parameterTypes)));
    }

    private static Method findMethodBestMatch(Class<?> clazz, String name, Object[] args,
                                              boolean requireStatic) {
        List<Method> candidates = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name)) continue;
                if (requireStatic && !Modifier.isStatic(method.getModifiers())) continue;
                if (method.getParameterCount() != args.length) continue;
                if (parametersMatch(method.getParameterTypes(), args)) candidates.add(method);
            }
            current = current.getSuperclass();
        }
        if (candidates.isEmpty()) {
            throw new RuntimeException(new NoSuchMethodException(clazz.getName() + "#" + name
                    + " args=" + Arrays.toString(args)));
        }
        Method result = candidates.get(0);
        result.setAccessible(true);
        return result;
    }

    private static boolean parametersMatch(Class<?>[] types, Object[] args) {
        for (int i = 0; i < types.length; i++) {
            Object arg = args[i];
            if (arg == null) {
                if (types[i].isPrimitive()) return false;
                continue;
            }
            if (!wrap(types[i]).isAssignableFrom(arg.getClass())) return false;
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == char.class) return Character.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        return type;
    }
}
