package com.hellovoid.liquiddock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Compatibility fallback for HyperOS builds whose DragObject completion callback is not the exact
 * zero-argument declaration expected by Miuix307DragCaptureHook.
 *
 * The primary hook remains untouched. If it already owns the zero-argument declaration, this
 * scanner skips only that one method and still hooks every other instance overload across the
 * class hierarchy. Nested overload forwarding is collapsed to one notification per outer vendor
 * callback.
 */
final class Miuix307DropFinishCompatHook {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final ThreadLocal<Integer> CALLBACK_DEPTH =
            ThreadLocal.withInitial(() -> 0);

    private Miuix307DropFinishCompatHook() {}

    static void install(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;

        final boolean primaryInstalled = primaryHookInstalled();
        try {
            Class<?> dragObjectClass = Class.forName(
                    "com.miui.home.launcher.DragObject", false, classLoader);
            int hooked = 0;
            int skippedPrimary = 0;
            Class<?> cursor = dragObjectClass;
            while (cursor != null && cursor != Object.class) {
                for (Method method : cursor.getDeclaredMethods()) {
                    if (!"onDropAnimationFinished".equals(method.getName())
                            || Modifier.isStatic(method.getModifiers())) {
                        continue;
                    }
                    if (primaryInstalled && method.getParameterCount() == 0) {
                        skippedPrimary++;
                        continue;
                    }
                    HookUtil.hook(method, chain -> {
                        int depth = CALLBACK_DEPTH.get();
                        CALLBACK_DEPTH.set(depth + 1);
                        Object result;
                        try {
                            result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        } finally {
                            CALLBACK_DEPTH.set(depth);
                        }

                        if (depth == 0) {
                            Object dragObject = chain.getThisObject();
                            Api101Bridge.log("[DC][DRAG] compat vendor finish method="
                                    + methodSignature(method) + " object=" + objectId(dragObject));
                            HookUtil.invokeStatic(Miuix307DragCaptureHook.class,
                                    "onDropAnimationFinished", dragObject);
                        }
                        return result;
                    });
                    hooked++;
                }
                cursor = cursor.getSuperclass();
            }

            if (hooked > 0 || primaryInstalled) {
                setPrimaryHookInstalled(true);
                Api101Bridge.log("[DC][DRAG] vendor finish coverage primary=" + primaryInstalled
                        + " compat=" + hooked + " skippedPrimary=" + skippedPrimary);
            } else {
                Api101Bridge.log("[DC][DRAG] no vendor finish overload found; fallback remains active");
            }
        } catch (Throwable error) {
            Api101Bridge.log("[DC][DRAG] compat vendor finish hook unavailable", error);
        }
    }

    private static boolean primaryHookInstalled() {
        try {
            Field field = Miuix307DragCaptureHook.class.getDeclaredField(
                    "dropAnimationFinishHookInstalled");
            field.setAccessible(true);
            return field.getBoolean(null);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void setPrimaryHookInstalled(boolean installed) {
        try {
            Field field = Miuix307DragCaptureHook.class.getDeclaredField(
                    "dropAnimationFinishHookInstalled");
            field.setAccessible(true);
            field.setBoolean(null, installed);
        } catch (Throwable error) {
            Api101Bridge.log("[DC][DRAG] unable to publish compat vendor hook state", error);
        }
    }

    private static String methodSignature(Method method) {
        StringBuilder out = new StringBuilder(method.getDeclaringClass().getSimpleName())
                .append('#').append(method.getName()).append('(');
        Class<?>[] params = method.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) out.append(',');
            out.append(params[i].getSimpleName());
        }
        return out.append(')').toString();
    }

    private static String objectId(Object value) {
        if (value == null) return "null";
        return value.getClass().getSimpleName() + "@"
                + Integer.toHexString(System.identityHashCode(value));
    }
}
