package com.hellovoid.liquiddock;

import com.hellovoid.prismal.PrismalComponentControls;

/** Preference keys and launcher-process initialization for independent Prismal components. */
public final class PrismalComponentPreferences {
    public static final String LAUNCHER_SKY_HAZE = "prismal_launcher_component_sky_haze";
    public static final String LAUNCHER_SPECULAR = "prismal_launcher_component_specular";
    public static final String LAUNCHER_LIT_RIM = "prismal_launcher_component_lit_rim";
    public static final String LAUNCHER_OPPOSITE_RIM = "prismal_launcher_component_opposite_rim";
    public static final String LAUNCHER_CORNER_RIM = "prismal_launcher_component_corner_rim";
    public static final String LAUNCHER_FACE_SHEEN = "prismal_launcher_component_face_sheen";
    public static final String LAUNCHER_PLAIN_HIGHLIGHT = "prismal_launcher_component_plain_highlight";
    public static final String LAUNCHER_CAUSTICS = "prismal_launcher_component_caustics";
    public static final String LAUNCHER_PRESS_GLOW = "prismal_launcher_component_press_glow";
    public static final String LAUNCHER_COMPACT_SAFE_HIGHLIGHT =
            "prismal_launcher_component_compact_safe_highlight";

    public static final String DOCK_SKY_HAZE = "prismal_dock_component_sky_haze";
    public static final String DOCK_SPECULAR = "prismal_dock_component_specular";
    public static final String DOCK_LIT_RIM = "prismal_dock_component_lit_rim";
    public static final String DOCK_OPPOSITE_RIM = "prismal_dock_component_opposite_rim";
    public static final String DOCK_CORNER_RIM = "prismal_dock_component_corner_rim";
    public static final String DOCK_FACE_SHEEN = "prismal_dock_component_face_sheen";
    public static final String DOCK_PLAIN_HIGHLIGHT = "prismal_dock_component_plain_highlight";
    public static final String DOCK_CAUSTICS = "prismal_dock_component_caustics";
    public static final String DOCK_PRESS_GLOW = "prismal_dock_component_press_glow";

    private PrismalComponentPreferences() {}

    static void apply(ConfigReader c) {
        if (c == null) c = ConfigReader.load();
        PrismalComponentControls.configureLauncher(
                read(c, LAUNCHER_SKY_HAZE, false),
                read(c, LAUNCHER_SPECULAR, false),
                read(c, LAUNCHER_LIT_RIM, false),
                read(c, LAUNCHER_OPPOSITE_RIM, false),
                read(c, LAUNCHER_CORNER_RIM, false),
                read(c, LAUNCHER_FACE_SHEEN, false),
                read(c, LAUNCHER_PLAIN_HIGHLIGHT, false),
                read(c, LAUNCHER_CAUSTICS, false),
                read(c, LAUNCHER_PRESS_GLOW, false),
                read(c, LAUNCHER_COMPACT_SAFE_HIGHLIGHT, true));
        PrismalComponentControls.configureDock(
                read(c, DOCK_SKY_HAZE, true),
                read(c, DOCK_SPECULAR, true),
                read(c, DOCK_LIT_RIM, true),
                read(c, DOCK_OPPOSITE_RIM, true),
                read(c, DOCK_CORNER_RIM, true),
                read(c, DOCK_FACE_SHEEN, true),
                read(c, DOCK_PLAIN_HIGHLIGHT, true),
                read(c, DOCK_CAUSTICS, true),
                read(c, DOCK_PRESS_GLOW, true));
    }

    private static boolean read(ConfigReader c, String key, boolean defaultValue) {
        return c.b(key, defaultValue);
    }
}
