package com.hellovoid.liquiddock;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedInterface;

/**
 * Unified hooking + reflection utility backed by libxposed API101.
 * Replaces the old XposedHelpers/XposedBridge shim.
 */
public final class HookUtil {

    private HookUtil() {}

    // ── Hooking ──────────────────────────────────────────────────────

    /** Hook a method. The callback receives the chain directly;
     *  call {@code chain.proceed(args)} to invoke the original. */
    public static void hook(Method method,
                            XposedInterface.Hooker callback) {
        method.setAccessible(true);
        Api101Bridge.module().hook(method).intercept(callback);
    }

    /** Hook a constructor. */
    public static void hook(Constructor<?> ctor,
                            XposedInterface.Hooker callback) {
        ctor.setAccessible(true);
        Api101Bridge.module().hook(ctor).intercept(callback);
    }

    /** Find + hook a declared method (exact class only; no superclass walk). */
    public static void hookMethod(Class<?> clazz, String methodName,
                                   Class<?>[] paramTypes,
                                   XposedInterface.Hooker callback) {
        try {
            hook(findMethodExact(clazz, methodName, paramTypes), callback);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(methodName + " on " + clazz.getName(), e);
        }
    }

    /** Find + hook — walks the superclass chain to find the method. */
    public static void hookMethod(ClassLoader cl, String className,
                                   String methodName,
                                   XposedInterface.Hooker callback,
                                   Object... paramTypeSpecs) {
        try {
            Class<?> clazz = Class.forName(className, false, cl);
            Class<?>[] types = new Class<?>[paramTypeSpecs.length];
            for (int i = 0; i < paramTypeSpecs.length; i++) {
                Object spec = paramTypeSpecs[i];
                if (spec instanceof Class<?>) {
                    types[i] = (Class<?>) spec;
                } else if (spec instanceof String) {
                    types[i] = Class.forName((String) spec, false, cl);
                }
            }
            hook(findMethodExact(clazz, methodName, types), callback);
        } catch (Exception e) {
            throw new RuntimeException(methodName, e);
        }
    }

    // ── Field reflection ─────────────────────────────────────────────

    public static Field findField(Class<?> clazz, String name) {
        Class<?> c = clazz;
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        throw new RuntimeException("field not found: " + clazz.getName() + "#" + name);
    }

    public static Object getField(Object target, String name) {
        try { return findField(target.getClass(), name).get(target); }
        catch (Throwable e) { throw new RuntimeException(e); }
    }

    public static int getIntField(Object target, String name) {
        try { return findField(target.getClass(), name).getInt(target); }
        catch (Throwable e) { throw new RuntimeException(e); }
    }

    public static long getLongField(Object target, String name) {
        try { return findField(target.getClass(), name).getLong(target); }
        catch (Throwable e) { throw new RuntimeException(e); }
    }

    public static boolean getBooleanField(Object target, String name) {
        try { return findField(target.getClass(), name).getBoolean(target); }
        catch (Throwable e) { throw new RuntimeException(e); }
    }

    public static void setField(Object target, String name, Object value) {
        try { findField(target.getClass(), name).set(target, value); }
        catch (Throwable e) { throw new RuntimeException(e); }
    }

    public static void setIntField(Object target, String name, int value) {
        try { findField(target.getClass(), name).setInt(target, value); }
        catch (Throwable e) { throw new RuntimeException(e); }
    }

    public static void setLongField(Object target, String name, long value) {
        try { findField(target.getClass(), name).setLong(target, value); }
        catch (Throwable e) { throw new RuntimeException(e); }
    }

    // ── Method resolution (superclass-aware) ─────────────────────────

    /** Find the exact method declaration, walking the superclass chain.
     *  Throws RuntimeException with cause NoSuchMethodException if not found. */
    public static Method findMethodExact(Class<?> clazz, String name, Class<?>[] paramTypes)
            throws NoSuchMethodException {
        Class<?> c = clazz;
        while (c != null) {
            try {
                Method m = c.getDeclaredMethod(name, paramTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchMethodException(clazz.getName() + "#" + name);
    }

    /** Find the best-matching instance method by name + argument count,
     *  walking the superclass chain.  Throws NoSuchMethodException if
     *  no compatible method is found. */
    public static Method findMethodBestMatch(Class<?> clazz, String name,
                                              Object[] args,
                                              boolean requireStatic) {
        List<Method> candidates = new ArrayList<>();
        Class<?> c = clazz;
        while (c != null) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name)) continue;
                if (requireStatic && !Modifier.isStatic(m.getModifiers())) continue;
                if (!requireStatic && Modifier.isStatic(m.getModifiers())) continue;
                if (m.getParameterCount() != args.length) continue;
                if (parametersMatch(m.getParameterTypes(), args)) {
                    candidates.add(m);
                }
            }
            c = c.getSuperclass();
        }
        if (candidates.isEmpty()) {
            throw new RuntimeException("method not found: " + clazz.getName()
                    + "#" + name);
        }
        Method result = candidates.get(0);
        result.setAccessible(true);
        return result;
    }

    /** Invoke an instance method by name (best-match).  Returns null on failure. */
    public static Object invoke(Object target, String methodName, Object... args) {
        try {
            return findMethodBestMatch(target.getClass(), methodName, args, false)
                    .invoke(target, args);
        } catch (Throwable e) { return null; }
    }

    /** Invoke a static method on the given class by name (best-match).  Returns null on failure. */
    public static Object invokeStatic(Class<?> clazz, String methodName, Object... args) {
        try {
            return findMethodBestMatch(clazz, methodName, args, true)
                    .invoke(null, args);
        } catch (Throwable e) { return null; }
    }

    /** Invoke a static method by class name (best-match).  Returns null on failure. */
    public static Object invokeStatic(String className, String methodName, Object... args) {
        try { return invokeStatic(Class.forName(className), methodName, args); }
        catch (Throwable e) { return null; }
    }

    // ── internals ────────────────────────────────────────────────────

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
