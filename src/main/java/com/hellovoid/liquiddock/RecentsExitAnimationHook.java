package com.hellovoid.liquiddock;

import java.lang.reflect.Method;

/**
 * Exact Launcher lifecycle for the visual Recents exit animation.
 *
 * ExitOverviewStateEvent is emitted when the transition starts, before the last composed
 * frames reach HOME. RecentsContainer keeps the authoritative animation lifetime through
 * setIsExitRecentsAnimating(true/false), so LiquidDock keeps live Recents capture until the
 * matching false edge arrives.
 */
final class RecentsExitAnimationHook {
    private static boolean exitAnimating;

    private RecentsExitAnimationHook() {}

    static void install(ClassLoader classLoader) {
        try {
            Class<?> container = Class.forName(
                    "com.miui.home.recents.views.RecentsContainer", false, classLoader);
            Method method = HookUtil.findMethodExact(container, "setIsExitRecentsAnimating",
                    new Class<?>[]{boolean.class});
            HookUtil.hook(method, chain -> {
                boolean active = (Boolean) chain.getArgs().get(0);
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));

                if (active) {
                    exitAnimating = true;
                    HomeOwnershipRuntime.onRecentsExitAnimationChanged(true);
                } else if (exitAnimating) {
                    // Ignore unrelated false writes during initialization/state cleanup. Only a
                    // false edge paired with an observed true edge owns the live-Recents exit.
                    exitAnimating = false;
                    HomeOwnershipRuntime.onRecentsExitAnimationChanged(false);
                }
                return result;
            });
            MainHook.log("[DC] Recents exit animation lifecycle hooked");
        } catch (Throwable error) {
            MainHook.log("[DC] Recents exit animation hook unavailable: " + error);
        }
    }
}
