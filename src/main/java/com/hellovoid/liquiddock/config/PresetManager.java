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
        float density = dm.density;
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
        int oneDp = Math.max(1, Math.round(density * displayScale));
        int bottomOffset = Math.round(10f * density * displayScale);

        preferences.edit()
                .putInt("blur_radius", 100)
                .putInt("height_offset", heightOffset)
                .putInt("width_offset", widthOffset)
                .putBoolean("corners_dp", true)
                .putInt("corner_offset", Math.round(cornerOffset / density))
                .putInt("blur_corner_offset", -1)
                .putBoolean("home_grid_8x4", true)
                .putBoolean("grid_margins_dp", true)
                .putBoolean("grid_margins_offset", true)
                .putInt("grid_landscape_margin_left", 0)
                .putInt("grid_landscape_margin_right", 0)
                .putInt("grid_landscape_margin_top", 0)
                .putInt("grid_landscape_margin_bottom", 0)
                .putInt("grid_portrait_margin_left", 0)
                .putInt("grid_portrait_margin_right", 0)
                .putInt("grid_portrait_margin_top", 0)
                .putInt("grid_portrait_margin_bottom", 0)
                .putInt("grid_landscape_row_gap", 0)
                .putInt("grid_portrait_row_gap", 0)
                .putInt("indicator_landscape_y", 0)
                .putInt("indicator_portrait_y", 0)
                .putBoolean("dock_customization", true)
                .putBoolean("dock_stroke", true)
                .putInt("stroke_base_r", 255)
                .putInt("stroke_base_g", 255)
                .putInt("stroke_base_b", 255)
                .putInt("stroke_base_alpha", 255)
                .putBoolean("squircle", true)
                .putInt("sq_stroke_w", oneDp)
                .putInt("sq_stroke_off", 0)
                .putInt("sq_outer_cp", 65)
                .putBoolean("fill_diff", true)
                .putInt("stroke_w", oneDp)
                .putInt("std_stroke_w", oneDp)
                .putBoolean("dock_shadow", true)
                .putInt("dock_shadow_radius", Math.round(10f * density * displayScale))
                .putInt("dock_shadow_size", Math.round(13f * density * displayScale))
                .putInt("dock_shadow_alpha", 140)
                .putInt("dock_shadow_y", Math.round(3f * density * displayScale))
                .putBoolean("stroke_shadow", false)
                .putInt("shadow_radius", Math.round(3f * density * displayScale))
                .putInt("shadow_alpha", 70)
                .putInt("dock_spacing", spacing)
                .putInt("dock_bottom_offset", bottomOffset)
                .commit();

        return new IpadPresetResult(spacing, heightOffset, widthOffset, cornerOffset, bottomOffset);
    }

    private static Map<String, Object> createDefaultValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("liquiddock_enabled", true);
        values.put("home_grid_extended", false);
        values.put("grid_profile", "8x4");
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

        // Prismal Quick Start optics with LiquidDock's intentional chromatic-strength override.
        // Keep this preset synchronized with the zero-copy material defaults.
        values.put("liquid_glass", true);
        values.put("liquid_dimensions_dp", true);
        values.put("liquid_blur_mode", "shader");
        values.put("liquid_ior", 155);
        values.put("liquid_normal_strength", 115);
        values.put("liquid_dome", 130);
        values.put("liquid_chromatic", 2);
        values.put("liquid_tint_alpha", 35);
        values.put("liquid_tint_r", 0);
        values.put("liquid_tint_g", 0);
        values.put("liquid_tint_b", 255);
        values.put("liquid_highlight_width", 100);
        values.put("liquid_highlight_alpha", 100);
        values.put("liquid_depth_effect", 8);
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
