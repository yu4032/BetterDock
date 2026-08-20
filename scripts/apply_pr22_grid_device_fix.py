#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
HOME = ROOT / "src/main/java/com/hellovoid/liquiddock/HomeGridHook.java"
POLICY = ROOT / "src/main/java/com/hellovoid/liquiddock/HomeGridGeometryPolicy.java"
MARKER = "Launcher GridConfig owns the normal Workspace vertical reserve"

POLICY_SOURCE = r'''package com.hellovoid.liquiddock;

/**
 * Pure normal-Workspace geometry policy shared by the 8x4 and 10x6 profiles.
 *
 * Edge offset belongs to the horizontal budget. Margin is the actual gap between
 * adjacent cells on both axes. A zero/non-positive margin selects 0.9% of the
 * current screen width. Launcher Workspace cells stay square because MIUI exposes
 * one GridConfig cellSize and its icon/widget measurement chain relies on that
 * single-cell contract.
 */
final class HomeGridGeometryPolicy {
    private static final float AUTO_MARGIN_WIDTH_FRACTION = 0.009f;

    private HomeGridGeometryPolicy() {}

    static int resolveMarginPx(int screenWidthPx, int configuredMarginPx) {
        if (configuredMarginPx > 0) return configuredMarginPx;
        return Math.max(1, Math.round(Math.max(0, screenWidthPx) * AUTO_MARGIN_WIDTH_FRACTION));
    }

    static Result compute(int width, int height, int countX, int countY,
                          int safeLeft, int safeTop, int safeRight, int safeBottom,
                          int edgeOffsetPx, int configuredMarginPx) {
        if (width <= 0 || height <= 0 || countX <= 0 || countY <= 0) {
            return new Result(0, 0, 0, 0, 1, 1, 0, 0);
        }

        int leftInset = clampInset(safeLeft, width);
        int rightInset = clampInset(safeRight, Math.max(0, width - leftInset));
        int topInset = clampInset(safeTop, height);
        int bottomInset = clampInset(safeBottom, Math.max(0, height - topInset));
        int edge = Math.max(0, edgeOffsetPx);

        int horizontalBudget = Math.max(countX,
                width - leftInset - rightInset - Math.min(width, edge * 2));
        int verticalBudget = Math.max(countY, height - topInset - bottomInset);
        int requestedGap = resolveMarginPx(width, configuredMarginPx);
        int gap = resolveSharedGap(horizontalBudget, verticalBudget,
                countX, countY, requestedGap);

        int horizontalCell = cellForBudget(horizontalBudget, countX, gap);
        int verticalCell = cellForBudget(verticalBudget, countY, gap);
        int cell = Math.max(1, Math.min(horizontalCell, verticalCell));

        int horizontalUsed = cell * countX + gap * Math.max(0, countX - 1);
        int verticalUsed = cell * countY + gap * Math.max(0, countY - 1);
        int horizontalRemainder = Math.max(0, horizontalBudget - horizontalUsed);
        int verticalRemainder = Math.max(0, verticalBudget - verticalUsed);
        int extraLeft = horizontalRemainder / 2;
        int extraRight = horizontalRemainder - extraLeft;
        int extraTop = verticalRemainder / 2;
        int extraBottom = verticalRemainder - extraTop;

        return new Result(
                leftInset + edge + extraLeft,
                topInset + extraTop,
                rightInset + edge + extraRight,
                bottomInset + extraBottom,
                cell,
                cell,
                countX > 1 ? gap : 0,
                countY > 1 ? gap : 0);
    }

    private static int resolveSharedGap(int horizontalBudget, int verticalBudget,
                                        int countX, int countY, int requestedGap) {
        int maxHorizontal = maxGap(horizontalBudget, countX);
        int maxVertical = maxGap(verticalBudget, countY);
        int maxShared;
        if (countX <= 1) {
            maxShared = maxVertical;
        } else if (countY <= 1) {
            maxShared = maxHorizontal;
        } else {
            maxShared = Math.min(maxHorizontal, maxVertical);
        }
        return Math.max(0, Math.min(requestedGap, maxShared));
    }

    private static int maxGap(int budget, int count) {
        if (count <= 1) return Integer.MAX_VALUE;
        return Math.max(0, (budget - count) / (count - 1));
    }

    private static int cellForBudget(int budget, int count, int gap) {
        if (count <= 0) return 1;
        int gapBudget = gap * Math.max(0, count - 1);
        return Math.max(1, Math.max(count, budget - gapBudget) / count);
    }

    private static int clampInset(int value, int max) {
        return Math.max(0, Math.min(Math.max(0, max), value));
    }

    static final class Result {
        final int left;
        final int top;
        final int right;
        final int bottom;
        final int cellWidth;
        final int cellHeight;
        final int widthGap;
        final int heightGap;

        Result(int left, int top, int right, int bottom,
               int cellWidth, int cellHeight, int widthGap, int heightGap) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.cellWidth = cellWidth;
            this.cellHeight = cellHeight;
            this.widthGap = widthGap;
            this.heightGap = heightGap;
        }
    }
}
'''


def replace_regex(text, pattern, replacement, label):
    new, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"HomeGridHook.java: {label} matched {count} times")
    return new


def main():
    text = HOME.read_text()
    if MARKER in text:
        print("PR22 device geometry fix already applied")
        return

    normal_workspace = r'''            if (!workstation) {
                // Launcher GridConfig owns the normal Workspace vertical reserve. On the
                // target HyperOS 3 Pad, land_grid is top=92, indicator=81, bottom=45,
                // dock=230; omitting indicator/bottom lets cells invade the indicator/Dock
                // band. System insets remain a safety floor for launcher variants.
                int launcherTop = gridMetric(config, "getTop", baseTop);
                int indicatorBarHeight = gridMetric(config, "getIndicatorBarHeight", 0);
                int launcherBottom = gridMetric(config, "getBottom", 0);
                int dockBarHeight = gridMetric(config, "getDockBarHeight", 0);
                int launcherBottomReserve = safeAdd(
                        indicatorBarHeight, launcherBottom, dockBarHeight);
                int[] systemSafe = normalWorkspaceSystemInsets(layout);
                int safeTop = Math.max(systemSafe[1], launcherTop);
                int safeBottom = Math.max(systemSafe[3], launcherBottomReserve);

                int edgeOffset = Math.max(0,
                        portrait ? portraitLeft : landscapeLeft);
                int configuredMargin = portrait ? portraitRowGap : landscapeRowGap;
                int screenWidth = width;
                android.view.View root = layout.getRootView();
                if (root != null && root.getWidth() > 0) screenWidth = root.getWidth();
                int margin = configuredMargin > 0
                        ? configuredMargin
                        : HomeGridGeometryPolicy.resolveMarginPx(screenWidth, 0);
                HomeGridGeometryPolicy.Result geometry = HomeGridGeometryPolicy.compute(
                        width, height, countX, countY,
                        systemSafe[0], safeTop, systemSafe[2], safeBottom,
                        edgeOffset, margin);
                HookUtil.setIntField(cellLayout, "mCellPaddingLeft", geometry.left);
                HookUtil.setIntField(cellLayout, "mCellPaddingTop", geometry.top);
                HookUtil.setIntField(cellLayout, "mCellWidth", geometry.cellWidth);
                HookUtil.setIntField(cellLayout, "mCellHeight", geometry.cellHeight);
                HookUtil.setIntField(cellLayout, "mWidthGap", geometry.widthGap);
                HookUtil.setIntField(cellLayout, "mHeightGap", geometry.heightGap);
                return;
            }

            // Laptop All Apps'''
    text = replace_regex(
        text,
        r'            if \(!workstation\) \{.*?            \}\n\n            // Laptop All Apps',
        normal_workspace,
        "normal Workspace geometry block",
    )

    helpers = r'''    private static int gridMetric(Object config, String methodName, int fallback) {
        try {
            Object value = HookUtil.invoke(config, methodName);
            if (value instanceof Integer) return Math.max(0, (Integer) value);
        } catch (Throwable ignored) {}
        return Math.max(0, fallback);
    }

    private static int safeAdd(int... values) {
        long total = 0L;
        for (int value : values) total += Math.max(0, value);
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static int[] normalWorkspaceSystemInsets(android.view.View layout) {
        int left = 0;
        int top = 0;
        int right = 0;
        int bottom = 0;
        try {
            WindowInsets windowInsets = layout.getRootWindowInsets();
            android.view.View root = layout.getRootView();
            if (windowInsets == null || root == null) {
                return new int[]{left, top, right, bottom};
            }
            Insets statusBars = windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.statusBars());
            Insets systemBars = windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars());
            int[] layoutScreen = new int[2];
            int[] rootScreen = new int[2];
            layout.getLocationOnScreen(layoutScreen);
            root.getLocationOnScreen(rootScreen);

            left = Math.max(0,
                    rootScreen[0] + systemBars.left - layoutScreen[0]);
            right = Math.max(0,
                    layoutScreen[0] + layout.getWidth()
                            - (rootScreen[0] + root.getWidth() - systemBars.right));
            top = Math.max(0,
                    rootScreen[1] + statusBars.top - layoutScreen[1]);
            bottom = Math.max(0,
                    layoutScreen[1] + layout.getHeight()
                            - (rootScreen[1] + root.getHeight() - systemBars.bottom));
        } catch (Throwable e) {
            MainHook.log("[DC] workspace system inset resolve failed: " + e);
        }
        return new int[]{left, top, right, bottom};
    }

    private static boolean isLaptopAllApps'''
    text = replace_regex(
        text,
        r'    private static int\[\] normalWorkspaceSafeInsets\(.*?\n    private static boolean isLaptopAllApps',
        helpers,
        "normal Workspace inset helper",
    )

    old_refresh = '''        workspace.post(() -> refreshWorkspaceGrid(workspace));
        workspace.postDelayed(() -> refreshWorkspaceGrid(workspace), 180L);
        workspace.postDelayed(() -> refreshWorkspaceGrid(workspace), 500L);'''
    new_refresh = '''        workspace.post(() -> refreshWorkspaceGridIfReady(workspace));
        workspace.postDelayed(() -> refreshWorkspaceGridIfReady(workspace), 180L);
        workspace.postDelayed(() -> refreshWorkspaceGridIfReady(workspace), 500L);'''
    if text.count(old_refresh) != 1:
        raise SystemExit(
            f"HomeGridHook.java: unguarded page refresh matched {text.count(old_refresh)} times")
    text = text.replace(old_refresh, new_refresh, 1)

    refresh_guard_old = '''    private static void refreshWorkspaceGrid(android.view.View workspace) {
        try {
            java.util.ArrayList<android.view.View> pages = new java.util.ArrayList<>();'''
    refresh_guard_new = '''    private static void refreshWorkspaceGrid(android.view.View workspace) {
        int workspaceWidth = workspace.getWidth();
        int workspaceHeight = workspace.getHeight();
        if (workspaceWidth <= 0 || workspaceHeight <= 0
                || !sizeMatchesOrientation(workspace, workspaceWidth, workspaceHeight)) return;
        try {
            java.util.ArrayList<android.view.View> pages = new java.util.ArrayList<>();'''
    if text.count(refresh_guard_old) != 1:
        raise SystemExit("HomeGridHook.java: refresh guard anchor mismatch")
    text = text.replace(refresh_guard_old, refresh_guard_new, 1)

    page_guard_old = '''            for (android.view.View page : pages) {
                if (!sizeMatchesOrientation(page, page.getWidth(), page.getHeight())) continue;
                HookUtil.invoke(page, "calculateXsAndYs");'''
    page_guard_new = '''            for (android.view.View page : pages) {
                int pageWidth = page.getWidth();
                int pageHeight = page.getHeight();
                if (pageWidth <= 0 || pageHeight <= 0
                        || !sizeMatchesOrientation(page, pageWidth, pageHeight)) continue;
                HookUtil.invoke(page, "calculateXsAndYs");'''
    if text.count(page_guard_old) != 1:
        raise SystemExit("HomeGridHook.java: page size guard anchor mismatch")
    text = text.replace(page_guard_old, page_guard_new, 1)

    POLICY.write_text(POLICY_SOURCE)
    HOME.write_text(text)
    print("PR22 device-derived square-cell/reserve/refresh fix applied")


if __name__ == "__main__":
    main()
