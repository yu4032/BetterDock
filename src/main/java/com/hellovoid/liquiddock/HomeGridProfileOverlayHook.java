package com.hellovoid.liquiddock;

import android.content.Context;

import com.hellovoid.liquiddock.config.GridProfileConfig;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;

import io.github.libxposed.api.XposedInterface;

/**
 * Narrow 10x6 overlay over the device-verified 8x4 HomeGridHook.
 *
 * The existing HomeGridHook continues to own CellLayout geometry, widget frames, margins,
 * folders, indicator positioning and rotation refresh. This class overrides only values that
 * are inherently profile-sized: Pad axis counts, normal GridConfig counts and rotation-rule
 * metadata. It is inactive for 8x4 and while the workstation/laptop surface owns the UI.
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
            // The stable 8x4 core remains installed underneath. Fail closed instead of
            // applying a partial 10x6 profile if one Launcher hook point is unavailable.
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
                    // The default-priority 8x4 core runs inside this highest-priority wrapper.
                    // Rewrite the observed 4/8 result, never a potentially stale Configuration.
                    return HomeGridCountPolicy.profileRewrite(
                            profile, false, xAxis, (Integer) result);
                });
    }

    private static void hookGridCountSetter(Class<?> gridConfig, String methodName,
                                            boolean xAxis) throws NoSuchMethodException {
        Method method = HookUtil.findMethodExact(gridConfig, methodName,
                new Class<?>[]{int.class});
        // The stable HomeGridHook setter has default priority and rewrites any stock 6 to 8.
        // Run after it, then map the actually observed short/long axis 4/8 to 6/10. Rotation
        // Configuration is deliberately not consulted because it can lead GridConfig.
        Api101Bridge.module().hook(method)
                .setPriority(XposedInterface.PRIORITY_LOWEST)
                .intercept(chain -> {
                    if (MainHook.isWorkstationMode() || isExcludedGridConfigCall()) {
                        return chain.proceed();
                    }
                    int current = (Integer) chain.getArg(0);
                    int target = HomeGridCountPolicy.profileRewrite(
                            profile, false, xAxis, current);
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
                    return HomeGridCountPolicy.profileRewrite(
                            profile, false, xAxis, (Integer) result);
                });
    }

    private static void installRotationTransform(ClassLoader classLoader) throws Exception {
        Class<?> rule = Class.forName(ROTATION_RULE, false, classLoader);
        for (Constructor<?> ctor : rule.getDeclaredConstructors()) {
            ctor.setAccessible(true);
            Api101Bridge.module().hook(ctor)
                    .setPriority(XposedInterface.PRIORITY_HIGHEST)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        if (MainHook.isWorkstationMode() || isExcludedGridConfigCall()) return result;
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (args.length < 2 || !(args[0] instanceof Integer)
                                || !(args[1] instanceof Integer)) return result;
                        int h = (Integer) args[0];
                        int v = (Integer) args[1];
                        if (!profile.matchesCounts(h, v)) return result;
                        Object target = chain.getThisObject();
                        HookUtil.setField(target, "vScreenCoordinate", blocks(true));
                        HookUtil.setField(target, "hScreenCoordinate", blocks(false));
                        HookUtil.setIntField(target, "totalBlocks", profile.totalBlocks());
                        return result;
                    });
        }

        Method check = HookUtil.findMethodExact(rule, "checkCellCount", new Class<?>[0]);
        Api101Bridge.module().hook(check)
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .intercept(chain -> {
                    if (MainHook.isWorkstationMode() || isExcludedGridConfigCall()) {
                        return chain.proceed();
                    }
                    Object target = chain.getThisObject();
                    Object hValue = HookUtil.invoke(target, "getMHCells");
                    Object vValue = HookUtil.invoke(target, "getMVCells");
                    if (hValue instanceof Integer && vValue instanceof Integer) {
                        int h = (Integer) hValue;
                        int v = (Integer) vValue;
                        if (profile.matchesCounts(h, v)) return null;
                    }
                    return chain.proceed();
                });

        installRotationDirectionFix(rule);
    }

    /**
     * MIUI's stock 6x4/4x6 transform writes
     * mIsVerticalCellCount = (getMHCells() != 4) inside transformToDstLayout().
     * That accidentally still distinguishes 8x4 from 4x8, but 10x6 and 6x10 both
     * evaluate true. get4x2WidgetCase() is the first call immediately after that write,
     * so this hook is the narrowest point where the native transform direction can be
     * corrected before any block or icon movement occurs.
     */
    private static void installRotationDirectionFix(Class<?> rule) throws NoSuchMethodException {
        Method directionLatch = null;
        for (Method candidate : rule.getDeclaredMethods()) {
            if ("get4x2WidgetCase".equals(candidate.getName())
                    && candidate.getParameterCount() == 2) {
                directionLatch = candidate;
                break;
            }
        }
        if (directionLatch == null) {
            throw new NoSuchMethodException(rule.getName() + "#get4x2WidgetCase");
        }
        directionLatch.setAccessible(true);
        Api101Bridge.module().hook(directionLatch)
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .intercept(chain -> {
                    Object target = chain.getThisObject();
                    if (!MainHook.isWorkstationMode()) {
                        Object hValue = HookUtil.invoke(target, "getMHCells");
                        Object vValue = HookUtil.invoke(target, "getMVCells");
                        if (hValue instanceof Integer && vValue instanceof Integer) {
                            int h = (Integer) hValue;
                            int v = (Integer) vValue;
                            if (profile.matchesCounts(h, v)) {
                                boolean horizontal =
                                        HomeGridRotationPolicy.sourceUsesHorizontalCoordinates(h, v);
                                HookUtil.setField(target, "mIsVerticalCellCount", horizontal);
                                MainHook.log("[DC] 10x6 rotation direction source="
                                        + (horizontal ? "horizontal" : "vertical")
                                        + " h=" + h + " v=" + v);
                            }
                        }
                    }
                    return chain.proceed();
                });
    }

    private static int[][] blocks(boolean portrait) {
        return profile.blockOrigins(portrait);
    }

    /**
     * GridConfig is also used by folders, All Apps, the laptop launcher and HotSeats.
     * These are explicit non-Workspace owners and must never inherit the normal 10x6 grid.
     * Unknown normal-launcher callers are left eligible because the stock GridConfig can be
     * created before a Workspace View exists; the old-value guard above is the second gate.
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
