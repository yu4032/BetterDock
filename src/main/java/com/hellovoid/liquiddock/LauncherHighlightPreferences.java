package com.hellovoid.liquiddock;

import com.hellovoid.prismal.PrismalHighlightProfile;

/** Preferences shared by folder glass and future widget glass; Dock never reads these keys. */
public final class LauncherHighlightPreferences {
    public static final String SKY_HAZE = "launcher_surface_component_sky_haze";
    public static final String SPECULAR = "launcher_surface_component_specular";
    public static final String LIT_RIM = "launcher_surface_component_lit_rim";
    public static final String OPPOSITE_RIM = "launcher_surface_component_opposite_rim";
    public static final String CORNER_RIM = "launcher_surface_component_corner_rim";
    public static final String FACE_SHEEN = "launcher_surface_component_face_sheen";
    public static final String PLAIN_HIGHLIGHT = "launcher_surface_component_plain_highlight";
    public static final String CAUSTICS = "launcher_surface_component_caustics";
    public static final String PRESS_GLOW = "launcher_surface_component_press_glow";

    private LauncherHighlightPreferences() {}

    static PrismalHighlightProfile read(ConfigReader c) {
        return new PrismalHighlightProfile(
                c.b(SKY_HAZE, true),
                c.b(SPECULAR, true),
                c.b(LIT_RIM, true),
                c.b(OPPOSITE_RIM, true),
                c.b(CORNER_RIM, true),
                c.b(FACE_SHEEN, true),
                c.b(PLAIN_HIGHLIGHT, true),
                c.b(CAUSTICS, true),
                c.b(PRESS_GLOW, true));
    }
}
