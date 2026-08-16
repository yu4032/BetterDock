package com.hellovoid.liquiddock;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Passive observer of the existing ShellTaskOrganizer executor. */
final class SystemUiTaskExecutorSource {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static volatile Executor currentExecutor;

    private SystemUiTaskExecutorSource() {}

    static void install(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> organizerClass = Class.forName(
                    "com.android.wm.shell.ShellTaskOrganizer", false, classLoader);
            Constructor<?>[] constructors = organizerClass.getDeclaredConstructors();
            if (constructors.length == 0) {
                throw new IllegalStateException("ShellTaskOrganizer has no constructor");
            }
            for (Constructor<?> constructor : constructors) {
                HookUtil.hook(constructor, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    try {
                        observeExisting(chain.getThisObject());
                    } catch (Throwable error) {
                        Api101Bridge.log("[DC] observe ShellTaskOrganizer executor unavailable", error);
                    }
                    return result;
                });
            }
            Api101Bridge.log("[DC] SystemUI task executor hook installed");
        } catch (Throwable error) {
            Api101Bridge.log("[DC] SystemUI task executor source unavailable", error);
        }
    }

    static void observeExisting(Object organizer) throws Exception {
        if (organizer == null) return;
        Method getExecutor = HookUtil.findMethodBestMatch(
                organizer.getClass(), "getExecutor", new Object[0], false);
        Object value = getExecutor.invoke(organizer);
        if (!(value instanceof Executor)) {
            throw new IllegalStateException("ShellTaskOrganizer#getExecutor is not Executor");
        }
        currentExecutor = (Executor) value;
    }

    static Executor executor() {
        return currentExecutor;
    }
}
