package com.hellovoid.liquiddock;

import java.util.Locale;

/** Pure count ownership for profile-sized Workspace grids. */
final class HomeGridCountPolicy {
    private HomeGridCountPolicy() {}

    /**
     * Convert only the device-verified 8x4 compatibility intermediates. Unknown counts are left
     * untouched so this policy cannot accidentally resize folder, All Apps, or vendor grids.
     */
    static int profileRewrite(HomeGridProfile profile, int current) {
        if (profile != HomeGridProfile.GRID_10X6) return current;
        if (current == 8) return 10;
        if (current == 4) return 6;
        return current;
    }

    /**
     * A named Workspace GridConfig is the final orientation owner during rotation. Global Android
     * Configuration can lead or lag the object MIUI is currently applying, so it is deliberately
     * not an input to this policy.
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
        return profileRewrite(profile, current);
    }
}
