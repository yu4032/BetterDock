package com.hellovoid.liquiddock;

/** Pure orientation policy for MIUI's block-based grid transform. */
final class HomeGridRotationPolicy {
    private HomeGridRotationPolicy() {}

    /**
     * MIUI names the runtime latch mIsVerticalCellCount, but in the stock 6x4/4x6
     * transform the value is true for the wider (landscape) source and false for
     * the taller (portrait) source. Generalize that relation instead of relying
     * on the launcher's hard-coded mHCells != 4 check.
     */
    static boolean sourceUsesHorizontalCoordinates(int horizontalCells, int verticalCells) {
        return horizontalCells > verticalCells;
    }
}
