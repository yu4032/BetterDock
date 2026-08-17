package com.hellovoid.liquiddock;

/** Pure orientation policy for MIUI's transposed grid transform. */
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
}
