package com.hellovoid.liquiddock;

import android.content.Context;

import com.hellovoid.liquiddock.config.GridProfileConfig;

import java.lang.reflect.Method;
import java.util.Locale;

import io.github.libxposed.api.XposedInterface;

/**
 * Narrow 10x6 overlay over the device-verified 8x4 HomeGridHook.
 *
 * The existing HomeGridHook continues to own CellLayout geometry, widget frames, margins,
 * folders, indicator positioning and rotation refresh. This class overrides only values that
 * are inherently profile-sized: Pad axis counts, normal GridConfig counts and the 10x6
 * orientation transform. It is inactive for 8x4 and workstation/laptop-owned surfaces.
 */
final class HomeGridProfileOverlayHook {
    private static final String PAD_CELL_COUNT =
            "com.miui.home.launcher.compat.LauncherCellCountCompatPadDevice";
    private static final String GRID_CONFIG =
            "com.miui.home.launcher.grid.GridConfig";
    private static final String ROTATION_RULE =
            "com.miui.home.launcher.compat.LayoutTransformRuleGridChanged";

    private static HomeGridProfile profile = HomeGridProfile.GRID_8X4;

    private HomeGridProfileOverlayHook() {}

    static void install(ClassLoader classLoader) {
        ConfigReader reader = ConfigReader.load();
        boolean enabled = reader.has(GridProfileConfig.ENABLED_KEY)
                ? reader.b(GridProfileConfig.ENABLED_KEY, GridProfileConfig.DEFAULT_ENABLED)
                : reader.b(GridProfileConfig.LEGACY_8X4_KEY, GridProfileConfig.DEFAULT_ENABLED);
        profile = HomeGridProfile.fromPersisted(reader.s(
                GridProfileConfig.PROFILE_KEY, GridProfileConfig.DEFAULT_PROFILE));
        if (!enabled || profile != HomeGridProfile.GRID_10X6) return;

        try {
            Class<?> compat = Class.forName(PAD_CELL_COUNT, false, classLoader);
            hookAxis(compat, "getCellCountXMin", true);
            hookAxis(compat, "getCellCountXDef", true);
            hookAxis(compat, "getCellCountYMin", false);
            hookAxis(compat, "getCellCountYDef", false);

            Class<?> gridConfig = Class.forName(GRID_CONFIG, false, classLoader);
            hookGridCountSetter(gridConfig, "setCountX", true);
            hookGridCountSetter(gridConfig, "setCountY", false);
            hookGridCountGetter(gridConfig, "getCountX", true);
            hookGridCountGetter(gridConfig, "getCountY", false);

            installRotationTransform(classLoader);
            MainHook.log("[DC] extended home grid overlay active profile="
                    + profile.persistedValue());
        } catch (Throwable error) {
            MainHook.log("[DC] 10x6 profile overlay unavailable: " + error);
        }
    }

    private static void hookAxis(Class<?> compat, String methodName, boolean xAxis)
            throws NoSuchMethodException {
        Method method = HookUtil.findMethodExact(compat, methodName,
                new Class<?>[]{Context.class});
        Api101Bridge.module().hook(method)
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    if (!(result instanceof Integer) || MainHook.isWorkstationMode()) return result;
                    return HomeGridCountPolicy.profileRewrite(
                            profile, false, xAxis, (Integer) result);
                });
    }

    private static void hookGridCountSetter(Class<?> gridConfig, String methodName,
                                            boolean xAxis) throws NoSuchMethodException {
        Method method = HookUtil.findMethodExact(gridConfig, methodName,
                new Class<?>[]{int.class});
        // Lowest priority observes the stable 8x4 core first, but the semantic GridConfig name
        // is the final owner. land_grid and vertical_grid cannot be confused by stale global
        // Configuration during rotation.
        Api101Bridge.module().hook(method)
                .setPriority(XposedInterface.PRIORITY_LOWEST)
                .intercept(chain -> {
                    if (MainHook.isWorkstationMode() || isExcludedGridConfigCall()) {
                        return chain.proceed();
                    }
                    int current = (Integer) chain.getArg(0);
                    int target = HomeGridCountPolicy.profileRewriteForGridName(
                            profile, gridName(chain.getThisObject()), xAxis, current);
                    if (target == current) return chain.proceed();
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    args[0] = target;
                    return chain.proceed(args);
                });
    }

    private static void hookGridCountGetter(Class<?> gridConfig, String methodName,
                                            boolean xAxis) throws NoSuchMethodException {
        Method method = HookUtil.findMethodExact(gridConfig, methodName, new Class<?>[0]);
        Api101Bridge.module().hook(method)
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    if (!(result instanceof Integer) || MainHook.isWorkstationMode()
                            || isExcludedGridConfigCall()) return result;
                    return HomeGridCountPolicy.profileRewriteForGridName(
                            profile, gridName(chain.getThisObject()), xAxis, (Integer) result);
                });
    }

    /**
     * The stock rule is not a generic block transform: widgetCaseInBlock() still assumes
     * stock 6x4/4x6 matrix bounds. The captured crash is length=6,index=6 inside that method.
     * For 10x6 only, bypass transformToDstLayout() completely and transform the rule's
     * already-created source/destination occupancy matrices with our generic engine.
     */
    private static void installRotationTransform(ClassLoader classLoader) throws Exception {
        Class<?> rule = Class.forName(ROTATION_RULE, false, classLoader);
        Method transform = HookUtil.findMethodExact(
                rule, "transformToDstLayout", new Class<?>[0]);
        Api101Bridge.module().hook(transform)
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .intercept(chain -> {
                    if (MainHook.isWorkstationMode() || isExcludedGridConfigCall()) {
                        return chain.proceed();
                    }
                    Object target = chain.getThisObject();
                    Object hValue = HookUtil.invoke(target, "getMHCells");
                    Object vValue = HookUtil.invoke(target, "getMVCells");
                    if (!(hValue instanceof Integer) || !(vValue instanceof Integer)) {
                        return chain.proceed();
                    }
                    int h = (Integer) hValue;
                    int v = (Integer) vValue;
                    if (!profile.matchesCounts(h, v)) return chain.proceed();

                    boolean transformed = HomeGridTransformEngine.transform(target);
                    if (!transformed) {
                        // Fail closed: keeping the launcher alive is preferable to entering the
                        // known stock widgetCaseInBlock() length-6 crash path.
                        MainHook.log("[DC] 10x6 rotation transform failed closed h="
                                + h + " v=" + v);
                    }
                    return target;
                });
    }

    private static String gridName(Object gridConfig) {
        Object value = HookUtil.invoke(gridConfig, "getName");
        if (value instanceof String) return (String) value;
        try {
            Object field = HookUtil.getField(gridConfig, "name");
            return field instanceof String ? (String) field : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * GridConfig is also used by folders, All Apps, the laptop launcher and HotSeats.
     * These are explicit non-Workspace owners and must never inherit the normal 10x6 grid.
     */
    private static boolean isExcludedGridConfigCall() {
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
