package com.hellovoid.liquiddock.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The persisted configuration contract.  Values intentionally retain the distinct defaults
 * used by the UI, injected runtime, and historical export format.
 */
public final class ConfigSchema {
    private ConfigSchema() {}

    public static final class Core {
        public static final ConfigKey<Boolean> ENABLED = bool(
                "liquiddock_enabled", true, true, true, ConfigKey.ExportMode.ALWAYS);

        private Core() {}
    }

    public static final class Grid {
        public static final ConfigKey<Boolean> ENABLED = bool(
                "home_grid_8x4", false, false, false, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Boolean> WIDGET_ADAPTATION = bool(
                "grid_widget_adaptation", false, false, false, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Boolean> MARGINS_DP = bool(
                "grid_margins_dp", true, false, true, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Boolean> MARGINS_OFFSET = bool(
                "grid_margins_offset", true, false, true, ConfigKey.ExportMode.ALWAYS);

        public static final ConfigKey<Integer> LANDSCAPE_HORIZONTAL_DISTANCE = dp(
                "grid_landscape_horizontal_distance", 0, 0, 0, -600, 600,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> LANDSCAPE_TOP_DISTANCE = dp(
                "grid_landscape_top_distance", 0, 0, 0, -600, 600,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> LANDSCAPE_BOTTOM_DISTANCE = dp(
                "grid_landscape_bottom_distance", 0, 0, 0, -600, 600,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> PORTRAIT_HORIZONTAL_DISTANCE = dp(
                "grid_portrait_horizontal_distance", 0, 0, 0, -600, 600,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> PORTRAIT_TOP_DISTANCE = dp(
                "grid_portrait_top_distance", 0, 0, 0, -600, 600,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> PORTRAIT_BOTTOM_DISTANCE = dp(
                "grid_portrait_bottom_distance", 0, 0, 0, -600, 600,
                ConfigKey.ExportMode.ALWAYS);

        // Migrated legacy per-edge grid settings remain exported and runtime-readable.
        public static final ConfigKey<Integer> LANDSCAPE_MARGIN_LEFT = dp(
                "grid_landscape_margin_left", 0, 0, 0, -2000, 2000,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> LANDSCAPE_MARGIN_RIGHT = dp(
                "grid_landscape_margin_right", 0, 0, 0, -2000, 2000,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> LANDSCAPE_MARGIN_TOP = dp(
                "grid_landscape_margin_top", 0, 0, 0, -2000, 2000,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> LANDSCAPE_MARGIN_BOTTOM = dp(
                "grid_landscape_margin_bottom", 0, 0, 0, -2000, 2000,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> PORTRAIT_MARGIN_LEFT = dp(
                "grid_portrait_margin_left", 0, 0, 0, -2000, 2000,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> PORTRAIT_MARGIN_RIGHT = dp(
                "grid_portrait_margin_right", 0, 0, 0, -2000, 2000,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> PORTRAIT_MARGIN_TOP = dp(
                "grid_portrait_margin_top", 0, 0, 0, -2000, 2000,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> PORTRAIT_MARGIN_BOTTOM = dp(
                "grid_portrait_margin_bottom", 0, 0, 0, -2000, 2000,
                ConfigKey.ExportMode.ALWAYS);

        // Runtime defaults for row gaps are compatibility-dependent and stay in LiquidDockConfig.
        public static final ConfigKey<Integer> LANDSCAPE_ROW_GAP = dp(
                "grid_landscape_row_gap", 0, null, 0, -200, 400,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> PORTRAIT_ROW_GAP = dp(
                "grid_portrait_row_gap", 0, null, 0, -200, 400,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> LANDSCAPE_INDICATOR_Y = dp(
                "indicator_landscape_y", 0, 0, 0, -160, 160,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> PORTRAIT_INDICATOR_Y = dp(
                "indicator_portrait_y", 0, 0, 0, -160, 160,
                ConfigKey.ExportMode.ALWAYS);

        // Import-only JSON aliases from pre-axis-distance exports. They never become
        // independent current preferences and therefore have no scalar runtime fallback.
        public static final ConfigKey<Integer> LEGACY_LANDSCAPE_HORIZONTAL_MARGIN = integer(
                "grid_landscape_margin_horizontal", 0, null, null, null, null,
                ConfigKey.ExportMode.NEVER);
        public static final ConfigKey<Integer> LEGACY_PORTRAIT_HORIZONTAL_MARGIN = integer(
                "grid_portrait_margin_horizontal", 0, null, null, null, null,
                ConfigKey.ExportMode.NEVER);
        public static final ConfigKey<Integer> LEGACY_MARGIN_LEFT = integer(
                "grid_margin_left", 160, null, null, null, null, ConfigKey.ExportMode.NEVER);
        public static final ConfigKey<Integer> LEGACY_MARGIN_RIGHT = integer(
                "grid_margin_right", 160, null, null, null, null, ConfigKey.ExportMode.NEVER);
        public static final ConfigKey<Integer> LEGACY_MARGIN_TOP = integer(
                "grid_margin_top", 80, null, null, null, null, ConfigKey.ExportMode.NEVER);
        public static final ConfigKey<Integer> LEGACY_MARGIN_BOTTOM = integer(
                "grid_margin_bottom", 80, null, null, null, null, ConfigKey.ExportMode.NEVER);

        private Grid() {}
    }

    public static final class Dock {
        public static final ConfigKey<Boolean> ENABLED = bool(
                "dock_customization", true, true, true, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Boolean> RESIZE_ANIMATION = bool(
                "dock_resize_animation", false, false, false, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Boolean> SMOOTH_RESIZE_ANIMATION = bool(
                "dock_smooth_resize_animation", true, true, true, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Boolean> DIMENSIONS_DP = bool(
                "dock_dimensions_dp", true, false, true, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> WIDTH_OFFSET = dp(
                "width_offset", 0, 0, 0, -80, 80, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> HEIGHT_OFFSET = dp(
                "height_offset", 0, 0, 0, -80, 80, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> SPACING = dp(
                "dock_spacing", 0, 0, 0, -8, 12, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> BOTTOM_OFFSET = dp(
                "dock_bottom_offset", 0, 0, 0, -30, 40, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> BLUR_RADIUS = integer(
                "blur_radius", 100, 100, 100, 0, 400, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Boolean> CORNERS_DP = bool(
                "corners_dp", true, false, true, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> CORNER_OFFSET = dp(
                "corner_offset", -1, -1, -1, -50, 100, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> BLUR_CORNER_OFFSET = dp(
                "blur_corner_offset", 0, 0, 0, -50, 100, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Boolean> SQUIRCLE = bool(
                "squircle", false, false, false, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Boolean> FILL_DIFF = bool(
                "fill_diff", false, false, false, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Boolean> STROKE_ENABLED = bool(
                "dock_stroke", true, true, true, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> SQUIRCLE_CONTROL_POINT = integer(
                "sq_outer_cp", 58, 58, 58, 40, 80, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> SQUIRCLE_STROKE_WIDTH = dp(
                "sq_stroke_w", 1, 4, 4, 0, 10, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> SQUIRCLE_STROKE_OFFSET = dp(
                "sq_stroke_off", 3, 8, 8, 0, 16, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> FILL_DIFF_STROKE_WIDTH = dp(
                "stroke_w", 1, 2, 2, 0, 6, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> STANDARD_STROKE_WIDTH = dp(
                "std_stroke_w", 1, 4, 4, 0, 10, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> STROKE_RED = integer(
                "stroke_base_r", 255, 255, 255, 0, 255, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> STROKE_GREEN = integer(
                "stroke_base_g", 255, 255, 255, 0, 255, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> STROKE_BLUE = integer(
                "stroke_base_b", 255, 255, 255, 0, 255, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> STROKE_ALPHA = integer(
                "stroke_base_alpha", 255, 255, 255, 0, 255, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Boolean> STROKE_SHADOW = bool(
                "stroke_shadow", false, false, false, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> STROKE_SHADOW_RADIUS = dp(
                "shadow_radius", 3, 8, 8, 1, 24, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> STROKE_SHADOW_ALPHA = integer(
                "shadow_alpha", 70, 70, 70, 0, 200, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Boolean> SHADOW_ENABLED = bool(
                "dock_shadow", true, true, true, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> SHADOW_RADIUS = dp(
                "dock_shadow_radius", 15, 42, 42, 1, 40, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> SHADOW_SIZE = dp(
                "dock_shadow_size", 18, 52, 52, 1, 60, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> SHADOW_ALPHA = integer(
                "dock_shadow_alpha", 140, 140, 140, 0, 200, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> SHADOW_Y = dp(
                "dock_shadow_y", 4, 12, 12, -24, 24, ConfigKey.ExportMode.ALWAYS);

        private Dock() {}
    }

    public static final class Divider {
        // Explicit-versus-legacy runtime defaults are conditional and deliberately not flattened.
        // Divider width/Y are historical raw tenths-of-dp integers in JSON, not DP_TENTHS
        // sidecar values; DIRECT preserves the old import clamps and export representation.
        public static final ConfigKey<Boolean> ENABLED = bool(
                "dock_divider_enabled", false, null, false, ConfigKey.ExportMode.IF_PRESENT);
        public static final ConfigKey<Integer> WIDTH_DP = integer(
                "dock_divider_width_dp", 10, null, 0, 0, 160, ConfigKey.ExportMode.IF_PRESENT);
        public static final ConfigKey<Integer> HEIGHT_SCALE = integer(
                "dock_divider_height_scale", 60, null, 0, 0, 100, ConfigKey.ExportMode.IF_PRESENT);
        public static final ConfigKey<Integer> Y_OFFSET_DP = integer(
                "dock_divider_y_offset", 0, null, 0, -80, 80, ConfigKey.ExportMode.IF_PRESENT);
        public static final ConfigKey<Integer> COLOR_RED = integer(
                "dock_divider_color_r", 255, null, 0, 0, 255, ConfigKey.ExportMode.IF_PRESENT);
        public static final ConfigKey<Integer> COLOR_GREEN = integer(
                "dock_divider_color_g", 255, null, 0, 0, 255, ConfigKey.ExportMode.IF_PRESENT);
        public static final ConfigKey<Integer> COLOR_BLUE = integer(
                "dock_divider_color_b", 255, null, 0, 0, 255, ConfigKey.ExportMode.IF_PRESENT);
        public static final ConfigKey<Integer> ALPHA = integer(
                "dock_divider_alpha", 128, null, 0, 0, 255, ConfigKey.ExportMode.IF_PRESENT);

        private Divider() {}
    }

    public static final class Glass {
        public static final ConfigKey<Boolean> ENABLED = bool(
                "liquid_glass", false, false, false, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Boolean> DIMENSIONS_DP = bool(
                "liquid_dimensions_dp", true, false, true, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<String> BLUR_MODE = string(
                "liquid_blur_mode", "shader", "shader", "shader",
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Boolean> MIUIX_307_PIPELINE = bool(
                "liquid_miuix_307_pipeline", false, false, false, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> BLUR = dp(
                "liquid_blur", 6, 6, 6, 0, 60, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> CHROMATIC = integer(
                "liquid_chromatic", 8, 8, 8, 0, 40, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> TINT_ALPHA = integer(
                "liquid_tint_alpha", 38, 38, 38, 0, 160, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> CAPTURE_FPS = integer(
                "liquid_capture_power_limit_fps", 20, 20, 20, 5, 60, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> CAPTURE_STOP_DELAY = integer(
                "liquid_capture_stop_delay", 150, 150, 150, 0, 10000, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> CAPTURE_BLEED_TOP = dp(
                "liquid_capture_bleed_top", 17, -1, 48, 0, 256, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> CAPTURE_BLEED_BOTTOM = dp(
                "liquid_capture_bleed_bottom", 6, -1, 16, 0, 256, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> THICKNESS = dp(
                "liquid_thickness", 18, 18, 18, 1, 60, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> IOR = integer(
                "liquid_ior", 155, 155, 155, 100, 200, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> NORMAL_STRENGTH = integer(
                "liquid_normal_strength", 115, 115, 115, 0, 300, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> DOME = integer(
                "liquid_dome", 100, 100, 100, 0, 200, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> LENS_REFRACTION = dp(
                "liquid_lens_refraction", 12, 12, 12, 0, 60, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> CAPTURE_SCALE = integer(
                "liquid_capture_scale", 50, 50, 50, 10, 100, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Boolean> DYNAMIC_APP_CAPTURE = bool(
                "liquid_dynamic_app_capture", true, true, true, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Boolean> FULLSCREEN_CAPTURE = bool(
                "liquid_capture_fullscreen", true, true, true, ConfigKey.ExportMode.NEVER);
        public static final ConfigKey<Integer> DYNAMIC_APP_PROBE_FPS = integer(
                "liquid_dynamic_app_probe_fps", 3, 3, 3, 1, 10, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> DYNAMIC_MOTION_THRESHOLD = integer(
                "liquid_dynamic_motion_threshold", 12, 12, 12, 1, 240, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> DYNAMIC_BIT_THRESHOLD = integer(
                "liquid_dynamic_bit_threshold", 18, 18, 18, 1, 64, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> DYNAMIC_HOLD_MS = integer(
                "liquid_dynamic_hold_ms", 900, 900, 900, 0, 5000, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> BLACK_THRESHOLD = integer(
                "liquid_black_threshold", 10, 10, 10, 0, 64, ConfigKey.ExportMode.ALWAYS);
        // Historical JSON export/import included this key in the _tenths round-trip list even
        // though the current UI presents whole milliseconds. Preserve that storage contract.
        public static final ConfigKey<Integer> HOME_SETTLE_DELAY_MS = dp(
                "liquid_home_settle_delay", 1200, 1200, 1200, 200, 3000, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> HIGHLIGHT_WIDTH = integer(
                "liquid_highlight_width", 100, 100, 100, 20, 300, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> TINT_RED = integer(
                "liquid_tint_r", 238, 238, 238, 0, 255, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> TINT_GREEN = integer(
                "liquid_tint_g", 244, 244, 244, 0, 255, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> TINT_BLUE = integer(
                "liquid_tint_b", 255, 255, 255, 0, 255, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> DEPTH_EFFECT = integer(
                "liquid_depth_effect", 8, 8, 8, 0, 50, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> BRIGHTNESS = integer(
                "liquid_brightness", 108, 108, 108, 50, 200, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> SPECULAR_SHARPNESS = integer(
                "liquid_specular_sharp", 88, 88, 88, 1, 200, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> SPECULAR_STRENGTH = integer(
                "liquid_specular_strength", 105, 105, 105, 0, 300, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> RIM_LIGHT = integer(
                "liquid_rim_light", 100, 100, 100, 0, 300, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> CAUSTICS = integer(
                "liquid_caustics", 28, 28, 28, 0, 100, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> EDGE_BAND = integer(
                "liquid_edge_band", 32, 32, 32, 5, 100, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> HIGHLIGHT_ALPHA = integer(
                "liquid_highlight_alpha", 100, 100, 100, 0, 200, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> RECENTS_PREARM_DISTANCE = dp(
                "liquid_recents_prearm_distance", 8, 8, 0, 1, 48, ConfigKey.ExportMode.ALWAYS);

        private Glass() {}
    }

    public static final class Workstation {
        public static final ConfigKey<Boolean> DOCK_CUSTOMIZATION = bool(
                "workstation_dock_customization", false, false, false, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> DOCK_WIDTH_OFFSET = dp(
                "workstation_dock_width_offset", 0, 0, 0, -240, 240,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> GRID_HORIZONTAL_OFFSET = dp(
                "workstation_grid_horizontal_offset", 0, 0, 0, -240, 240,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> ALL_APPS_LANDSCAPE_HORIZONTAL_OFFSET = dp(
                "workstation_all_apps_landscape_horizontal_offset", 0, 0, 0, 0, 240,
                ConfigKey.ExportMode.ALWAYS);
        // Merged vertical keys are retained for old configs/JSON only; current UI writes
        // independent top/bottom spacing keys below.
        public static final ConfigKey<Integer> ALL_APPS_LANDSCAPE_VERTICAL_OFFSET = dp(
                "workstation_all_apps_landscape_vertical_offset", 0, 0, 0, 0, 240,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> ALL_APPS_LANDSCAPE_TOP_SPACING = dp(
                "workstation_all_apps_landscape_top_spacing", 0, 0, 0, 0, 240,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> ALL_APPS_LANDSCAPE_BOTTOM_SPACING = dp(
                "workstation_all_apps_landscape_bottom_spacing", 0, 0, 0, 0, 240,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> ALL_APPS_PORTRAIT_HORIZONTAL_OFFSET = dp(
                "workstation_all_apps_portrait_horizontal_offset", 0, 0, 0, 0, 240,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> ALL_APPS_PORTRAIT_VERTICAL_OFFSET = dp(
                "workstation_all_apps_portrait_vertical_offset", 0, 0, 0, 0, 240,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> ALL_APPS_PORTRAIT_TOP_SPACING = dp(
                "workstation_all_apps_portrait_top_spacing", 0, 0, 0, 0, 240,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> ALL_APPS_PORTRAIT_BOTTOM_SPACING = dp(
                "workstation_all_apps_portrait_bottom_spacing", 0, 0, 0, 0, 240,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> DOCK_ICON_TOP_OFFSET = dp(
                "workstation_dock_icon_top_offset", 0, 0, 0, -48, 48,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> DOCK_ICON_BOTTOM_OFFSET = dp(
                "workstation_dock_icon_bottom_offset", 0, 0, 0, -48, 48,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> LEGACY_ALL_APPS_HORIZONTAL_OFFSET = dp(
                "workstation_all_apps_horizontal_offset", 0, 0, 0, null, null,
                ConfigKey.ExportMode.NEVER);
        public static final ConfigKey<Integer> LEGACY_ALL_APPS_VERTICAL_OFFSET = dp(
                "workstation_all_apps_vertical_offset", 0, 0, 0, null, null,
                ConfigKey.ExportMode.NEVER);

        private Workstation() {}
    }

    public static final class Debug {
        public static final ConfigKey<Boolean> LOGGING = bool(
                "liquiddock_debug_log", false, false, false, ConfigKey.ExportMode.NEVER);

        private Debug() {}
    }

    public static List<ConfigKey<?>> all() { return AllHolder.KEYS; }

    private static final class AllHolder {
        private static final List<ConfigKey<?>> KEYS = createAll();

        private AllHolder() {}
    }

    private static List<ConfigKey<?>> createAll() {
        List<ConfigKey<?>> keys = new ArrayList<>();
        add(keys, Core.ENABLED);
        add(keys, Grid.ENABLED, Grid.WIDGET_ADAPTATION, Grid.MARGINS_DP, Grid.MARGINS_OFFSET,
                Grid.LANDSCAPE_HORIZONTAL_DISTANCE, Grid.LANDSCAPE_TOP_DISTANCE,
                Grid.LANDSCAPE_BOTTOM_DISTANCE, Grid.PORTRAIT_HORIZONTAL_DISTANCE,
                Grid.PORTRAIT_TOP_DISTANCE, Grid.PORTRAIT_BOTTOM_DISTANCE,
                Grid.LANDSCAPE_MARGIN_LEFT, Grid.LANDSCAPE_MARGIN_RIGHT,
                Grid.LANDSCAPE_MARGIN_TOP, Grid.LANDSCAPE_MARGIN_BOTTOM,
                Grid.PORTRAIT_MARGIN_LEFT, Grid.PORTRAIT_MARGIN_RIGHT,
                Grid.PORTRAIT_MARGIN_TOP, Grid.PORTRAIT_MARGIN_BOTTOM,
                Grid.LANDSCAPE_ROW_GAP, Grid.PORTRAIT_ROW_GAP,
                Grid.LANDSCAPE_INDICATOR_Y, Grid.PORTRAIT_INDICATOR_Y,
                Grid.LEGACY_LANDSCAPE_HORIZONTAL_MARGIN,
                Grid.LEGACY_PORTRAIT_HORIZONTAL_MARGIN, Grid.LEGACY_MARGIN_LEFT,
                Grid.LEGACY_MARGIN_RIGHT, Grid.LEGACY_MARGIN_TOP,
                Grid.LEGACY_MARGIN_BOTTOM);
        add(keys, Dock.ENABLED, Dock.RESIZE_ANIMATION, Dock.SMOOTH_RESIZE_ANIMATION,
                Dock.DIMENSIONS_DP, Dock.WIDTH_OFFSET, Dock.HEIGHT_OFFSET, Dock.SPACING,
                Dock.BOTTOM_OFFSET, Dock.BLUR_RADIUS, Dock.CORNERS_DP, Dock.CORNER_OFFSET,
                Dock.BLUR_CORNER_OFFSET, Dock.SQUIRCLE, Dock.FILL_DIFF, Dock.STROKE_ENABLED,
                Dock.SQUIRCLE_CONTROL_POINT, Dock.SQUIRCLE_STROKE_WIDTH,
                Dock.SQUIRCLE_STROKE_OFFSET, Dock.FILL_DIFF_STROKE_WIDTH,
                Dock.STANDARD_STROKE_WIDTH, Dock.STROKE_RED, Dock.STROKE_GREEN,
                Dock.STROKE_BLUE, Dock.STROKE_ALPHA, Dock.STROKE_SHADOW,
                Dock.STROKE_SHADOW_RADIUS, Dock.STROKE_SHADOW_ALPHA, Dock.SHADOW_ENABLED,
                Dock.SHADOW_RADIUS, Dock.SHADOW_SIZE, Dock.SHADOW_ALPHA, Dock.SHADOW_Y);
        add(keys, Divider.ENABLED, Divider.WIDTH_DP, Divider.HEIGHT_SCALE, Divider.Y_OFFSET_DP,
                Divider.COLOR_RED, Divider.COLOR_GREEN, Divider.COLOR_BLUE, Divider.ALPHA);
        add(keys, Glass.ENABLED, Glass.DIMENSIONS_DP, Glass.BLUR_MODE, Glass.BLUR, Glass.CHROMATIC,
                Glass.TINT_ALPHA, Glass.CAPTURE_FPS, Glass.CAPTURE_STOP_DELAY,
                Glass.CAPTURE_BLEED_TOP, Glass.CAPTURE_BLEED_BOTTOM, Glass.THICKNESS,
                Glass.IOR, Glass.NORMAL_STRENGTH, Glass.DOME, Glass.LENS_REFRACTION,
                Glass.CAPTURE_SCALE, Glass.DYNAMIC_APP_CAPTURE, Glass.FULLSCREEN_CAPTURE,
                Glass.DYNAMIC_APP_PROBE_FPS, Glass.DYNAMIC_MOTION_THRESHOLD,
                Glass.DYNAMIC_BIT_THRESHOLD, Glass.DYNAMIC_HOLD_MS, Glass.BLACK_THRESHOLD,
                Glass.HOME_SETTLE_DELAY_MS, Glass.HIGHLIGHT_WIDTH, Glass.TINT_RED,
                Glass.TINT_GREEN, Glass.TINT_BLUE, Glass.DEPTH_EFFECT, Glass.BRIGHTNESS,
                Glass.SPECULAR_SHARPNESS, Glass.SPECULAR_STRENGTH, Glass.RIM_LIGHT,
                Glass.CAUSTICS, Glass.EDGE_BAND, Glass.HIGHLIGHT_ALPHA,
                Glass.RECENTS_PREARM_DISTANCE);
        add(keys, Workstation.DOCK_CUSTOMIZATION, Workstation.DOCK_WIDTH_OFFSET,
                Workstation.GRID_HORIZONTAL_OFFSET,
                Workstation.ALL_APPS_LANDSCAPE_HORIZONTAL_OFFSET,
                Workstation.ALL_APPS_LANDSCAPE_VERTICAL_OFFSET,
                Workstation.ALL_APPS_LANDSCAPE_TOP_SPACING,
                Workstation.ALL_APPS_LANDSCAPE_BOTTOM_SPACING,
                Workstation.ALL_APPS_PORTRAIT_HORIZONTAL_OFFSET,
                Workstation.ALL_APPS_PORTRAIT_VERTICAL_OFFSET,
                Workstation.ALL_APPS_PORTRAIT_TOP_SPACING,
                Workstation.ALL_APPS_PORTRAIT_BOTTOM_SPACING,
                Workstation.DOCK_ICON_TOP_OFFSET, Workstation.DOCK_ICON_BOTTOM_OFFSET,
                Workstation.LEGACY_ALL_APPS_HORIZONTAL_OFFSET,
                Workstation.LEGACY_ALL_APPS_VERTICAL_OFFSET);
        add(keys, Debug.LOGGING);
        return Collections.unmodifiableList(keys);
    }

    private static void add(List<ConfigKey<?>> keys, ConfigKey<?>... added) {
        Collections.addAll(keys, added);
    }

    private static ConfigKey<Boolean> bool(String name, Boolean uiDefault, Boolean runtimeFallback,
                                           Boolean exportDefault, ConfigKey.ExportMode exportMode) {
        return new ConfigKey<>(name, ConfigKey.Type.BOOLEAN, uiDefault, runtimeFallback,
                exportDefault, null, null, ConfigKey.StorageMode.DIRECT, exportMode);
    }

    private static ConfigKey<String> string(String name, String uiDefault,
                                             String runtimeFallback, String exportDefault,
                                             ConfigKey.ExportMode exportMode) {
        return new ConfigKey<>(name, ConfigKey.Type.STRING, uiDefault, runtimeFallback,
                exportDefault, null, null, ConfigKey.StorageMode.DIRECT, exportMode);
    }

    private static ConfigKey<Integer> integer(String name, Integer uiDefault,
                                               Integer runtimeFallback, Integer exportDefault,
                                               Integer min, Integer max,
                                               ConfigKey.ExportMode exportMode) {
        return new ConfigKey<>(name, ConfigKey.Type.INT, uiDefault, runtimeFallback,
                exportDefault, min, max, ConfigKey.StorageMode.DIRECT, exportMode);
    }

    private static ConfigKey<Integer> dp(String name, Integer uiDefault, Integer runtimeFallback,
                                          Integer exportDefault, Integer min, Integer max,
                                          ConfigKey.ExportMode exportMode) {
        return new ConfigKey<>(name, ConfigKey.Type.INT, uiDefault, runtimeFallback,
                exportDefault, min, max, ConfigKey.StorageMode.DP_TENTHS, exportMode);
    }
}
