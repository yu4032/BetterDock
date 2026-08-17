package com.hellovoid.liquiddock;

import java.util.Locale;

/** Resolves profile-sized Workspace counts without depending on rotation timing. */
final class HomeGridCountPolicy {
    private HomeGridCountPolicy() {}

    static int profileRewrite(HomeGridProfile profile, boolean ignoredPortrait,
                              boolean ignoredXAxis, int current) {
        if (profile != HomeGridProfile.GRID_10X6) return current;

        // Values emitted by the device-verified 8x4 compatibility layer are unambiguous.
        if (current == 4) return 6;
        if (current == 8) return 10;
        if (current == 6 || current == 10) return current;
        return current;
    }

    /**
     * GridConfig has a stable semantic owner name (land_grid / vertical_grid). Use that
     * instead of global Configuration, which can lead or lag the GridConfig being applied
     * during a physical rotation.
     */
    static int profileRewriteForGridName(HomeGridProfile profile, String gridName,
                                         boolean xAxis, int current) {
        if (profile != HomeGridProfile.GRID_10X6) return current;
        String name = gridName == null ? "" : gridName.toLowerCase(Locale.ROOT);
        if (name.contains("vertical_grid") || name.contains("portrait")) {
            return xAxis ? 6 : 10;
        }
        if (name.contains("land_grid") || name.contains("landscape")) {
            return xAxis ? 10 : 6;
        }
        return profileRewrite(profile, false, xAxis, current);
    }
}
