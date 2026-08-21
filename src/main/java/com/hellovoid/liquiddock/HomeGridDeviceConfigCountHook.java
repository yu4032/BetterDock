package com.hellovoid.liquiddock;

import java.lang.reflect.Method;
import java.util.Locale;

import io.github.libxposed.api.XposedInterface;

/**
 * 10x6-only owner for DeviceConfig's process-wide workspace cell counts.
 *
 * CellLayout/GridConfig can already be 6x10 while older DeviceConfig-derived drag code still sees
 * the legacy 4x8 profile. That split caps portrait drag coordinates at y=7 even though the live
 * occupancy matrix has ten rows. Rewrite only the verified legacy 4/8 intermediates to 6/10 and
 * leave unrelated counts untouched.
 */
final class HomeGridDeviceConfigCountHook {
    private static final String DEVICE_CONFIG = "com.miui.home.launcher.DeviceConfig";
    private static boolean installed;
    private static boolean loggedXRewrite;
    private static boolean loggedYRewrite;

    private HomeGridDeviceConfigCountHook() {}

    static void install(ClassLoader classLoader, boolean customGridEnabled,
                        HomeGridProfile selectedProfile) {
        if (installed || !customGridEnabled || selectedProfile != HomeGridProfile.GRID_10X6) {
            return;
        }
        try {
            Class<?> deviceConfig = Class.forName(DEVICE_CONFIG, false, classLoader);
            hookCount(deviceConfig, "getCellCountX", true, selectedProfile);
            hookCount(deviceConfig, "getCellCountY", false, selectedProfile);
            installed = true;
            MainHook.log("[DC][GRID10] DeviceConfig cell-count ownership installed");
        } catch (Throwable error) {
            MainHook.log("[DC][GRID10] DeviceConfig cell-count ownership unavailable: " + error);
        }
    }

    private static void hookCount(Class<?> owner, String methodName, boolean xAxis,
                                  HomeGridProfile profile) throws NoSuchMethodException {
        Method method = HookUtil.findMethodExact(owner, methodName, new Class<?>[0]);
        Api101Bridge.module().hook(method)
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    if (!(result instanceof Integer) || MainHook.isWorkstationMode()
                            || isExcludedCall()) {
                        return result;
                    }
                    int current = (Integer) result;
                    int target = HomeGridCountPolicy.profileRewrite(profile, current);
                    if (target != current) logRewriteOnce(xAxis, methodName, current, target);
                    return target;
                });
    }

    private static void logRewriteOnce(boolean xAxis, String methodName, int current, int target) {
        if (xAxis) {
            if (loggedXRewrite) return;
            loggedXRewrite = true;
        } else {
            if (loggedYRewrite) return;
            loggedYRewrite = true;
        }
        MainHook.log("[DC][GRID10] DeviceConfig " + methodName + " "
                + current + "->" + target);
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
