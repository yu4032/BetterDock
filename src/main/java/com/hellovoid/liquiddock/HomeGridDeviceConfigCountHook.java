package com.hellovoid.liquiddock;

import java.lang.reflect.Method;
import java.util.Locale;

import io.github.libxposed.api.XposedInterface;

/** Keeps DeviceConfig workspace counts consistent with the selected 10x6 profile. */
final class HomeGridDeviceConfigCountHook {
    private static final String DEVICE_CONFIG = "com.miui.home.launcher.DeviceConfig";
    private static boolean installed;

    private HomeGridDeviceConfigCountHook() {}

    static void install(ClassLoader classLoader, boolean customGridEnabled,
                        HomeGridProfile selectedProfile) {
        if (installed || !customGridEnabled || selectedProfile != HomeGridProfile.GRID_10X6) {
            return;
        }
        try {
            Class<?> deviceConfig = Class.forName(DEVICE_CONFIG, false, classLoader);
            hookCount(deviceConfig, "getCellCountX", selectedProfile);
            hookCount(deviceConfig, "getCellCountY", selectedProfile);
            installed = true;
        } catch (Throwable error) {
            MainHook.log("[DC] 10x6 DeviceConfig counts unavailable: " + error);
        }
    }

    private static void hookCount(Class<?> owner, String methodName, HomeGridProfile profile)
            throws NoSuchMethodException {
        Method method = HookUtil.findMethodExact(owner, methodName, new Class<?>[0]);
        Api101Bridge.module().hook(method)
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    if (!(result instanceof Integer) || MainHook.isWorkstationMode()
                            || isExcludedCall()) {
                        return result;
                    }
                    return HomeGridCountPolicy.profileRewrite(profile, (Integer) result);
                });
    }

    private static boolean isExcludedCall() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String name = frame.getClassName().toLowerCase(Locale.ROOT);
            if (name.contains(".folder.") || name.contains("allapps")
                    || name.contains(".laptop.") || name.contains("hotseats")
                    || name.contains("dockbar")) {
                return true;
            }
        }
        return false;
    }
}
