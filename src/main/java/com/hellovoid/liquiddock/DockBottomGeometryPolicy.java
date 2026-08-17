package com.hellovoid.liquiddock;

/** Pure math for ordinary Dock vertical placement without changing layout reserve. */
final class DockBottomGeometryPolicy {
    private DockBottomGeometryPolicy() {}

    /** Positive bottom offset means visually farther from the physical bottom, hence negative Y. */
    static float visualTranslationY(float vendorTranslationY, int bottomOffsetPx) {
        return vendorTranslationY - bottomOffsetPx;
    }

    /** Reconstruct DeviceConfig.getHotSeatsMarginBottom() from the launcher DEX. */
    static int stockMargin(int gridBottomPx, int mingouLaptopOffsetPx) {
        if (mingouLaptopOffsetPx <= 0) return Math.max(0, gridBottomPx);
        return Math.max(0, gridBottomPx - mingouLaptopOffsetPx);
    }
}
