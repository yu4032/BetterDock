package com.hellovoid.liquiddock;

import java.util.Locale;

/**
 * Supported home-grid shapes. This type owns only profile-derived dimensions; layout geometry
 * remains owned by {@link HomeGridHook}.
 */
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
        if (value == null) return GRID_8X4;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (HomeGridProfile profile : values()) {
            if (profile.persistedValue.equals(normalized)) return profile;
        }
        return GRID_8X4;
    }

    public String persistedValue() {
        return persistedValue;
    }

    int columns(boolean portrait) {
        return portrait ? landscapeRows : landscapeColumns;
    }

    int rows(boolean portrait) {
        return portrait ? landscapeColumns : landscapeRows;
    }

    boolean matchesCounts(int columns, int rows) {
        return (columns == landscapeColumns && rows == landscapeRows)
                || (columns == landscapeRows && rows == landscapeColumns);
    }

    int totalBlocks() {
        return (landscapeColumns / 2) * (landscapeRows / 2);
    }

    /** Row-major 2x2 block origins for the requested orientation. */
    int[][] blockOrigins(boolean portrait) {
        int columns = columns(portrait);
        int rows = rows(portrait);
        int[][] result = new int[(columns / 2) * (rows / 2)][2];
        int index = 0;
        for (int y = 0; y + 1 < rows; y += 2) {
            for (int x = 0; x + 1 < columns; x += 2) {
                result[index][0] = x;
                result[index][1] = y;
                index++;
            }
        }
        return result;
    }
}
