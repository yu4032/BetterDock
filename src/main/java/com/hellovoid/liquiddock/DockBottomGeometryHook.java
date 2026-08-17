package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewParent;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedInterface;

/** Own the ordinary Floating Dock's Y offset without changing Workspace geometry. */
final class DockBottomGeometryHook {
    private static final String HOT_SEATS = "com.miui.home.launcher.hotseats.HotSeats";
    private static final String DEVICE_CONFIG = "com.miui.home.launcher.DeviceConfig";
    private static final String GRID_CONTROLLER = "com.miui.home.launcher.grid.GridController";
    private static final WeakHashMap<View, Float> VENDOR_TRANSLATION_Y = new WeakHashMap<>();

    private DockBottomGeometryHook() {}

    static void install(ClassLoader classLoader) {
        LiquidDockConfig config = LiquidDockConfig.load();
        if (!config.enabled || !config.dock.enabled) return;
        float scale = config.dock.dimensionsDp
                ? android.content.res.Resources.getSystem().getDisplayMetrics().density : 1f;
        int bottomOffsetPx = Math.round(config.dock.bottomOffset * scale);
        if (bottomOffsetPx == 0) return;
        installStockMarginFence(classLoader);
        installVisualTranslationOwner(classLoader, bottomOffsetPx);
    }

    /** Preserve the exact stock margin so LiquidDock never changes Workspace/Dock-window reserve. */
    private static void installStockMarginFence(ClassLoader classLoader) {
        try {
            Class<?> deviceConfig = Class.forName(DEVICE_CONFIG, false, classLoader);
            Method getter = HookUtil.findMethodExact(
                    deviceConfig, "getHotSeatsMarginBottom", new Class<?>[0]);
            Api101Bridge.module().hook(getter)
                    .setPriority(XposedInterface.PRIORITY_HIGHEST)
                    .intercept(chain -> {
                        try {
                            Object controller = HookUtil.invokeStatic(GRID_CONTROLLER, "getInstance");
                            Object grid = HookUtil.invoke(controller, "getActiveGridConfigInDock");
                            Object bottom = HookUtil.invoke(grid, "getBottom");
                            Object mingou = HookUtil.invokeStatic(
                                    DEVICE_CONFIG, "getMingouLaptopDockBottomOffsetPx");
                            if (bottom instanceof Number && mingou instanceof Number) {
                                return DockBottomGeometryPolicy.stockMargin(
                                        ((Number) bottom).intValue(), ((Number) mingou).intValue());
                            }
                        } catch (Throwable error) {
                            MainHook.log("[DC] stock Dock margin reconstruction failed: " + error);
                        }
                        return chain.proceed();
                    });
            MainHook.log("[DC] stock Dock margin fence installed");
        } catch (Throwable error) {
            MainHook.log("[DC] stock Dock margin fence unavailable: " + error);
        }
    }

    /** Add the custom delta only to the vendor's visual translationY animation value. */
    private static void installVisualTranslationOwner(ClassLoader classLoader, int bottomOffsetPx) {
        try {
            Class<?> hotSeats = Class.forName(HOT_SEATS, false, classLoader);
            Method translation = HookUtil.findMethodExact(
                    hotSeats, "setTranslationY", new Class<?>[]{float.class});
            Api101Bridge.module().hook(translation)
                    .setPriority(XposedInterface.PRIORITY_HIGHEST)
                    .intercept(chain -> {
                        Object owner = chain.getThisObject();
                        Object value = chain.getArg(0);
                        if (!(owner instanceof View) || !(value instanceof Number)) {
                            return chain.proceed();
                        }
                        View view = (View) owner;
                        float vendorY = ((Number) value).floatValue();
                        synchronized (VENDOR_TRANSLATION_Y) {
                            VENDOR_TRANSLATION_Y.put(view, vendorY);
                        }
                        if (view.getParent() == null || isLaptopDockHierarchy(view)) {
                            return chain.proceed();
                        }
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        args[0] = DockBottomGeometryPolicy.visualTranslationY(
                                vendorY, bottomOffsetPx);
                        return chain.proceed(args);
                    });

            Method attached = HookUtil.findMethodExact(
                    hotSeats, "onAttachedToWindow", new Class<?>[0]);
            Api101Bridge.module().hook(attached)
                    .setPriority(XposedInterface.PRIORITY_HIGHEST)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object owner = chain.getThisObject();
                        if (!(owner instanceof View)) return result;
                        View view = (View) owner;
                        if (isLaptopDockHierarchy(view)) return result;
                        float vendorY;
                        synchronized (VENDOR_TRANSLATION_Y) {
                            Float remembered = VENDOR_TRANSLATION_Y.get(view);
                            if (remembered == null) {
                                remembered = view.getTranslationY();
                                VENDOR_TRANSLATION_Y.put(view, remembered);
                            }
                            vendorY = remembered;
                        }
                        float target = DockBottomGeometryPolicy.visualTranslationY(
                                vendorY, bottomOffsetPx);
                        if (Math.abs(view.getTranslationY() - target) > 0.01f) {
                            view.setTranslationY(vendorY);
                        }
                        return result;
                    });
            MainHook.log("[DC] Dock bottom visual translation owner installed offset="
                    + bottomOffsetPx);
        } catch (Throwable error) {
            MainHook.log("[DC] Dock bottom visual translation owner unavailable: " + error);
        }
    }

    static boolean isLaptopDockHierarchy(View view) {
        ViewParent parent = view == null ? null : view.getParent();
        int depth = 0;
        while (parent != null && depth++ < 8) {
            String name = parent.getClass().getName().toLowerCase(Locale.ROOT);
            if (name.contains(".laptop.")
                    || name.contains("dockcontainerview")
                    || name.contains("laptopdock")) return true;
            parent = parent.getParent();
        }
        return false;
    }
}
