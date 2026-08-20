package com.hellovoid.liquiddock.config;

/** Canonical persisted keys for optional extended home-screen layouts. */
public final class GridProfileConfig {
    public static final String ENABLED_KEY = "home_grid_extended";
    public static final String PROFILE_KEY = "grid_profile";
    public static final String LEGACY_8X4_KEY = "home_grid_8x4";

    public static final boolean DEFAULT_ENABLED = false;
    public static final String DEFAULT_PROFILE = "8x4";

    private GridProfileConfig() {}

    public static String normalizeProfile(String value) {
        return "10x6".equalsIgnoreCase(value) ? "10x6" : DEFAULT_PROFILE;
    }
}
