package com.hellovoid.liquiddock;

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
