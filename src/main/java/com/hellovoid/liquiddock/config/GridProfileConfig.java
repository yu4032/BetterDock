package com.hellovoid.liquiddock.config;

/** Persisted selection for the supported home-screen grid profiles. */
public final class GridProfileConfig {
    public static final String PROFILE_KEY = "grid_profile";
    public static final String DEFAULT_PROFILE = "8x4";

    private GridProfileConfig() {}

    public static String normalizeProfile(String value) {
        return "10x6".equalsIgnoreCase(value) ? "10x6" : DEFAULT_PROFILE;
    }
}
