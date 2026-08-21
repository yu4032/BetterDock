package com.hellovoid.liquiddock;

import android.content.res.Configuration;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/** Fits the 10x6 profile's ten portrait rows inside the live CellLayout bounds. */
final class HomeGridVerticalBoundsHook {
    private HomeGridVerticalBoundsHook() {}

    static void install(ClassLoader classLoader, boolean customGridEnabled,
                        HomeGridProfile selectedProfile, LiquidDockConfig.Grid grid) {
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
                        fitPortraitVerticalGeometry(chain.getThisObject(), selectedProfile, grid);
                        return result;
                    });

            Method onLayout = HookUtil.findMethodExact(cellLayout, "onLayout",
                    new Class<?>[]{boolean.class, int.class, int.class, int.class, int.class});
            Api101Bridge.module().hook(onLayout)
                    .setPriority(XposedInterface.PRIORITY_LOWEST)
                    .intercept(chain -> {
                        fitPortraitVerticalGeometry(chain.getThisObject(), selectedProfile, grid);
                        return chain.proceed();
                    });
        } catch (Throwable error) {
            MainHook.log("[DC] 10x6 portrait geometry unavailable: " + error);
        }
    }

    private static void fitPortraitVerticalGeometry(Object target, HomeGridProfile profile,
                                                     LiquidDockConfig.Grid grid) {
        if (!(target instanceof android.view.View) || MainHook.isWorkstationMode()) return;
        try {
            android.view.View view = (android.view.View) target;
            boolean portrait = view.getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_PORTRAIT;
            if (!portrait) return;

            int height = view.getHeight();
            if (height <= 0) return;
            int countX = HookUtil.getIntField(target, "mHCells");
            int countY = HookUtil.getIntField(target, "mVCells");
            if (!profile.matchesCounts(countX, countY) || countY != profile.rows(true)) return;

            int currentCell = HookUtil.getIntField(target, "mCellHeight");
            int currentGap = HookUtil.getIntField(target, "mHeightGap");
            if (currentCell <= 0) return;

            int dockBarHeight = 0;
            try {
                Object gridConfig = HookUtil.getField(target, "mGridConfig");
                Object value = HookUtil.invoke(gridConfig, "getDockBarHeight");
                if (value instanceof Integer) dockBarHeight = Math.max(0, (Integer) value);
            } catch (Throwable ignored) {}

            float scale = grid.dp ? view.getResources().getDisplayMetrics().density : 1f;
            HomeGridVerticalBoundsPolicy.Geometry geometry = HomeGridVerticalBoundsPolicy.resolve(
                    height, countY, currentCell, currentGap, dockBarHeight,
                    Math.round(grid.portraitTop * scale),
                    Math.round(grid.portraitBottom * scale));
            if (geometry.cellSize <= 0) return;

            int oldTop = HookUtil.getIntField(target, "mCellPaddingTop");
            int oldCell = HookUtil.getIntField(target, "mCellHeight");
            int oldGap = HookUtil.getIntField(target, "mHeightGap");
            int oldLastBottom = oldTop + oldCell * countY
                    + oldGap * Math.max(0, countY - 1);
            if (oldTop == geometry.top && oldCell == geometry.cellSize
                    && oldGap == geometry.gap && oldLastBottom <= height) {
                return;
            }

            HookUtil.setIntField(target, "mCellPaddingTop", geometry.top);
            HookUtil.setIntField(target, "mCellHeight", geometry.cellSize);
            HookUtil.setIntField(target, "mHeightGap", geometry.gap);
            rebuildYs(target, countY, geometry.top, geometry.cellSize, geometry.gap);
        } catch (Throwable error) {
            MainHook.log("[DC] 10x6 portrait geometry failed: " + error);
        }
    }

    private static void rebuildYs(Object target, int countY, int top,
                                  int cellSize, int gap) throws Exception {
        int[] ys = new int[countY];
        for (int y = 0; y < countY; y++) {
            ys[y] = top + y * (cellSize + gap);
        }
        HookUtil.setField(target, "mYs", ys);
    }
}
