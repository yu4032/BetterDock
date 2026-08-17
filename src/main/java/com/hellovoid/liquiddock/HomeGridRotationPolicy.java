package com.hellovoid.liquiddock;

/** Pure mapping policy for profile-sized grid transforms. */
final class HomeGridRotationPolicy {
    private HomeGridRotationPolicy() {}

    /**
     * LayoutTransformRule.init() allocates the source as [mVCells][mHCells] and
     * the destination as [mHCells][mVCells]. Therefore mH/mV describe the
     * destination axes while the source is their transpose. A taller destination
     * (mV > mH) is transforming from a wider, horizontal source.
     */
    static boolean sourceUsesHorizontalCoordinates(int horizontalCells, int verticalCells) {
        return verticalCells > horizontalCells;
    }

    /**
     * Preserve a 1x1 icon's absolute row-major cell index across transposed grids.
     * Because 10x6 and 6x10 contain the same number of cells, this mapping is a
     * bijection and a round trip returns every icon to its original cell.
     */
    static int[] mapIconCell(int x, int y,
                             int srcCols, int srcRows,
                             int dstCols, int dstRows) {
        if (srcCols <= 0 || srcRows <= 0 || dstCols <= 0 || dstRows <= 0
                || srcCols * srcRows != dstCols * dstRows
                || x < 0 || y < 0 || x >= srcCols || y >= srcRows) {
            throw new IllegalArgumentException("invalid grid mapping");
        }
        int index = y * srcCols + x;
        return new int[]{index % dstCols, index / dstCols};
    }

    /** Preserve a widget's relative anchor while keeping its span inside the target. */
    static int[] mapWidgetAnchor(int x, int y, int spanX, int spanY,
                                 int srcCols, int srcRows,
                                 int dstCols, int dstRows) {
        int srcMaxX = Math.max(0, srcCols - spanX);
        int srcMaxY = Math.max(0, srcRows - spanY);
        int dstMaxX = Math.max(0, dstCols - spanX);
        int dstMaxY = Math.max(0, dstRows - spanY);
        int mappedX = srcMaxX == 0 ? 0
                : Math.round((float) clamp(x, 0, srcMaxX) * dstMaxX / srcMaxX);
        int mappedY = srcMaxY == 0 ? 0
                : Math.round((float) clamp(y, 0, srcMaxY) * dstMaxY / srcMaxY);
        return new int[]{mappedX, mappedY};
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
