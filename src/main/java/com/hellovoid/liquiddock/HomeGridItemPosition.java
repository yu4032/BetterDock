package com.hellovoid.liquiddock;

/** Immutable logical placement of one workspace item. */
final class HomeGridItemPosition {
    private final long itemId;
    private final long screenId;
    private final int cellX;
    private final int cellY;
    private final int spanX;
    private final int spanY;

    HomeGridItemPosition(long itemId, long screenId,
                         int cellX, int cellY, int spanX, int spanY) {
        this.itemId = itemId;
        this.screenId = screenId;
        this.cellX = cellX;
        this.cellY = cellY;
        this.spanX = spanX;
        this.spanY = spanY;
    }

    long itemId() { return itemId; }
    long screenId() { return screenId; }
    int cellX() { return cellX; }
    int cellY() { return cellY; }
    int spanX() { return spanX; }
    int spanY() { return spanY; }

    boolean fitsWithin(int columns, int rows) {
        return columns > 0 && rows > 0
                && spanX > 0 && spanY > 0
                && cellX >= 0 && cellY >= 0
                && cellX <= columns - spanX
                && cellY <= rows - spanY;
    }

    boolean overlaps(HomeGridItemPosition other) {
        if (other == null || screenId != other.screenId) return false;
        long right = (long) cellX + spanX;
        long bottom = (long) cellY + spanY;
        long otherRight = (long) other.cellX + other.spanX;
        long otherBottom = (long) other.cellY + other.spanY;
        return cellX < otherRight && other.cellX < right
                && cellY < otherBottom && other.cellY < bottom;
    }
}
