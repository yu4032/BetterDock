package com.hellovoid.liquiddock;

import java.lang.reflect.Method;

/** Exact completion edge for HyperOS APP -> HOME CLOSE_TO_HOME animations. */
final class AppHomeAnimationHook {
    private AppHomeAnimationHook() {}

    static void install(ClassLoader classLoader) {
        try {
            // Device Launcher logs identify this as the user animator listener attached to
            // RectFParams(animType=CLOSE_TO_HOME, taskFromApp=true, needFinishOnAnimEnd=true).
            Class<?> listenerClass = Class.forName(
                    "com.miui.home.recents.GestureModeApp$8", false, classLoader);
            int hooked = 0;
            for (Method method : listenerClass.getDeclaredMethods()) {
                if (!"onAnimationEnd".equals(method.getName())) continue;
                HookUtil.hook(method, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    // CaptureSceneState accepts this only while a GestureToHome from APP is
                    // pending, so stale/cancelled callbacks cannot force HOME.
                    HomeOwnershipRuntime.onAppHomeAnimationEnd();
                    return result;
                });
                hooked++;
            }
            if (hooked == 0) {
                MainHook.log("[DC] APP HOME animation hook unavailable: onAnimationEnd missing");
            } else {
                MainHook.log("[DC] APP HOME CLOSE_TO_HOME completion hooked methods=" + hooked);
            }
        } catch (Throwable error) {
            // CaptureSceneState has a bounded watchdog solely for this vendor mismatch case.
            MainHook.log("[DC] APP HOME animation hook unavailable: " + error);
        }
    }
}
