package com.hellovoid.liquiddock;

/**
 * Pure normal-Workspace geometry policy shared by the 8x4 and 10x6 profiles.
 *
 * Edge offset belongs only to the horizontal axis. Margin is the actual gap between
 * adjacent cells on both axes. A zero/non-positive margin selects the device-derived
 * default of 0.9% of the current screen width.
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
        int requestedMargin = resolveMarginPx(width, configuredMarginPx);

        Axis horizontal = solveAxis(horizontalBudget, countX, requestedMargin);
        Axis vertical = solveAxis(verticalBudget, countY, requestedMargin);

        int horizontalRemainder = Math.max(0, horizontalBudget - horizontal.used);
        int verticalRemainder = Math.max(0, verticalBudget - vertical.used);
        int extraLeft = horizontalRemainder / 2;
        int extraRight = horizontalRemainder - extraLeft;
        int extraTop = verticalRemainder / 2;
        int extraBottom = verticalRemainder - extraTop;

        return new Result(
                leftInset + edge + extraLeft,
                topInset + extraTop,
                rightInset + edge + extraRight,
                bottomInset + extraBottom,
                horizontal.cell,
                vertical.cell,
                horizontal.gap,
                vertical.gap);
    }

    private static int clampInset(int value, int max) {
        return Math.max(0, Math.min(Math.max(0, max), value));
    }

    private static Axis solveAxis(int budget, int count, int requestedGap) {
        if (count <= 1) {
            return new Axis(Math.max(1, budget), 0, Math.max(1, budget));
        }
        int maxGap = Math.max(0, (budget - count) / (count - 1));
        int gap = Math.max(0, Math.min(requestedGap, maxGap));
        int cellsBudget = Math.max(count, budget - gap * (count - 1));
        int cell = Math.max(1, cellsBudget / count);
        int used = cell * count + gap * (count - 1);
        return new Axis(cell, gap, used);
    }

    private static final class Axis {
        final int cell;
        final int gap;
        final int used;

        Axis(int cell, int gap, int used) {
            this.cell = cell;
            this.gap = gap;
            this.used = used;
        }
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
