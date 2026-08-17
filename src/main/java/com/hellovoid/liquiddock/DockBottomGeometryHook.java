package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewParent;

import java.lang.reflect.Method;
import java.util.Locale;

import io.github.libxposed.api.XposedInterface;

/**
 * Final owner for the ordinary Floating Dock's vertical offset.
 *
 * Historical LiquidDock versions added the custom delta inside
 * DeviceConfig.getHotSeatsMarginBottom(). That getter is only one vendor routing path and
 * its base value itself contains Mingou/Laptop branches. Keep a compatibility neutralizer
 * for already-installed legacy interceptors, but apply the actual feature to the visible
 * HotSeats View after layout. The ordinary Dock therefore no longer depends on Mingou being
 * installed or on LauncherModeController reporting a particular transient state.
 */
final class DockBottomGeometryHook {
    private static final String HOT_SEATS = "com.miui.home.launcher.hotseats.HotSeats";
    private static final String DEVICE_CONFIG = "com.miui.home.launcher.DeviceConfig";

    private DockBottomGeometryHook() {}

    static void install(ClassLoader classLoader) {
        LiquidDockConfig config = LiquidDockConfig.load();
        if (!config.enabled || !config.dock.enabled) return;

        float scale = config.dock.dimensionsDp
                ? android.content.res.Resources.getSystem().getDisplayMetrics().density : 1f;
        int bottomOffsetPx = Math.round(config.dock.bottomOffset * scale);
        if (bottomOffsetPx == 0) return;

        neutralizeLegacyMarginDelta(classLoader, bottomOffsetPx);
        installFinalHotSeatsOffset(classLoader, bottomOffsetPx);
    }

    /**
     * MainHook or the 307 compatibility path can still contain the historical
     * getHotSeatsMarginBottom()+offset interceptor. Highest priority wraps that interceptor;
     * subtracting exactly our configured delta restores the vendor getter result. This is
     * compatibility-only and is not the geometry owner.
     */
    private static void neutralizeLegacyMarginDelta(ClassLoader classLoader, int bottomOffsetPx) {
        try {
            Class<?> deviceConfig = Class.forName(DEVICE_CONFIG, false, classLoader);
            Method getter = HookUtil.findMethodExact(
                    deviceConfig, "getHotSeatsMarginBottom", new Class<?>[0]);
            Api101Bridge.module().hook(getter)
                    .setPriority(XposedInterface.PRIORITY_HIGHEST)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        // The historical hooks themselves skip while MainHook thinks the
                        // workstation is active, so only neutralize when they could have added.
                        if (!(result instanceof Integer) || MainHook.isWorkstationMode()) {
                            return result;
                        }
                        return (Integer) result - bottomOffsetPx;
                    });
            MainHook.log("[DC] legacy Dock bottom margin delta neutralized offset="
                    + bottomOffsetPx);
        } catch (Throwable error) {
            MainHook.log("[DC] legacy Dock bottom margin neutralizer unavailable: " + error);
        }
    }

    private static void installFinalHotSeatsOffset(ClassLoader classLoader, int bottomOffsetPx) {
        try {
            Class<?> hotSeats = Class.forName(HOT_SEATS, false, classLoader);
            Method onLayout = HookUtil.findMethodExact(hotSeats, "onLayout",
                    new Class<?>[]{boolean.class, int.class, int.class, int.class, int.class});
            Api101Bridge.module().hook(onLayout)
                    .setPriority(XposedInterface.PRIORITY_HIGHEST)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object owner = chain.getThisObject();
                        if (owner instanceof View) {
                            View view = (View) owner;
                            // Do not trust the global Mingou/Laptop flag here: uninstalling the
                            // companion launcher can leave that vendor state on a different path
                            // from the actual visible hierarchy. Exclude only a real laptop Dock.
                            if (!isLaptopDockHierarchy(view)) {
                                // Positive historical bottom margin moved the Dock upward.
                                view.offsetTopAndBottom(-bottomOffsetPx);
                            }
                        }
                        return result;
                    });
            MainHook.log("[DC] final Dock bottom geometry owner installed offset="
                    + bottomOffsetPx);
        } catch (Throwable error) {
            MainHook.log("[DC] final Dock bottom geometry owner unavailable: " + error);
        }
    }

    static boolean isLaptopDockHierarchy(View view) {
        ViewParent parent = view == null ? null : view.getParent();
        int depth = 0;
        while (parent != null && depth++ < 8) {
            String name = parent.getClass().getName().toLowerCase(Locale.ROOT);
            if (name.contains(".laptop.")
                    || name.contains("dockcontainerview")
                    || name.contains("laptopdock")) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }
}
