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
    private static final String GRID_OCCUPANCY_CONTROLLER =
            "com.miui.home.GridOccupancyController";
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

            installDragRuleOwnership(classLoader);
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
     * MIUI Pad initializes GridOccupancyController with SwapPlaces rules. Those rules are tied to
     * the stock 4x6/6x4 topology: 4x2 widgets are legal only at y=0/2 and the swap engine carries
     * a four-cell bound plus six hard-coded 2x2 blocks. The occupancy matrix itself already loads
     * as 6x10/10x6; only the drag rules are stale.
     *
     * Reuse MIUI's own private rule factory instead of patching individual legality methods. On a
     * Pad initSqueezeAndDropRule() intentionally inverts its boolean; true therefore selects the
     * generic LayoutSqueezePlaces + LayoutDropRuleSqueezePlaces pair without changing
     * mIsNoVacantMode. The newly-created transform is then initialized with the counts that
     * loadGridConfig() just committed.
     */
    private static void installDragRuleOwnership(ClassLoader classLoader) throws Exception {
        Class<?> controller = Class.forName(GRID_OCCUPANCY_CONTROLLER, false, classLoader);
        Method loadGridConfig = null;
        for (Method candidate : controller.getDeclaredMethods()) {
            if ("loadGridConfig".equals(candidate.getName())
                    && candidate.getParameterCount() == 4) {
                loadGridConfig = candidate;
                break;
            }
        }
        if (loadGridConfig == null) {
            throw new NoSuchMethodException(controller.getName() + "#loadGridConfig");
        }
        Method initRules = HookUtil.findMethodExact(
                controller, "initSqueezeAndDropRule", new Class<?>[]{boolean.class});
        Method initTransform = HookUtil.findMethodExact(
                controller, "initLayoutSqueezeDataTransform", new Class<?>[0]);

        loadGridConfig.setAccessible(true);
        Api101Bridge.module().hook(loadGridConfig)
                .setPriority(XposedInterface.PRIORITY_LOWEST)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    if (MainHook.isWorkstationMode()) return result;
                    Object target = chain.getThisObject();
                    int h;
                    int v;
                    try {
                        h = HookUtil.getIntField(target, "mHCells");
                        v = HookUtil.getIntField(target, "mVCells");
                    } catch (Throwable error) {
                        return result;
                    }
                    if (!profile.matchesCounts(h, v)) return result;
                    try {
                        // Pad boolean inversion: true -> generic SqueezePlaces rules.
                        initRules.invoke(target, true);
                        initTransform.invoke(target);
                        MainHook.log("[DC] 10x6 drag rules=generic grid=" + h + "x" + v);
                    } catch (Throwable error) {
                        MainHook.log("[DC] 10x6 generic drag-rule init failed: " + error);
                    }
                    return result;
                });
    }

    /**
     * Extend only the profile-sized metadata of MIUI's native transpose transform.
     * LayoutTransformRule.init() creates source occupancy as [mVCells][mHCells] and
     * destination occupancy as [mHCells][mVCells]. Therefore mH/mV describe the target
     * grid while the source grid is their transpose. The native block and icon movers are
     * retained; only their 10x6 metadata and stock hard-coded orientation/block constants
     * are generalized.
     */
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
        installOtherWidgetBlockRemap(rule);
    }

    /**
     * Stock transformToDstLayout() writes mIsVerticalCellCount = (mHCells != 4).
     * mH/mV are target counts, so a target 6x10 comes from a horizontal 10x6 source
     * and must use hScreenCoordinate; a target 10x6 comes from vertical 6x10 and must
     * use vScreenCoordinate. get4x2WidgetCase() is the first native call after the
     * stock write, making it the narrowest safe re-latch point.
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
                                boolean sourceHorizontal =
                                        HomeGridRotationPolicy.sourceUsesHorizontalCoordinates(h, v);
                                HookUtil.setField(target, "mIsVerticalCellCount", sourceHorizontal);
                                MainHook.log("[DC] 10x6 rotation source="
                                        + (sourceHorizontal ? "horizontal" : "vertical")
                                        + " target=" + h + "x" + v);
                            }
                        }
                    }
                    return chain.proceed();
                });
    }

    /**
     * Native getDstBlockXY() contains literal block indices 4 and 2 for the second
     * 4x2 SPECIAL_WIDGET. Those constants are valid only for the stock six-block grid.
     * Keep native special-widget copying, switchBlock(), and switchIcons(), but map each
     * remaining source block to the same ordinal free destination block on the 15-block grid.
     */
    private static void installOtherWidgetBlockRemap(Class<?> rule) throws NoSuchMethodException {
        Method mapper = null;
        for (Method candidate : rule.getDeclaredMethods()) {
            if ("getDstBlockXY".equals(candidate.getName())
                    && candidate.getParameterCount() == 2) {
                mapper = candidate;
                break;
            }
        }
        if (mapper == null) {
            throw new NoSuchMethodException(rule.getName() + "#getDstBlockXY");
        }
        mapper.setAccessible(true);
        Api101Bridge.module().hook(mapper)
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .intercept(chain -> {
                    Object target = chain.getThisObject();
                    if (MainHook.isWorkstationMode()) return chain.proceed();
                    Object hValue = HookUtil.invoke(target, "getMHCells");
                    Object vValue = HookUtil.invoke(target, "getMVCells");
                    if (!(hValue instanceof Integer) || !(vValue instanceof Integer)
                            || !(chain.getArg(0) instanceof boolean[])
                            || !(chain.getArg(1) instanceof Integer)) {
                        return chain.proceed();
                    }
                    int h = (Integer) hValue;
                    int v = (Integer) vValue;
                    if (!profile.matchesCounts(h, v)) return chain.proceed();
                    boolean[] special = (boolean[]) chain.getArg(0);
                    int sourceIndex = (Integer) chain.getArg(1);
                    boolean firstSpecial = special.length > 0 && special[0];
                    boolean secondSpecial = special.length > 1 && special[1];
                    int targetIndex = HomeGridRotationPolicy.mapOtherWidgetBlockIndex(
                            h, v, firstSpecial, secondSpecial, sourceIndex);
                    int[][] targetBlocks = blocks(v > h);
                    if (targetIndex < 0 || targetIndex >= targetBlocks.length) {
                        return chain.proceed();
                    }
                    return targetBlocks[targetIndex];
                });
    }

    private static int[][] blocks(boolean portrait) {
        return profile.blockOrigins(portrait);
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
