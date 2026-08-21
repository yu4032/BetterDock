package com.hellovoid.liquiddock;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * 10x6-only correction for the legacy HomeGridHook horizontal geometry.
 *
 * HomeGridHook historically derives a non-negative left baseline but allows the derived right
 * baseline to become negative when the old source cell size no longer fits a larger column count.
 * In 10x6 that makes an ostensibly symmetric horizontal-distance adjustment translate the whole
 * grid. Keep the already-resolved left edge as the user's requested symmetric margin and solve the
 * horizontal cell/gap geometry again from width - 2 * margin.
 */
final class HomeGridHorizontalCenteringHook {
    private HomeGridHorizontalCenteringHook() {}

    static void install(ClassLoader classLoader, boolean customGridEnabled,
                        HomeGridProfile selectedProfile) {
        if (!customGridEnabled || selectedProfile != HomeGridProfile.GRID_10X6) return;
        try {
            Class<?> cellLayout = Class.forName(
                    "com.miui.home.launcher.CellLayout", false, classLoader);

            Method calculate = HookUtil.findMethodExact(
                    cellLayout, "calculateXsAndYs", new Class<?>[0]);
            Api101Bridge.module().hook(calculate)
                    .setPriority(XposedInterface.PRIORITY_HIGHEST)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        centerHorizontalGeometry(chain.getThisObject(), selectedProfile);
                        return result;
                    });

            Method onLayout = HookUtil.findMethodExact(cellLayout, "onLayout",
                    new Class<?>[]{boolean.class, int.class, int.class, int.class, int.class});
            Api101Bridge.module().hook(onLayout)
                    .setPriority(XposedInterface.PRIORITY_LOWEST)
                    .intercept(chain -> {
                        centerHorizontalGeometry(chain.getThisObject(), selectedProfile);
                        return chain.proceed();
                    });

            MainHook.log("[DC][GRID10] symmetric horizontal geometry correction installed");
        } catch (Throwable error) {
            MainHook.log("[DC][GRID10] horizontal centering correction unavailable: " + error);
        }
    }

    private static void centerHorizontalGeometry(Object target, HomeGridProfile profile) {
        if (!(target instanceof android.view.View) || MainHook.isWorkstationMode()) return;
        try {
            android.view.View view = (android.view.View) target;
            int width = view.getWidth();
            if (width <= 0) return;

            int countX = HookUtil.getIntField(target, "mHCells");
            int countY = HookUtil.getIntField(target, "mVCells");
            if (!profile.matchesCounts(countX, countY)) return;

            int requestedLeft = HookUtil.getIntField(target, "mCellPaddingLeft");
            int sourceCell = HookUtil.getIntField(target, "mCellWidth");
            try {
                Object gridConfig = HookUtil.getField(target, "mGridConfig");
                int configuredCell = HookUtil.getIntField(gridConfig, "cellSize");
                if (configuredCell > 0) sourceCell = configuredCell;
            } catch (Throwable ignored) {}
            if (sourceCell <= 0) return;

            HomeGridHorizontalCenteringPolicy.Geometry geometry =
                    HomeGridHorizontalCenteringPolicy.resolve(
                            width, countX, sourceCell, requestedLeft);
            if (geometry.cellSize <= 0) return;

            int oldLeft = HookUtil.getIntField(target, "mCellPaddingLeft");
            int oldCell = HookUtil.getIntField(target, "mCellWidth");
            int oldGap = HookUtil.getIntField(target, "mWidthGap");
            if (oldLeft == geometry.left && oldCell == geometry.cellSize
                    && oldGap == geometry.gap) {
                return;
            }

            HookUtil.setIntField(target, "mCellPaddingLeft", geometry.left);
            HookUtil.setIntField(target, "mCellWidth", geometry.cellSize);
            HookUtil.setIntField(target, "mWidthGap", geometry.gap);
            rebuildXs(target, countX, geometry.left, geometry.cellSize, geometry.gap);
            MainHook.log("[DC][GRID10] centered horizontal geometry width=" + width
                    + " cells=" + countX + " left=" + geometry.left
                    + " right=" + geometry.right(width, countX)
                    + " cell=" + geometry.cellSize + " gap=" + geometry.gap);
        } catch (Throwable error) {
            MainHook.log("[DC][GRID10] horizontal centering correction failed: " + error);
        }
    }

    private static void rebuildXs(Object target, int countX, int left,
                                  int cellSize, int gap) throws Exception {
        int[] xs = new int[countX];
        for (int x = 0; x < countX; x++) {
            xs[x] = left + x * (cellSize + gap);
        }
        HookUtil.setField(target, "mXs", xs);
    }
}
