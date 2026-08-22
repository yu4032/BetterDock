package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewParent;

import java.lang.reflect.Method;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedInterface;

/** Own the ordinary Floating Dock's Y offset without changing Workspace reserve geometry. */
final class DockBottomGeometryHook {
    private static final String HOT_SEATS = "com.miui.home.launcher.hotseats.HotSeats";
    private static final String DEVICE_CONFIG = "com.miui.home.launcher.DeviceConfig";
    private static final String GRID_CONTROLLER = "com.miui.home.launcher.grid.GridController";
    private static final WeakHashMap<View, View.OnLayoutChangeListener> LAYOUT_OWNERS =
            new WeakHashMap<>();

    private DockBottomGeometryHook() {}

    static void install(ClassLoader classLoader) {
        LiquidDockConfig config = LiquidDockConfig.load();
        if (!config.enabled || !config.dock.enabled) return;
        float scale = config.dock.dimensionsDp
                ? android.content.res.Resources.getSystem().getDisplayMetrics().density : 1f;
        int bottomOffsetPx = Math.round(config.dock.bottomOffset * scale);
        if (bottomOffsetPx == 0) return;
        installStockMarginFence(classLoader);
        installVisualLayoutOwner(classLoader, bottomOffsetPx);
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

    /**
     * Keep the custom offset in HotSeats' real layout coordinates instead of translationY.
     * MIUI uses the layout position as the target for icon/fly-in animations, while translationY
     * is an independent animation property. Moving the bounds after each vendor layout keeps the
     * animation target and the final Dock position in the same coordinate space without changing
     * the parent's stock reserve or LayoutParams.
     */
    private static void installVisualLayoutOwner(ClassLoader classLoader, int bottomOffsetPx) {
        try {
            Class<?> hotSeats = Class.forName(HOT_SEATS, false, classLoader);
            Method attached = HookUtil.findMethodExact(
                    hotSeats, "onAttachedToWindow", new Class<?>[0]);
            Api101Bridge.module().hook(attached)
                    .setPriority(XposedInterface.PRIORITY_HIGHEST)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object owner = chain.getThisObject();
                        if (!(owner instanceof View) || !hotSeats.isInstance(owner)) return result;
                        View view = (View) owner;
                        if (isLaptopDockHierarchy(view)) return result;
                        ensureLayoutOwner(view, bottomOffsetPx);
                        // onAttached normally precedes first layout. requestLayout also covers
                        // re-attaches where the previous frame might otherwise be reused.
                        view.requestLayout();
                        return result;
                    });
            MainHook.log("[DC] Dock bottom visual layout owner installed offset="
                    + bottomOffsetPx);
        } catch (Throwable error) {
            MainHook.log("[DC] Dock bottom visual layout owner unavailable: " + error);
        }
    }

    private static void ensureLayoutOwner(View view, int bottomOffsetPx) {
        synchronized (LAYOUT_OWNERS) {
            if (LAYOUT_OWNERS.containsKey(view)) return;
            View.OnLayoutChangeListener listener = (v, left, top, right, bottom,
                                                     oldLeft, oldTop, oldRight, oldBottom) -> {
                if (v.getParent() == null || isLaptopDockHierarchy(v)) return;
                int deltaY = DockBottomGeometryPolicy.layoutDeltaY(bottomOffsetPx);
                if (deltaY != 0) v.offsetTopAndBottom(deltaY);
            };
            LAYOUT_OWNERS.put(view, listener);
            view.addOnLayoutChangeListener(listener);
        }
    }

    static boolean isLaptopDockHierarchy(View view) {
        ViewParent parent = view == null ? null : view.getParent();
        int depth = 0;
        while (parent != null && depth++ < 8) {
            if (DockBottomGeometryPolicy.isLaptopHierarchyClassName(
                    parent.getClass().getName())) return true;
            parent = parent.getParent();
        }
        return false;
    }
}
