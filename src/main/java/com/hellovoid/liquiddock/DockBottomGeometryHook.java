package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * Final owner for the ordinary Dock's vertical offset.
 *
 * Historical LiquidDock versions added the custom offset inside
 * DeviceConfig.getHotSeatsMarginBottom(). That couples our feature to whichever vendor branch
 * happens to consume that getter (including Mingou/Laptop variants). This hook first neutralizes
 * that legacy compatibility delta when the getter exists, then applies the requested displacement
 * to the actual HotSeats View after layout. The visible Y therefore no longer depends on Mingou,
 * DeviceConfig margin routing, or whether the launcher used margin vs translation internally.
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
     * MainHook or the 307 compatibility path may still have installed the historical
     * getHotSeatsMarginBottom()+offset interceptor before this owner runs. Highest priority wraps
     * that interceptor, so subtracting exactly our configured delta restores the vendor result.
     * This is optional: if the vendor getter disappears, final visible-Y ownership still works.
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
                        if (MainHook.isWorkstationMode()) return result;
                        Object owner = chain.getThisObject();
                        if (owner instanceof View) {
                            // Positive historical bottom margin moved the Dock upward.
                            ((View) owner).offsetTopAndBottom(-bottomOffsetPx);
                        }
                        return result;
                    });
            MainHook.log("[DC] final Dock bottom geometry owner installed offset="
                    + bottomOffsetPx);
        } catch (Throwable error) {
            MainHook.log("[DC] final Dock bottom geometry owner unavailable: " + error);
        }
    }
}
