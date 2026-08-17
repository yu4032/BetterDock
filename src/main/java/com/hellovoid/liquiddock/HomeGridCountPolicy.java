package com.hellovoid.liquiddock;

/**
 * Resolves profile-sized Workspace counts after the stable 8x4 core has had a chance to
 * rewrite stock Launcher values.  The 8x4 core intentionally remains untouched; this policy
 * is used only by the optional profile overlay.
 */
final class HomeGridCountPolicy {
    private HomeGridCountPolicy() {}

    static int profileRewrite(HomeGridProfile profile, boolean portrait,
                              boolean xAxis, int current) {
        if (profile != HomeGridProfile.GRID_10X6) return current;

        int legacy = xAxis
                ? HomeGridProfile.GRID_8X4.columns(portrait)
                : HomeGridProfile.GRID_8X4.rows(portrait);
        int target = xAxis ? profile.columns(portrait) : profile.rows(portrait);

        // The stable HomeGridHook rewrites any stock count of 6 to 8 before the overlay's
        // lowest-priority setter and inside the overlay's highest-priority getter.  Therefore
        // 8 is a legitimate intermediate value on either axis, including the 6-row/column
        // side of the 10x6 profile.  Also accept untouched stock 6 and the historical 8x4
        // axis value so startup/rotation ordering cannot leave a mixed 10x8 or 8x10 grid.
        if (current == 6 || current == 8 || current == legacy || current == target) {
            return target;
        }
        return current;
    }
}
