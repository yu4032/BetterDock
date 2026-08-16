package com.hellovoid.liquiddock;

import java.lang.reflect.Method;

/** Samples HyperOS Launcher's own wallpaper visual-scale animation without mutating it. */
final class WallpaperZoomHook {
    private static final String LOCAL = "com.miui.home.recents.anim.LocalWallpaperElement";
    private static final String SYSTEM = "com.miui.home.recents.anim.SystemWallpaperElement";
    private static boolean installed;

    private WallpaperZoomHook() {}

    static synchronized void install(ClassLoader classLoader) {
        if (installed) return;
        hookLocalScale(classLoader);
        hookDiagnostics(classLoader, LOCAL, "local");
        hookDiagnostics(classLoader, SYSTEM, "system");
        installed = true;
    }

    private static void hookLocalScale(ClassLoader classLoader) {
        try {
            Class<?> type = Class.forName(LOCAL, false, classLoader);
            int hooked = 0;
            for (Method method : type.getDeclaredMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (!"updateTargetParams".equals(method.getName())
                        || params.length != 1 || params[0] != float.class) continue;
                HookUtil.hook(method, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    Object result = chain.proceed(args);
                    if (args.length == 1 && args[0] instanceof Number) {
                        float scale = ((Number) args[0]).floatValue();
                        if (MainHook.debugLogging) {
                            MainHook.log("[DC] wallpaper zoom visualScale=" + scale);
                        }
                        WallpaperZoomRuntime.onScale(scale);
                    }
                    return result;
                });
                hooked++;
            }
            MainHook.log("[DC] wallpaper zoom LocalWallpaperElement hooks=" + hooked);
        } catch (Throwable error) {
            MainHook.log("[DC] wallpaper zoom local hook unavailable: " + error);
        }
    }

    /** Diagnostic only: identifies which vendor wallpaper implementation is active. */
    private static void hookDiagnostics(ClassLoader classLoader, String className, String label) {
        try {
            Class<?> type = Class.forName(className, false, classLoader);
            for (Method method : type.getDeclaredMethods()) {
                String name = method.getName();
                if (!("animTo".equals(name) || "setTo".equals(name))) continue;
                HookUtil.hook(method, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    if (MainHook.debugLogging) {
                        Object target = args.length > 0 && args[0] != null
                                ? HookUtil.invoke(args[0], "getZoomOut") : null;
                        MainHook.log("[DC] wallpaper zoom path=" + label
                                + " method=" + name + " targetScale=" + target);
                    }
                    return chain.proceed(args);
                });
            }
        } catch (Throwable error) {
            MainHook.log("[DC] wallpaper zoom " + label + " diagnostics unavailable: " + error);
        }
    }
}
