package com.hellovoid.liquiddock;

/** Pure horizontal geometry for keeping profile-sized edge margins symmetric. */
final class HomeGridHorizontalCenteringPolicy {
    private HomeGridHorizontalCenteringPolicy() {}

    static Geometry resolve(int width, int countX, int sourceCellSize, int requestedLeft) {
        if (width <= 0 || countX <= 0) {
            return new Geometry(0, 0, 0, 0);
        }
        int sourceCell = Math.max(1, sourceCellSize);
        long availableLong = (long) width - 2L * requestedLeft;
        int available = (int) Math.max(countX, Math.min(Integer.MAX_VALUE, availableLong));
        int cellSize = Math.min(sourceCell, Math.max(1, available / countX));
        int gap = countX > 1
                ? Math.max(0, (available - cellSize * countX) / (countX - 1))
                : 0;
        int used = cellSize * countX + gap * Math.max(0, countX - 1);
        int remainder = Math.max(0, available - used);
        int left = requestedLeft + remainder / 2;
        return new Geometry(left, cellSize, gap, available);
    }

    static final class Geometry {
        final int left;
        final int cellSize;
        final int gap;
        final int available;

        Geometry(int left, int cellSize, int gap, int available) {
            this.left = left;
            this.cellSize = cellSize;
            this.gap = gap;
            this.available = available;
        }

        int right(int width, int countX) {
            int used = cellSize * countX + gap * Math.max(0, countX - 1);
            return width - left - used;
        }
    }
}
