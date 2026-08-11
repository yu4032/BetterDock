package com.hellovoid.betterdock;

import android.view.View;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

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
                Class<?> type = XposedHelpers.findClass(name, classLoader);
                XposedHelpers.findAndHookMethod(type, "performEnterRecent", View.class,
                        new XC_MethodHook() {
                            @Override protected void beforeHookedMethod(MethodHookParam param) {
                                listener.onEnterRecents();
                            }
                        });
                hooked++;
            } catch (Throwable error) {
                XposedBridge.log("[DC] recents haptic hook unavailable for " + name
                        + ": " + error);
            }
        }
        XposedBridge.log("[DC] recents haptic trigger hooked implementations=" + hooked);
    }
}
