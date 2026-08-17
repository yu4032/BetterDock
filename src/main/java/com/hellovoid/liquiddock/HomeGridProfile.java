package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.GridProfileConfig;

/** Geometry selected while the optional extended home-grid master switch is enabled. */
public enum HomeGridProfile {
    GRID_8X4("8x4", 8, 4),
    GRID_10X6("10x6", 10, 6);

    private final String persistedValue;
    private final int landscapeColumns;
    private final int landscapeRows;

    HomeGridProfile(String persistedValue, int landscapeColumns, int landscapeRows) {
        this.persistedValue = persistedValue;
        this.landscapeColumns = landscapeColumns;
        this.landscapeRows = landscapeRows;
    }

    public static HomeGridProfile fromPersisted(String value) {
        return "10x6".equalsIgnoreCase(value) ? GRID_10X6 : GRID_8X4;
    }

    public String persistedValue() {
        return persistedValue;
    }

    public int columns(boolean portrait) {
        return portrait ? landscapeRows : landscapeColumns;
    }

    public int rows(boolean portrait) {
        return portrait ? landscapeColumns : landscapeRows;
    }

    public static String normalizePersisted(String value) {
        return GridProfileConfig.normalizeProfile(value);
    }
}
