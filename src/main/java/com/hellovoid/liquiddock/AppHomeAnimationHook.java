package com.hellovoid.liquiddock;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Exact lifecycle edges for HyperOS APP -> HOME CLOSE_TO_HOME animations. */
final class AppHomeAnimationHook {
    private AppHomeAnimationHook() {}

    static void install(ClassLoader classLoader) {
        AppHomeAnimationLayerExclusion.install(classLoader);
        try {
            // Device Launcher logs identify this as the user animator listener attached to
            // RectFParams(animType=CLOSE_TO_HOME, taskFromApp=true, needFinishOnAnimEnd=true).
            Class<?> listenerClass = Class.forName(
                    "com.miui.home.recents.GestureModeApp$8", false, classLoader);

            int constructorsHooked = 0;
            for (Constructor<?> constructor : listenerClass.getDeclaredConstructors()) {
                HookUtil.hook(constructor, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    // Constructor creation is the first exact signal that the CLOSE_TO_HOME
                    // listener exists. It closes the race with Recents prearm / GestureToHome.
                    HomeOwnershipRuntime.onAppHomeAnimationStart();
                    return result;
                });
                constructorsHooked++;
            }

            int endsHooked = 0;
            for (Method method : listenerClass.getDeclaredMethods()) {
                if (!"onAnimationEnd".equals(method.getName())) continue;
                HookUtil.hook(method, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    // CaptureSceneState accepts HOME_ANIMATION_END only while the paired APP
                    // HOME lifecycle is pending, so stale/cancelled callbacks cannot force HOME.
                    HomeOwnershipRuntime.onAppHomeAnimationEnd();
                    return result;
                });
                endsHooked++;
            }

            if (constructorsHooked == 0 || endsHooked == 0) {
                MainHook.log("[DC] APP HOME animation hook incomplete constructors="
                        + constructorsHooked + " ends=" + endsHooked);
            } else {
                MainHook.log("[DC] APP HOME CLOSE_TO_HOME lifecycle hooked constructors="
                        + constructorsHooked + " ends=" + endsHooked);
            }
        } catch (Throwable error) {
            // CaptureSceneState has a bounded watchdog solely for this vendor mismatch case.
            MainHook.log("[DC] APP HOME animation hook unavailable: " + error);
        }
    }
}
