package com.hellovoid.liquiddock;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * Thin hooking utility backed by libxposed API101.
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

    /** Convenience: find + hook a method when the class is known. */
    public static void hookMethod(Class<?> clazz, String methodName,
                                   Class<?>[] paramTypes,
                                   XposedInterface.Hooker callback) {
        try {
            Method m = clazz.getDeclaredMethod(methodName, paramTypes);
            hook(m, callback);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(methodName + " on " + clazz.getName(), e);
        }
    }

    /** Convenience: find + hook with string param type specs. */
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
            Method m = clazz.getDeclaredMethod(methodName, types);
            hook(m, callback);
        } catch (Exception e) {
            throw new RuntimeException(methodName, e);
        }
    }

    // ── Reflection helpers (no Xposed dependency) ───────────────────

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
}
