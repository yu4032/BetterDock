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

    public int totalBlocks() {
        return (landscapeColumns / 2) * (landscapeRows / 2);
    }

    public boolean matchesCounts(int horizontalCells, int verticalCells) {
        return (horizontalCells == landscapeColumns && verticalCells == landscapeRows)
                || (horizontalCells == landscapeRows && verticalCells == landscapeColumns);
    }

    /** Row-major 2x2 origins, preserving the exact historical 8x4 ordering. */
    public int[][] blockOrigins(boolean portrait) {
        int columns = columns(portrait);
        int rows = rows(portrait);
        int[][] origins = new int[(columns / 2) * (rows / 2)][2];
        int index = 0;
        for (int y = 0; y < rows; y += 2) {
            for (int x = 0; x < columns; x += 2) {
                origins[index][0] = x;
                origins[index][1] = y;
                index++;
            }
        }
        return origins;
    }

    public static String normalizePersisted(String value) {
        return GridProfileConfig.normalizeProfile(value);
    }
}
