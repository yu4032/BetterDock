package com.hellovoid.liquiddock;

/** Pure geometry for the normal Workspace custom grid. X and Y are intentionally independent. */
final class HomeGridGeometryPolicy {
    static final int LEFT = 0;
    static final int RIGHT = 1;
    static final int TOP = 2;
    static final int BOTTOM = 3;
    static final int CELL_WIDTH = 4;
    static final int CELL_HEIGHT = 5;
    static final int WIDTH_GAP = 6;
    static final int HEIGHT_GAP = 7;

    private HomeGridGeometryPolicy() {}

    /**
     * Returns {left, right, top, bottom, cellWidth, cellHeight, widthGap, heightGap}.
     *
     * The normal Workspace is centered in the real area above the Dock before user offsets
     * are applied. Horizontal offsets are allowed to change only horizontal geometry; vertical
     * offsets/row-gap changes are allowed to change only vertical geometry. This deliberately
     * does not force square cells: CellLayout already owns independent mCellWidth/mCellHeight,
     * and coupling them is what made a horizontal offset move icon rows on dense profiles.
     */
    static int[] normalWorkspace(
            int width, int height, int dockBarHeight,
            int countX, int countY, int baseCell, int baseHeightGap,
            int leftOffset, int rightOffset, int topOffset, int bottomOffset,
            int rowGapOffset) {
        if (width <= 0 || height <= 0 || countX <= 0 || countY <= 0 || baseCell <= 0) {
            return new int[]{0, 0, 0, 0, 1, 1, 0, 0};
        }

        int dock = clamp(dockBarHeight, 0, height);

        // X baseline: fit the selected column count to the actual width first, then center.
        int baseCellWidth = Math.min(baseCell, Math.max(1, width / countX));
        int baseGridWidth = baseCellWidth * countX;
        int horizontalRemainder = Math.max(0, width - baseGridWidth);
        int baseLeft = horizontalRemainder / 2;
        int baseRight = horizontalRemainder - baseLeft;

        // Y baseline: only the real space above Dock is available. Never manufacture a
        // content height from baseCell*rows; that was the source of the 10x6 placement drift.
        int contentHeight = Math.max(countY, height - dock);
        int nativeGap = Math.max(1, baseHeightGap);
        int nativeGapTotal = nativeGap * Math.max(0, countY - 1);
        int baseCellHeight = Math.min(baseCell,
                Math.max(1, (contentHeight - nativeGapTotal) / countY));
        int baseGridHeight = baseCellHeight * countY + nativeGapTotal;
        int verticalRemainder = Math.max(0, contentHeight - baseGridHeight);
        int baseTop = verticalRemainder / 2;
        int baseBottom = dock + (verticalRemainder - baseTop);

        int left = baseLeft + leftOffset;
        int right = baseRight + rightOffset;
        int top = baseTop + topOffset;
        int bottom = baseBottom + bottomOffset;

        int availableWidth = Math.max(countX, width - left - right);
        int cellWidth = Math.min(baseCell, Math.max(1, availableWidth / countX));
        int widthGap = countX > 1
                ? Math.max(0, availableWidth - cellWidth * countX) / (countX - 1)
                : 0;

        int heightGap = nativeGap + rowGapOffset;
        int innerHeight = Math.max(countY, height - top - bottom);
        int cellHeight = Math.min(baseCell, Math.max(1,
                (innerHeight - heightGap * Math.max(0, countY - 1)) / countY));

        return new int[]{left, right, top, bottom,
                cellWidth, cellHeight, widthGap, heightGap};
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
