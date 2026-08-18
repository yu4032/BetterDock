package com.hellovoid.liquiddock;

/** Pure coordinate and legality rules for dragging on profile-sized CellLayouts. */
final class HomeGridDragGeometryPolicy {
    private HomeGridDragGeometryPolicy() {}

    static int[] pointToCell(int x, int y,
                             int paddingLeft, int paddingTop,
                             int cellWidth, int cellHeight,
                             int widthGap, int heightGap,
                             int countX, int countY) {
        int pitchX = Math.max(1, cellWidth + widthGap);
        int pitchY = Math.max(1, cellHeight + heightGap);
        int cellX = (x - paddingLeft) / pitchX;
        int cellY = (y - paddingTop) / pitchY;
        cellX = clamp(cellX, 0, Math.max(0, countX - 1));
        cellY = clamp(cellY, 0, Math.max(0, countY - 1));
        return new int[]{cellX, cellY};
    }

    static int[] cellToPoint(int cellX, int cellY,
                             int paddingLeft, int paddingTop,
                             int cellWidth, int cellHeight,
                             int widthGap, int heightGap) {
        int pitchX = Math.max(1, cellWidth + widthGap);
        int pitchY = Math.max(1, cellHeight + heightGap);
        return new int[]{
                paddingLeft + cellX * pitchX,
                paddingTop + cellY * pitchY
        };
    }

    /**
     * Generalizes MIUI's swap-place rule without changing its 2-cell alignment policy.
     * The stock implementation additionally hard-codes spanX=4 to x=0 and y=0/2, which
     * is only valid on its original 4x6 grid. Bounds now come from the active CellLayout.
     */
    static boolean isSwapPlacementLegal(int cellX, int cellY, int spanX, int spanY,
                                        int countX, int countY) {
        if (spanX <= 0 || spanY <= 0 || cellX < 0 || cellY < 0) return false;
        if (cellX + spanX > countX || cellY + spanY > countY) return false;
        if (spanX > 1 && (cellX & 1) != 0) return false;
        if (spanY > 1 && (cellY & 1) != 0) return false;
        return true;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
