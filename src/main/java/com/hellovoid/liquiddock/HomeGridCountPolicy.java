package com.hellovoid.liquiddock;

/**
 * Resolves profile-sized Workspace counts after the stable 8x4 core has had a chance to
 * rewrite stock Launcher values. The 8x4 core intentionally remains untouched; this policy
 * is used only by the optional profile overlay.
 */
final class HomeGridCountPolicy {
    private HomeGridCountPolicy() {}

    static int profileRewrite(HomeGridProfile profile, boolean ignoredPortrait,
                              boolean ignoredXAxis, int current) {
        if (profile != HomeGridProfile.GRID_10X6) return current;

        /*
         * Do not consult Configuration here. During a physical rotation HyperOS can publish
         * the new Configuration before GridConfig/CellLayout has left the previous-orientation
         * intermediate. The device-verified 8x4 core gives us a stronger source of truth:
         *
         *   stock/legacy short axis 4 -> target short axis 6
         *   stable-core long axis    8 -> target long axis 10
         *
         * Already-resolved 6/10 values are idempotent. This makes 8x4 -> 10x6 and
         * 4x8 -> 6x10 independent of whether Configuration is early or late.
         */
        if (current == 4) return 6;
        if (current == 8) return 10;
        if (current == 6 || current == 10) return current;
        return current;
    }
}
