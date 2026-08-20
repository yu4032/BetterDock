package com.hellovoid.liquiddock.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.util.DisplayMetrics;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Owns persisted preset values and calculations, without UI side effects. */
public final class PresetManager {
    private static final Map<String, Object> DEFAULT_VALUES = createDefaultValues();

    private PresetManager() {}

    public static Map<String, Object> defaultValues() {
        return DEFAULT_VALUES;
    }

    public static void applyDefault(SharedPreferences.Editor editor) {
        for (Map.Entry<String, Object> entry : DEFAULT_VALUES.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean) {
                editor.putBoolean(entry.getKey(), (Boolean) value);
            } else if (value instanceof String) {
                editor.putString(entry.getKey(), (String) value);
            } else if (value instanceof Integer) {
                editor.putInt(entry.getKey(), (Integer) value);
            }
        }
        editor.commit();
    }

    public static IpadPresetResult applyIpad(Context context, SharedPreferences preferences) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float density = Math.max(0.1f, dm.density);
        float shortSideDp = Math.min(dm.widthPixels, dm.heightPixels) / density;
        float displayScale = Math.max(0.90f, Math.min(1.20f, shortSideDp / 668f));

        Resources launcherRes = null;
        String launcherPackage = "com.miui.home";
        try {
            launcherRes = context.createPackageContext(launcherPackage,
                    Context.CONTEXT_IGNORE_SECURITY).getResources();
        } catch (PackageManager.NameNotFoundException ignored) {}

        int icon = dimenPx(launcherRes, launcherPackage,
                "config_hotseats_icon_content_default_height", 60f, density);
        int cell = dimenPx(launcherRes, launcherPackage,
                "hotseats_list_content_cell_width", 80f, density);
        int dockHeight = dimenPx(launcherRes, launcherPackage,
                "hotseats_height_land", 78f, density);
        int dockRadius = dimenPx(launcherRes, launcherPackage,
                "hotseats_list_content_background_radius", 21f, density);
        int sidePadding = dimenPx(launcherRes, launcherPackage,
                "hotseats_list_content_padding_side", 9.3f, density);

        int targetGap = Math.round(14f * density * displayScale);
        int targetHeight = icon + Math.round(20f * density * displayScale);
        int targetRadius = Math.round(22f * density * displayScale);
        int targetSidePadding = Math.round(14f * density * displayScale);
        int spacing = Math.round((icon + targetGap - cell) / 2f);
        int heightOffset = targetHeight - dockHeight;
        int widthOffset = 2 * (targetSidePadding - sidePadding);
        int cornerOffset = targetRadius - dockRadius;
        int oneDp = Math.max(1, Math.round(displayScale));
        int bottomOffset = Math.round(10f * density * displayScale);

        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(ConfigSchema.Dock.BLUR_RADIUS.name(), 100);
        editor.putBoolean(ConfigSchema.Dock.DIMENSIONS_DP.name(), true);
        editor.putBoolean(ConfigSchema.Dock.CORNERS_DP.name(), true);
        putDp(editor, ConfigSchema.Dock.HEIGHT_OFFSET, heightOffset / density);
        putDp(editor, ConfigSchema.Dock.WIDTH_OFFSET, widthOffset / density);
        putDp(editor, ConfigSchema.Dock.CORNER_OFFSET, cornerOffset / density);
        putDp(editor, ConfigSchema.Dock.BLUR_CORNER_OFFSET, -1f);
        editor.putBoolean(ConfigSchema.Grid.ENABLED.name(), true);
        editor.putBoolean(ConfigSchema.Grid.MARGINS_DP.name(), true);
        editor.putBoolean(ConfigSchema.Grid.MARGINS_OFFSET.name(), true);
        editor.putInt("grid_landscape_margin_left", 0);
        editor.putInt("grid_landscape_margin_right", 0);
        editor.putInt("grid_landscape_margin_top", 0);
        editor.putInt("grid_landscape_margin_bottom", 0);
        editor.putInt("grid_portrait_margin_left", 0);
        editor.putInt("grid_portrait_margin_right", 0);
        editor.putInt("grid_portrait_margin_top", 0);
        editor.putInt("grid_portrait_margin_bottom", 0);
        editor.putInt("grid_landscape_row_gap", 0);
        editor.putInt("grid_portrait_row_gap", 0);
        editor.putInt("indicator_landscape_y", 0);
        editor.putInt("indicator_portrait_y", 0);
        editor.putBoolean(ConfigSchema.Dock.ENABLED.name(), true);
        editor.putBoolean(ConfigSchema.Dock.STROKE_ENABLED.name(), true);
        editor.putInt(ConfigSchema.Dock.STROKE_RED.name(), 255);
        editor.putInt(ConfigSchema.Dock.STROKE_GREEN.name(), 255);
        editor.putInt(ConfigSchema.Dock.STROKE_BLUE.name(), 255);
        editor.putInt(ConfigSchema.Dock.STROKE_ALPHA.name(), 255);
        editor.putBoolean(ConfigSchema.Dock.SQUIRCLE.name(), true);
        putDp(editor, ConfigSchema.Dock.SQUIRCLE_STROKE_WIDTH, oneDp);
        putDp(editor, ConfigSchema.Dock.SQUIRCLE_STROKE_OFFSET, 0f);
        editor.putInt(ConfigSchema.Dock.SQUIRCLE_CONTROL_POINT.name(), 65);
        editor.putBoolean(ConfigSchema.Dock.FILL_DIFF.name(), true);
        putDp(editor, ConfigSchema.Dock.FILL_DIFF_STROKE_WIDTH, oneDp);
        putDp(editor, ConfigSchema.Dock.STANDARD_STROKE_WIDTH, oneDp);
        editor.putBoolean(ConfigSchema.Dock.SHADOW_ENABLED.name(), true);
        putDp(editor, ConfigSchema.Dock.SHADOW_RADIUS, 10f * displayScale);
        putDp(editor, ConfigSchema.Dock.SHADOW_SIZE, 13f * displayScale);
        editor.putInt(ConfigSchema.Dock.SHADOW_ALPHA.name(), 140);
        putDp(editor, ConfigSchema.Dock.SHADOW_Y, 3f * displayScale);
        editor.putBoolean(ConfigSchema.Dock.STROKE_SHADOW.name(), false);
        putDp(editor, ConfigSchema.Dock.STROKE_SHADOW_RADIUS, 3f * displayScale);
        editor.putInt(ConfigSchema.Dock.STROKE_SHADOW_ALPHA.name(), 70);
        putDp(editor, ConfigSchema.Dock.SPACING, spacing / density);
        putDp(editor, ConfigSchema.Dock.BOTTOM_OFFSET, bottomOffset / density);
        editor.commit();

        return new IpadPresetResult(spacing, heightOffset, widthOffset, cornerOffset, bottomOffset);
    }

    private static Map<String, Object> createDefaultValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("liquiddock_enabled", true);
        values.put("home_grid_8x4", false);
        values.put("grid_widget_adaptation", false);
        values.put("grid_margins_dp", true);
        values.put("grid_margins_offset", true);
        putDp(values, "grid_landscape_margin_left", 30f);
        putDp(values, "grid_landscape_margin_right", 30f);
        putDp(values, "grid_landscape_margin_top", -35f);
        putDp(values, "grid_landscape_margin_bottom", 20f);
        putDp(values, "grid_portrait_margin_left", 0f);
        putDp(values, "grid_portrait_margin_right", 0f);
        putDp(values, "grid_portrait_margin_top", 0f);
        putDp(values, "grid_portrait_margin_bottom", 100f);
        putDp(values, "grid_landscape_row_gap", 0f);
        putDp(values, "grid_portrait_row_gap", -16f);
        putDp(values, "grid_landscape_horizontal_distance", 30f);
        putDp(values, "grid_landscape_top_distance", 0f);
        putDp(values, "grid_landscape_bottom_distance", 20f);
        putDp(values, "grid_portrait_horizontal_distance", 0f);
        putDp(values, "grid_portrait_top_distance", 10.3f);
        putDp(values, "grid_portrait_bottom_distance", 0f);
        putDp(values, "indicator_landscape_y", -8.8f);
        putDp(values, "indicator_portrait_y", 11.8f);
        values.put("dock_customization", true);
        values.put("dock_dimensions_dp", true);
        values.put("dock_resize_animation", false);
        values.put("dock_smooth_resize_animation", true);
        values.put("workstation_dock_customization", false);
        values.put("dock_divider_enabled", false);
        values.put("blur_radius", 100);

        // Effective Prismal v1.0.6 Quick Start optics: PrismalFrameLayout defaults followed
        // by PrismalLiquidGlass.applyBase(). Keep synchronized with runtime fallbacks.
        values.put("liquid_glass", true);
        values.put("liquid_dimensions_dp", true);
        values.put("liquid_blur_mode", "shader");
        values.put("liquid_ior", 155);
        values.put("liquid_normal_strength", 115);
        values.put("liquid_dome", 130);
        values.put("liquid_chromatic", 26);
        values.put("liquid_tint_alpha", 35);
        values.put("liquid_tint_r", 0);
        values.put("liquid_tint_g", 0);
        values.put("liquid_tint_b", 255);
        values.put("liquid_highlight_width", 100);
        values.put("liquid_highlight_alpha", 100);
        values.put("liquid_depth_effect", 0);
        values.put("liquid_brightness", 108);
        values.put("liquid_specular_sharp", 88);
        values.put("liquid_specular_strength", 152);
        values.put("liquid_rim_light", 122);
        values.put("liquid_caustics", 28);
        values.put("liquid_edge_band", 32);
        values.put("liquid_capture_power_limit_fps", 30);
        values.put("liquid_dynamic_app_capture", true);
        values.put("liquid_dynamic_app_probe_fps", 3);
        values.put("liquid_dynamic_motion_threshold", 12);
        values.put("liquid_dynamic_bit_threshold", 18);
        values.put("liquid_dynamic_hold_ms", 900);
        values.put("liquid_black_threshold", 10);
        values.put("liquid_capture_scale", 100);
        values.put("liquid_capture_stop_delay", 150);
        putDp(values, "liquid_blur", 2f);
        putDp(values, "liquid_thickness", 18f);
        putDp(values, "liquid_lens_refraction", 1.3f);
        values.put("liquid_capture_bleed_top", 48);
        values.put("liquid_capture_bleed_bottom", 16);
        values.put("liquid_capture_bleed_left", 0);
        values.put("liquid_capture_bleed_right", 0);
        putDp(values, "liquid_recents_prearm_distance", 8f);

        putDp(values, "liquid_prismal_refraction_inset", 20f);
        values.put("liquid_prismal_displacement_scale", 115);
        putDp(values, "liquid_prismal_height_transition_width", 19f);
        putDp(values, "liquid_prismal_smin_smoothing", 1.8f);
        values.put("liquid_prismal_edge_refraction_falloff", 400);
        values.put("liquid_prismal_fresnel_reflect", 198);
        values.put("liquid_prismal_dispersion_r", 100);
        values.put("liquid_prismal_dispersion_b", 100);
        values.put("liquid_prismal_vibrancy", 128);
        values.put("liquid_prismal_plain_highlight", 8);
        values.put("liquid_prismal_light_dir_x", -50);
        values.put("liquid_prismal_light_dir_y", -80);
        values.put("liquid_prismal_shadow_r", 255);
        values.put("liquid_prismal_shadow_g", 255);
        values.put("liquid_prismal_shadow_b", 255);
        values.put("liquid_prismal_shadow_alpha", 35);
        values.put("liquid_prismal_shadow_softness", 1000);
        values.put("liquid_prismal_transmittance", 100);
        values.put("liquid_prismal_backdrop_scale_x", 100);
        values.put("liquid_prismal_backdrop_scale_y", 100);
        values.put("liquid_prismal_parallax_scale", 100);
        values.put("liquid_prismal_show_normals", false);

        values.put("corners_dp", true);
        values.put("dock_stroke", true);
        values.put("stroke_base_r", 180);
        values.put("stroke_base_g", 180);
        values.put("stroke_base_b", 180);
        values.put("stroke_base_alpha", 119);
        values.put("squircle", true);
        values.put("sq_outer_cp", 65);
        values.put("fill_diff", true);
        values.put("dock_shadow", true);
        values.put("dock_shadow_alpha", 64);
        values.put("stroke_shadow", false);
        values.put("shadow_alpha", 70);
        putDp(values, "height_offset", 2.2f);
        putDp(values, "width_offset", 0f);
        putDp(values, "corner_offset", 1f);
        putDp(values, "blur_corner_offset", -1f);
        putDp(values, "sq_stroke_w", 1f);
        putDp(values, "sq_stroke_off", 0f);
        putDp(values, "stroke_w", 1f);
        putDp(values, "std_stroke_w", 1f);
        putDp(values, "dock_shadow_radius", 10f);
        putDp(values, "dock_shadow_size", 4.7f);
        putDp(values, "dock_shadow_y", 0f);
        putDp(values, "shadow_radius", 3f);
        putDp(values, "dock_spacing", 0f);
        putDp(values, "dock_bottom_offset", -2f);
        putDp(values, "workstation_dock_width_offset", 0f);
        putDp(values, "workstation_grid_horizontal_offset", 0f);
        putDp(values, "workstation_dock_icon_top_offset", 0f);
        putDp(values, "workstation_dock_icon_bottom_offset", 0f);
        return Collections.unmodifiableMap(values);
    }

    private static void putDp(SharedPreferences.Editor editor, ConfigKey<Integer> key,
                              float value) {
        float clamped = value;
        if (key.minInt() != null) clamped = Math.max(key.minInt(), clamped);
        if (key.maxInt() != null) clamped = Math.min(key.maxInt(), clamped);
        editor.putInt(key.name(), Math.round(clamped));
        editor.putInt(key.name() + "_tenths", Math.round(clamped * 10f));
    }

    private static void putDp(Map<String, Object> values, String key, float value) {
        values.put(key, Math.round(value));
        values.put(key + "_tenths", Math.round(value * 10f));
    }

    private static int dimenPx(Resources resources, String packageName,
                               String name, float fallbackDp, float density) {
        if (resources != null) {
            int id = resources.getIdentifier(name, "dimen", packageName);
            if (id != 0) {
                try { return resources.getDimensionPixelSize(id); }
                catch (Resources.NotFoundException ignored) {}
            }
        }
        return Math.round(fallbackDp * density);
    }

    public static final class IpadPresetResult {
        public final int spacing;
        public final int heightOffset;
        public final int widthOffset;
        public final int cornerOffset;
        public final int bottomOffset;

        private IpadPresetResult(int spacing, int heightOffset, int widthOffset,
                                 int cornerOffset, int bottomOffset) {
            this.spacing = spacing;
            this.heightOffset = heightOffset;
            this.widthOffset = widthOffset;
            this.cornerOffset = cornerOffset;
            this.bottomOffset = bottomOffset;
        }
    }
}
