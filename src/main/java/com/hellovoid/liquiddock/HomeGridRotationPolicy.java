package com.hellovoid.liquiddock;

/** Pure orientation and block-index policy for MIUI's transposed grid transform. */
final class HomeGridRotationPolicy {
    private HomeGridRotationPolicy() {}

    /**
     * LayoutTransformRule.init() allocates source occupancy as [mVCells][mHCells]
     * and destination occupancy as [mHCells][mVCells]. mH/mV therefore describe
     * the target grid while the source is their transpose. A taller target comes
     * from a wider horizontal source.
     */
    static boolean sourceUsesHorizontalCoordinates(int horizontalCells, int verticalCells) {
        return verticalCells > horizontalCells;
    }

    /**
     * MIUI copies its two fixed 4x2 SPECIAL_WIDGET slots at the same cell coordinates before
     * moving ordinary widget blocks. The first slot occupies block indices 0/1 in both source
     * and destination, but the second slot (x=0..3,y=2..3) has different row-major block
     * indices after a transpose. Map the remaining source blocks to the remaining destination
     * blocks by ordinal instead of using the stock hard-coded 2/4 substitutions.
     */
    static int mapOtherWidgetBlockIndex(int targetColumns, int targetRows,
                                        boolean firstSpecial, boolean secondSpecial,
                                        int sourceIndex) {
        int sourceColumns = targetRows;
        int sourceRows = targetColumns;
        int sourceBlockColumns = sourceColumns / 2;
        int sourceBlockRows = sourceRows / 2;
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

        int ordinal = -1;
        for (int i = 0; i <= sourceIndex; i++) {
            if (!sourceReserved[i]) ordinal++;
        }
        int seen = -1;
        for (int i = 0; i < total; i++) {
            if (targetReserved[i]) continue;
            seen++;
            if (seen == ordinal) return i;
        }
        return sourceIndex;
    }

    private static void reservePair(boolean[] reserved, int start) {
        if (start >= 0 && start < reserved.length) reserved[start] = true;
        if (start + 1 >= 0 && start + 1 < reserved.length) reserved[start + 1] = true;
    }
}
