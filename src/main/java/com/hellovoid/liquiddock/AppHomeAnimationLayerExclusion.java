package com.hellovoid.liquiddock;

import android.view.SurfaceControl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Captures HyperOS Launcher's real HOME Activity leash for APP -> HOME backdrop exclusion.
 *
 * Device logs show the vendor icon-layer leash remains null on the FloatingIconView2 path.
 * The same transition, however, delivers ActivityStartInfo.mHomeActivityLeash as a valid
 * SurfaceControl from FastLaunchWindowElement$getActivityOptions$1.startActivityFinished.
 * FloatingIconView2 is rendered inside the Launcher Activity hierarchy, so excluding that
 * Activity leash removes Launcher-drawn transition pixels without mutating the visible animation.
 */
final class AppHomeAnimationLayerExclusion {
    private static final String FAST_LAUNCH_CALLBACK =
            "com.miui.home.recents.anim.FastLaunchWindowElement$getActivityOptions$1";
    private static final String START_FINISHED_METHOD = "startActivityFinished";
    private static final String HOME_ACTIVITY_LEASH_FIELD = "mHomeActivityLeash";

    private static volatile SurfaceControl homeActivitySurface;
    private static boolean installed;

    private AppHomeAnimationLayerExclusion() {}

    static synchronized void install(ClassLoader classLoader) {
        if (installed) return;
        try {
            Class<?> callbackClass = Class.forName(FAST_LAUNCH_CALLBACK, false, classLoader);
            int hooked = 0;
            for (Method method : callbackClass.getDeclaredMethods()) {
                if (!START_FINISHED_METHOD.equals(method.getName())) continue;
                HookUtil.hook(method, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    captureHomeActivityLeash(args);
                    return chain.proceed(args);
                });
                hooked++;
            }
            if (hooked == 0) {
                MainHook.log("[DC] APP HOME activity leash hook unavailable: "
                        + START_FINISHED_METHOD + " missing");
                return;
            }
            installed = true;
            MainHook.log("[DC] APP HOME activity leash hook installed methods=" + hooked);
        } catch (Throwable error) {
            MainHook.log("[DC] APP HOME activity leash hook unavailable: " + error);
        }
    }

    private static void captureHomeActivityLeash(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) return;
        Object activityStartInfo = args[0];
        try {
            Field leashField = HookUtil.findField(
                    activityStartInfo.getClass(), HOME_ACTIVITY_LEASH_FIELD);
            Object value = leashField.get(activityStartInfo);
            if (!(value instanceof SurfaceControl)) {
                MainHook.log("[DC] APP HOME activity leash missing value=" + value);
                return;
            }
            SurfaceControl surface = (SurfaceControl) value;
            if (!surface.isValid()) {
                MainHook.log("[DC] APP HOME activity leash invalid");
                return;
            }
            homeActivitySurface = surface;
            MainHook.log("[DC] APP HOME activity leash captured " + surface);
        } catch (Throwable error) {
            MainHook.log("[DC] APP HOME activity leash read failed: " + error);
        }
    }

    static SurfaceControl currentValidSurface() {
        SurfaceControl surface = homeActivitySurface;
        if (surface == null) return null;
        try {
            if (surface.isValid()) return surface;
        } catch (Throwable ignored) {
        }
        homeActivitySurface = null;
        return null;
    }
}
