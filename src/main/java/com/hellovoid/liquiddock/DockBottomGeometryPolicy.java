package com.hellovoid.liquiddock;

import java.util.Locale;

/** Pure math and classification for ordinary Dock visual placement. */
final class DockBottomGeometryPolicy {
    private DockBottomGeometryPolicy() {}

    /** Positive bottom offset means farther from the physical bottom, hence a negative layout Y delta. */
    static int layoutDeltaY(int bottomOffsetPx) {
        return -bottomOffsetPx;
    }

    /** Reconstruct DeviceConfig.getHotSeatsMarginBottom() from the launcher DEX. */
    static int stockMargin(int gridBottomPx, int mingouLaptopOffsetPx) {
        if (mingouLaptopOffsetPx <= 0) return Math.max(0, gridBottomPx);
        return Math.max(0, gridBottomPx - mingouLaptopOffsetPx);
    }

    /**
     * The ordinary Floating Dock itself lives under launcher.dock.DockContainerView, so a bare
     * DockContainerView match cannot identify workstation mode. Only explicit laptop namespaces
     * or LaptopDock owners are excluded from the ordinary Dock bottom-offset feature.
     */
    static boolean isLaptopHierarchyClassName(String className) {
        if (className == null) return false;
        String name = className.toLowerCase(Locale.ROOT);
        return name.contains(".laptop.") || name.contains("laptopdock");
    }
}
