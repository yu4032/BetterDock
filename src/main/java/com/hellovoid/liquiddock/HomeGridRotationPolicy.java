package com.hellovoid.liquiddock;

/** Pure orientation and block-index policy for MIUI's transposed grid transform. */
final class HomeGridRotationPolicy {
    private HomeGridRotationPolicy() {}

    static boolean sourceUsesHorizontalCoordinates(int horizontalCells, int verticalCells) {
        return verticalCells > horizontalCells;
    }

    static int mapOtherWidgetBlockIndex(int targetColumns, int targetRows,
                                        boolean firstSpecial, boolean secondSpecial,
                                        int sourceIndex) {
        if (targetColumns <= 0 || targetRows <= 0
                || (targetColumns & 1) != 0 || (targetRows & 1) != 0) {
            return sourceIndex;
        }

        int sourceBlockColumns = targetRows / 2;
        int sourceBlockRows = targetColumns / 2;
        int targetBlockColumns = targetColumns / 2;
        int targetBlockRows = targetRows / 2;
        int total = sourceBlockColumns * sourceBlockRows;
        if (total <= 0 || total != targetBlockColumns * targetBlockRows
                || sourceIndex < 0 || sourceIndex >= total) {
            return sourceIndex;
        }

        boolean[] sourceReserved = new boolean[total];
        boolean[] targetReserved = new boolean[total];
        if (firstSpecial) {
            reservePair(sourceReserved, 0);
            reservePair(targetReserved, 0);
        }
        if (secondSpecial) {
            reservePair(sourceReserved, sourceBlockColumns);
            reservePair(targetReserved, targetBlockColumns);
        }
        if (sourceReserved[sourceIndex]) return sourceIndex;

        int ordinal = 0;
        for (int i = 0; i < sourceIndex; i++) {
            if (!sourceReserved[i]) ordinal++;
        }

        int freeIndex = 0;
        for (int target = 0; target < total; target++) {
            if (targetReserved[target]) continue;
            if (freeIndex == ordinal) return target;
            freeIndex++;
        }
        return sourceIndex;
    }

    private static void reservePair(boolean[] reserved, int start) {
        if (start >= 0 && start < reserved.length) reserved[start] = true;
        if (start + 1 >= 0 && start + 1 < reserved.length) reserved[start + 1] = true;
    }
}
