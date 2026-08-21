package com.hellovoid.liquiddock;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/** Keeps the 10x6 horizontal grid centered when the legacy source cell size no longer fits. */
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
        } catch (Throwable error) {
            MainHook.log("[DC] 10x6 horizontal centering unavailable: " + error);
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
        } catch (Throwable error) {
            MainHook.log("[DC] 10x6 horizontal centering failed: " + error);
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
