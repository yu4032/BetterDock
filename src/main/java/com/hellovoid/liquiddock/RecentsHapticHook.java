package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Method;

/** Version-tolerant adapter for the launcher's semantic enter-recents haptic event. */
final class RecentsHapticHook {
    interface Listener { void onEnterRecents(); }
    private RecentsHapticHook() {}

    static void install(ClassLoader classLoader, Listener listener) {
        String[] implementations = {
                "com.miui.home.launcher.common.HapticFeedbackCompatLinear",
                "com.miui.home.launcher.common.HapticFeedbackCompatV2",
                "com.miui.home.launcher.common.HapticFeedbackCompatNormal"
        };
        int hooked = 0;
        for (String name : implementations) {
            try {
                Class<?> type = Class.forName(name, false, classLoader);
                Method method = HookUtil.findMethodExact(type, "performEnterRecent",
                        new Class<?>[]{View.class});
                HookUtil.hook(method, chain -> {
                    // 307 real-glass uses the vendor pass-window blur underneath Prismal.
                    // Never pre-arm DockLiquidGlassView's legacy Recents capture state here;
                    // the specialized pipeline was already installed and MainHook returned
                    // before installing HOME/APP/RECENTS lifecycle hooks.
                    if (!Miuix307MaterialPipeline.isInstalled()) {
                        listener.onEnterRecents();
                    }
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                });
                hooked++;
            } catch (Throwable error) {
                MainHook.log("[DC] recents haptic hook unavailable for " + name
                        + ": " + error);
            }
        }
        MainHook.log("[DC] recents haptic trigger hooked implementations=" + hooked);
    }
}
