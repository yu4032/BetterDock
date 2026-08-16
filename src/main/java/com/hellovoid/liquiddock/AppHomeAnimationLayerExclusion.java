package com.hellovoid.liquiddock;

import android.view.SurfaceControl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Captures HyperOS Launcher's root SurfaceControl used by the APP -> HOME floating-icon path.
 *
 * On this Launcher build WindowElement.bindIconLayerLeashIfNeeded() binds
 * mFloatingIconLayerLeash to Launcher.getRootView()'s SurfaceControl. FloatingIconView2 is
 * rendered inside that Launcher root rather than in its own SurfaceFlinger layer, so excluding
 * this leash is the precise compositor-side way to keep the animated icon out of backdrop
 * capture without mutating the visible Launcher animation.
 */
final class AppHomeAnimationLayerExclusion {
    private static final String WINDOW_ELEMENT = "com.miui.home.recents.anim.WindowElement";
    private static final String BIND_METHOD = "bindIconLayerLeashIfNeeded";
    private static final String LEASH_FIELD = "mFloatingIconLayerLeash";

    private static volatile SurfaceControl launcherRootSurface;
    private static boolean installed;

    private AppHomeAnimationLayerExclusion() {}

    static synchronized void install(ClassLoader classLoader) {
        if (installed) return;
        try {
            Class<?> windowElementClass = Class.forName(WINDOW_ELEMENT, false, classLoader);
            Method bind = HookUtil.findMethodExact(windowElementClass, BIND_METHOD, new Class<?>[0]);
            Field leashField = HookUtil.findField(windowElementClass, LEASH_FIELD);
            HookUtil.hook(bind, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                try {
                    Object value = leashField.get(chain.getThisObject());
                    if (value instanceof SurfaceControl) {
                        SurfaceControl surface = (SurfaceControl) value;
                        if (surface.isValid()) {
                            launcherRootSurface = surface;
                            MainHook.log("[DC] APP HOME Launcher root leash captured");
                        }
                    }
                } catch (Throwable error) {
                    MainHook.log("[DC] APP HOME Launcher root leash read failed: " + error);
                }
                return result;
            });
            installed = true;
            MainHook.log("[DC] APP HOME Launcher root leash hook installed");
        } catch (Throwable error) {
            MainHook.log("[DC] APP HOME Launcher root leash hook unavailable: " + error);
        }
    }

    static SurfaceControl currentValidSurface() {
        SurfaceControl surface = launcherRootSurface;
        if (surface == null) return null;
        try {
            if (surface.isValid()) return surface;
        } catch (Throwable ignored) {
        }
        launcherRootSurface = null;
        return null;
    }
}
