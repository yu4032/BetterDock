package com.hellovoid.liquiddock;

import android.content.Context;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

import io.github.libxposed.api.XposedInterface;

/**
 * Narrow profile-sized overlay for the optional 10x6 / 6x10 home grid.
 *
 * <p>{@link HomeGridHook} remains the single owner of CellLayout geometry, margins, row gaps,
 * widget frames, folder alignment, indicator placement, lazy-page preparation and workspace
 * refresh. This overlay changes only Pad compatibility counts, normal Workspace GridConfig
 * counts, and the profile-sized metadata used by MIUI's native rotation transform.</p>
 */
final class HomeGridProfileOverlayHook {
    private static final String PAD_CELL_COUNT =
            "com.miui.home.launcher.compat.LauncherCellCountCompatPadDevice";
    private static final String GRID_CONFIG =
            "com.miui.home.launcher.grid.GridConfig";
    private static final String ROTATION_RULE =
            "com.miui.home.launcher.compat.LayoutTransformRuleGridChanged";

    private HomeGridProfileOverlayHook() {}

    static void install(ClassLoader classLoader, boolean customGridEnabled,
                        HomeGridProfile selectedProfile) {
        if (!customGridEnabled || selectedProfile != HomeGridProfile.GRID_10X6) return;

        try {
            // Resolve the complete vendor contract before registering the first hook. We cannot
            // atomically roll back libxposed hook registration, so preflight is what makes an
            // unsupported Launcher build genuinely fail closed instead of leaving a partial grid.
            preflightPrivateApi(classLoader);

            Class<?> compat = Class.forName(PAD_CELL_COUNT, false, classLoader);
            hookAxis(compat, "getCellCountXMin", selectedProfile);
            hookAxis(compat, "getCellCountXDef", selectedProfile);
            hookAxis(compat, "getCellCountYMin", selectedProfile);
            hookAxis(compat, "getCellCountYDef", selectedProfile);

            Class<?> gridConfig = Class.forName(GRID_CONFIG, false, classLoader);
            hookGridCountSetter(gridConfig, "setCountX", true, selectedProfile);
            hookGridCountSetter(gridConfig, "setCountY", false, selectedProfile);
            hookGridCountGetter(gridConfig, "getCountX", true, selectedProfile);
            hookGridCountGetter(gridConfig, "getCountY", false, selectedProfile);

            installRotationTransform(classLoader, selectedProfile);
            MainHook.log("[DC] extended home grid overlay active profile="
                    + selectedProfile.persistedValue());
        } catch (Throwable error) {
            MainHook.log("[DC] 10x6 profile overlay unavailable: " + error);
        }
    }

    private static void preflightPrivateApi(ClassLoader classLoader) throws Exception {
        Class<?> compat = Class.forName(PAD_CELL_COUNT, false, classLoader);
        HookUtil.findMethodExact(compat, "getCellCountXMin", new Class<?>[]{Context.class});
        HookUtil.findMethodExact(compat, "getCellCountXDef", new Class<?>[]{Context.class});
        HookUtil.findMethodExact(compat, "getCellCountYMin", new Class<?>[]{Context.class});
        HookUtil.findMethodExact(compat, "getCellCountYDef", new Class<?>[]{Context.class});

        Class<?> gridConfig = Class.forName(GRID_CONFIG, false, classLoader);
        HookUtil.findMethodExact(gridConfig, "setCountX", new Class<?>[]{int.class});
        HookUtil.findMethodExact(gridConfig, "setCountY", new Class<?>[]{int.class});
        HookUtil.findMethodExact(gridConfig, "getCountX", new Class<?>[0]);
        HookUtil.findMethodExact(gridConfig, "getCountY", new Class<?>[0]);

        Class<?> rule = Class.forName(ROTATION_RULE, false, classLoader);
        Constructor<?>[] constructors = rule.getDeclaredConstructors();
        if (constructors.length == 0) {
            throw new NoSuchMethodException(rule.getName() + "#<init>");
        }
        HookUtil.findMethodExact(rule, "checkCellCount", new Class<?>[0]);
        HookUtil.findMethodExact(rule, "getMHCells", new Class<?>[0]);
        HookUtil.findMethodExact(rule, "getMVCells", new Class<?>[0]);
        findTwoArgMethod(rule, "get4x2WidgetCase");
        findTwoArgMethod(rule, "getDstBlockXY");
        requireField(rule, "vScreenCoordinate");
        requireField(rule, "hScreenCoordinate");
        requireField(rule, "totalBlocks");
        requireField(rule, "mIsVerticalCellCount");
    }

    private static void hookAxis(Class<?> compat, String methodName,
                                 HomeGridProfile profile) throws NoSuchMethodException {
        Method method = HookUtil.findMethodExact(
                compat, methodName, new Class<?>[]{Context.class});
        Api101Bridge.module().hook(method)
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    if (!(result instanceof Integer) || MainHook.isWorkstationMode()) return result;
                    return HomeGridCountPolicy.profileRewrite(profile, (Integer) result);
                });
    }

    private static void hookGridCountSetter(Class<?> gridConfig, String methodName,
                                            boolean xAxis, HomeGridProfile profile)
            throws NoSuchMethodException {
        Method method = HookUtil.findMethodExact(
                gridConfig, methodName, new Class<?>[]{int.class});
        Api101Bridge.module().hook(method)
                .setPriority(XposedInterface.PRIORITY_LOWEST)
                .intercept(chain -> {
                    if (MainHook.isWorkstationMode() || isExcludedGridConfigCall()) {
                        return chain.proceed();
                    }
                    Object value = chain.getArg(0);
                    if (!(value instanceof Integer)) return chain.proceed();

                    int current = (Integer) value;
                    int target = HomeGridCountPolicy.profileRewriteForGridName(
                            profile, gridName(chain.getThisObject()), xAxis, current);
                    if (target == current) return chain.proceed();

                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    args[0] = target;
                    return chain.proceed(args);
                });
    }

    private static void hookGridCountGetter(Class<?> gridConfig, String methodName,
                                            boolean xAxis, HomeGridProfile profile)
            throws NoSuchMethodException {
        Method method = HookUtil.findMethodExact(gridConfig, methodName, new Class<?>[0]);
        Api101Bridge.module().hook(method)
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    if (!(result instanceof Integer) || MainHook.isWorkstationMode()
                            || isExcludedGridConfigCall()) {
                        return result;
                    }
                    return HomeGridCountPolicy.profileRewriteForGridName(
                            profile, gridName(chain.getThisObject()), xAxis, (Integer) result);
                });
    }

    /**
     * Extend only the dimensions embedded in MIUI's native transpose transform. The source
     * occupancy remains MIUI-owned and is the transpose of the target mHCells/mVCells matrix.
     */
    private static void installRotationTransform(ClassLoader classLoader, HomeGridProfile profile)
            throws Exception {
        Class<?> rule = Class.forName(ROTATION_RULE, false, classLoader);

        for (Constructor<?> constructor : rule.getDeclaredConstructors()) {
            constructor.setAccessible(true);
            Api101Bridge.module().hook(constructor)
                    .setPriority(XposedInterface.PRIORITY_HIGHEST)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        if (MainHook.isWorkstationMode() || isExcludedGridConfigCall()) {
                            return result;
                        }

                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (args.length < 2 || !(args[0] instanceof Integer)
                                || !(args[1] instanceof Integer)) {
                            return result;
                        }

                        int horizontalCells = (Integer) args[0];
                        int verticalCells = (Integer) args[1];
                        if (!profile.matchesCounts(horizontalCells, verticalCells)) return result;

                        Object target = chain.getThisObject();
                        HookUtil.setField(target, "vScreenCoordinate", profile.blockOrigins(true));
                        HookUtil.setField(target, "hScreenCoordinate", profile.blockOrigins(false));
                        HookUtil.setIntField(target, "totalBlocks", profile.totalBlocks());
                        return result;
                    });
        }

        Method checkCellCount = HookUtil.findMethodExact(
                rule, "checkCellCount", new Class<?>[0]);
        Api101Bridge.module().hook(checkCellCount)
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .intercept(chain -> {
                    if (MainHook.isWorkstationMode() || isExcludedGridConfigCall()) {
                        return chain.proceed();
                    }

                    int[] counts = transformCounts(chain.getThisObject());
                    if (counts != null && profile.matchesCounts(counts[0], counts[1])) {
                        // checkCellCount is a validation guard. Exact supported profile counts are
                        // already valid, so bypass only that stock hard-coded size check.
                        return null;
                    }
                    return chain.proceed();
                });

        installRotationDirectionFix(rule, profile);
        installOtherWidgetBlockRemap(rule, profile);
    }

    /**
     * MIUI writes mIsVerticalCellCount from a stock constant before processing 4x2 widgets.
     * Correct it at the first native method that consumes that direction, without replacing the
     * transform algorithm itself.
     */
    private static void installRotationDirectionFix(Class<?> rule, HomeGridProfile profile)
            throws NoSuchMethodException {
        Method directionLatch = findTwoArgMethod(rule, "get4x2WidgetCase");
        Api101Bridge.module().hook(directionLatch)
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .intercept(chain -> {
                    if (!MainHook.isWorkstationMode() && !isExcludedGridConfigCall()) {
                        int[] counts = transformCounts(chain.getThisObject());
                        if (counts != null && profile.matchesCounts(counts[0], counts[1])) {
                            boolean sourceHorizontal =
                                    HomeGridRotationPolicy.sourceUsesHorizontalCoordinates(
                                            counts[0], counts[1]);
                            HookUtil.setField(
                                    chain.getThisObject(), "mIsVerticalCellCount", sourceHorizontal);
                        }
                    }
                    return chain.proceed();
                });
    }

    /**
     * The stock getDstBlockXY() embeds ordinary-block indices for the smaller stock grid. Keep
     * MIUI's special-widget copy and icon movement, but map remaining 2x2 blocks by free-block
     * ordinal around the native 4x2 reservations.
     */
    private static void installOtherWidgetBlockRemap(Class<?> rule, HomeGridProfile profile)
            throws NoSuchMethodException {
        Method mapper = findTwoArgMethod(rule, "getDstBlockXY");
        Api101Bridge.module().hook(mapper)
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .intercept(chain -> {
                    if (MainHook.isWorkstationMode() || isExcludedGridConfigCall()) {
                        return chain.proceed();
                    }

                    int[] counts = transformCounts(chain.getThisObject());
                    Object specialValue = chain.getArg(0);
                    Object sourceValue = chain.getArg(1);
                    if (counts == null || !profile.matchesCounts(counts[0], counts[1])
                            || !(specialValue instanceof boolean[])
                            || !(sourceValue instanceof Integer)) {
                        return chain.proceed();
                    }

                    boolean[] special = (boolean[]) specialValue;
                    boolean firstSpecial = special.length > 0 && special[0];
                    boolean secondSpecial = special.length > 1 && special[1];
                    int targetIndex = HomeGridRotationPolicy.mapOtherWidgetBlockIndex(
                            counts[0], counts[1], firstSpecial, secondSpecial,
                            (Integer) sourceValue);

                    int[][] targetBlocks = profile.blockOrigins(counts[1] > counts[0]);
                    if (targetIndex < 0 || targetIndex >= targetBlocks.length) {
                        return chain.proceed();
                    }
                    return targetBlocks[targetIndex];
                });
    }

    private static Method findTwoArgMethod(Class<?> owner, String name)
            throws NoSuchMethodException {
        for (Method method : owner.getDeclaredMethods()) {
            if (name.equals(method.getName()) && method.getParameterCount() == 2) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(owner.getName() + "#" + name);
    }

    private static Field requireField(Class<?> owner, String name) throws NoSuchFieldException {
        Class<?> current = owner;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(owner.getName() + "#" + name);
    }

    private static int[] transformCounts(Object transform) {
        Object horizontal = HookUtil.invoke(transform, "getMHCells");
        Object vertical = HookUtil.invoke(transform, "getMVCells");
        if (!(horizontal instanceof Integer) || !(vertical instanceof Integer)) return null;
        return new int[]{(Integer) horizontal, (Integer) vertical};
    }

    private static String gridName(Object gridConfig) {
        try {
            Object value = HookUtil.invoke(gridConfig, "getName");
            if (value instanceof String) return (String) value;
        } catch (Throwable ignored) {
            // Some launcher variants expose the semantic name as a field instead of a getter.
        }
        try {
            Object field = HookUtil.getField(gridConfig, "name");
            return field instanceof String ? (String) field : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Exclude vendor grids whose counts are not Workspace-page ownership. */
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
