package com.hellovoid.liquiddock;

import java.util.Locale;

/** Pure count ownership for profile-sized Workspace grids. */
final class HomeGridCountPolicy {
    private HomeGridCountPolicy() {}

    static int profileRewrite(HomeGridProfile profile, int current) {
        if (profile != HomeGridProfile.GRID_10X6) return current;
        if (current == 8) return 10;
        if (current == 4) return 6;
        return current;
    }

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
