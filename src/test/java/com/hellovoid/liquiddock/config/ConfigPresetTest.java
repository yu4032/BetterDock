package com.hellovoid.liquiddock.config;

import android.content.SharedPreferences;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class ConfigPresetTest {
    @Test
    public void defaultPresetKeepsTheComposePersistedValues() {
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("liquiddock_enabled", true);
        expected.put("home_grid_8x4", false);
        expected.put("grid_widget_adaptation", false);
        expected.put("grid_margins_dp", true);
        expected.put("grid_margins_offset", true);
        expected.put("grid_landscape_margin_left", 30);
        expected.put("grid_landscape_margin_left_tenths", 300);
        expected.put("grid_landscape_margin_right", 30);
        expected.put("grid_landscape_margin_right_tenths", 300);
        expected.put("grid_landscape_margin_top", -35);
        expected.put("grid_landscape_margin_top_tenths", -350);
        expected.put("grid_landscape_margin_bottom", 20);
        expected.put("grid_landscape_margin_bottom_tenths", 200);
        expected.put("grid_portrait_margin_left", 0);
        expected.put("grid_portrait_margin_left_tenths", 0);
        expected.put("grid_portrait_margin_right", 0);
        expected.put("grid_portrait_margin_right_tenths", 0);
        expected.put("grid_portrait_margin_top", 0);
        expected.put("grid_portrait_margin_top_tenths", 0);
        expected.put("grid_portrait_margin_bottom", 100);
        expected.put("grid_portrait_margin_bottom_tenths", 1000);
        expected.put("grid_landscape_row_gap", 0);
        expected.put("grid_landscape_row_gap_tenths", 0);
        expected.put("grid_portrait_row_gap", -16);
        expected.put("grid_portrait_row_gap_tenths", -160);
        expected.put("grid_landscape_horizontal_distance", 30);
        expected.put("grid_landscape_horizontal_distance_tenths", 300);
        expected.put("grid_landscape_top_distance", 0);
        expected.put("grid_landscape_top_distance_tenths", 0);
        expected.put("grid_landscape_bottom_distance", 20);
        expected.put("grid_landscape_bottom_distance_tenths", 200);
        expected.put("grid_portrait_horizontal_distance", 0);
        expected.put("grid_portrait_horizontal_distance_tenths", 0);
        expected.put("grid_portrait_top_distance", 10);
        expected.put("grid_portrait_top_distance_tenths", 103);
        expected.put("grid_portrait_bottom_distance", 0);
        expected.put("grid_portrait_bottom_distance_tenths", 0);
        expected.put("indicator_landscape_y", -9);
        expected.put("indicator_landscape_y_tenths", -88);
        expected.put("indicator_portrait_y", 12);
        expected.put("indicator_portrait_y_tenths", 118);
        expected.put("dock_customization", true);
        expected.put("dock_dimensions_dp", true);
        expected.put("dock_resize_animation", false);
        expected.put("dock_smooth_resize_animation", true);
        expected.put("workstation_dock_customization", false);
        expected.put("dock_divider_enabled", false);
        expected.put("blur_radius", 100);

        expected.put("liquid_glass", true);
        expected.put("liquid_dimensions_dp", true);
        expected.put("liquid_blur_mode", "shader");
        expected.put("liquid_ior", 155);
        expected.put("liquid_normal_strength", 115);
        expected.put("liquid_dome", 130);
        expected.put("liquid_chromatic", 2);
        expected.put("liquid_tint_alpha", 35);
        expected.put("liquid_tint_r", 0);
        expected.put("liquid_tint_g", 0);
        expected.put("liquid_tint_b", 255);
        expected.put("liquid_highlight_width", 100);
        expected.put("liquid_highlight_alpha", 100);
        expected.put("liquid_depth_effect", 8);
        expected.put("liquid_brightness", 108);
        expected.put("liquid_specular_sharp", 88);
        expected.put("liquid_specular_strength", 152);
        expected.put("liquid_rim_light", 122);
        expected.put("liquid_caustics", 28);
        expected.put("liquid_edge_band", 32);
        expected.put("liquid_capture_power_limit_fps", 30);
        expected.put("liquid_dynamic_app_capture", true);
        expected.put("liquid_dynamic_app_probe_fps", 3);
        expected.put("liquid_dynamic_motion_threshold", 12);
        expected.put("liquid_dynamic_bit_threshold", 18);
        expected.put("liquid_dynamic_hold_ms", 900);
        expected.put("liquid_black_threshold", 10);
        expected.put("liquid_capture_scale", 100);
        expected.put("liquid_capture_stop_delay", 150);
        expected.put("liquid_blur", 2);
        expected.put("liquid_blur_tenths", 20);
        expected.put("liquid_thickness", 18);
        expected.put("liquid_thickness_tenths", 180);
        expected.put("liquid_lens_refraction", 1);
        expected.put("liquid_lens_refraction_tenths", 13);
        expected.put("liquid_capture_bleed_top", 17);
        expected.put("liquid_capture_bleed_top_tenths", 170);
        expected.put("liquid_capture_bleed_bottom", 16);
        expected.put("liquid_capture_bleed_bottom_tenths", 160);
        expected.put("liquid_recents_prearm_distance", 8);
        expected.put("liquid_recents_prearm_distance_tenths", 80);

        expected.put("liquid_prismal_refraction_inset", 20);
        expected.put("liquid_prismal_refraction_inset_tenths", 200);
        expected.put("liquid_prismal_displacement_scale", 115);
        expected.put("liquid_prismal_height_transition_width", 19);
        expected.put("liquid_prismal_height_transition_width_tenths", 190);
        expected.put("liquid_prismal_smin_smoothing", 2);
        expected.put("liquid_prismal_smin_smoothing_tenths", 18);
        expected.put("liquid_prismal_edge_refraction_falloff", 400);
        expected.put("liquid_prismal_fresnel_reflect", 198);
        expected.put("liquid_prismal_dispersion_r", 100);
        expected.put("liquid_prismal_dispersion_b", 100);
        expected.put("liquid_prismal_vibrancy", 128);
        expected.put("liquid_prismal_plain_highlight", 8);
        expected.put("liquid_prismal_light_dir_x", -50);
        expected.put("liquid_prismal_light_dir_y", -80);
        expected.put("liquid_prismal_shadow_r", 255);
        expected.put("liquid_prismal_shadow_g", 255);
        expected.put("liquid_prismal_shadow_b", 255);
        expected.put("liquid_prismal_shadow_alpha", 35);
        expected.put("liquid_prismal_shadow_softness", 1000);
        expected.put("liquid_prismal_transmittance", 100);
        expected.put("liquid_prismal_backdrop_scale_x", 100);
        expected.put("liquid_prismal_backdrop_scale_y", 100);
        expected.put("liquid_prismal_parallax_scale", 100);
        expected.put("liquid_prismal_show_normals", false);

        expected.put("corners_dp", true);
        expected.put("dock_stroke", true);
        expected.put("stroke_base_r", 180);
        expected.put("stroke_base_g", 180);
        expected.put("stroke_base_b", 180);
        expected.put("stroke_base_alpha", 119);
        expected.put("squircle", true);
        expected.put("sq_outer_cp", 65);
        expected.put("fill_diff", true);
        expected.put("dock_shadow", true);
        expected.put("dock_shadow_alpha", 64);
        expected.put("stroke_shadow", false);
        expected.put("shadow_alpha", 70);
        expected.put("height_offset", 2);
        expected.put("height_offset_tenths", 22);
        expected.put("width_offset", 0);
        expected.put("width_offset_tenths", 0);
        expected.put("corner_offset", 1);
        expected.put("corner_offset_tenths", 10);
        expected.put("blur_corner_offset", -1);
        expected.put("blur_corner_offset_tenths", -10);
        expected.put("sq_stroke_w", 1);
        expected.put("sq_stroke_w_tenths", 10);
        expected.put("sq_stroke_off", 0);
        expected.put("sq_stroke_off_tenths", 0);
        expected.put("stroke_w", 1);
        expected.put("stroke_w_tenths", 10);
        expected.put("std_stroke_w", 1);
        expected.put("std_stroke_w_tenths", 10);
        expected.put("dock_shadow_radius", 10);
        expected.put("dock_shadow_radius_tenths", 100);
        expected.put("dock_shadow_size", 5);
        expected.put("dock_shadow_size_tenths", 47);
        expected.put("dock_shadow_y", 0);
        expected.put("dock_shadow_y_tenths", 0);
        expected.put("shadow_radius", 3);
        expected.put("shadow_radius_tenths", 30);
        expected.put("dock_spacing", 0);
        expected.put("dock_spacing_tenths", 0);
        expected.put("dock_bottom_offset", -2);
        expected.put("dock_bottom_offset_tenths", -20);
        expected.put("workstation_dock_width_offset", 0);
        expected.put("workstation_dock_width_offset_tenths", 0);
        expected.put("workstation_grid_horizontal_offset", 0);
        expected.put("workstation_grid_horizontal_offset_tenths", 0);
        expected.put("workstation_dock_icon_top_offset", 0);
        expected.put("workstation_dock_icon_top_offset_tenths", 0);
        expected.put("workstation_dock_icon_bottom_offset", 0);
        expected.put("workstation_dock_icon_bottom_offset_tenths", 0);

        assertEquals(expected, PresetManager.defaultValues());
        assertEquals(Boolean.TRUE, PresetManager.defaultValues().get("liquiddock_enabled"));
        assertEquals(Boolean.FALSE, PresetManager.defaultValues().get("home_grid_8x4"));
        assertEquals(Boolean.FALSE, PresetManager.defaultValues().get("grid_widget_adaptation"));
        assertEquals(Integer.valueOf(155), PresetManager.defaultValues().get("liquid_ior"));
        assertEquals(Integer.valueOf(130), PresetManager.defaultValues().get("liquid_dome"));
        assertEquals(Integer.valueOf(198), PresetManager.defaultValues().get("liquid_prismal_fresnel_reflect"));
        assertEquals(Integer.valueOf(30), PresetManager.defaultValues().get("liquid_capture_power_limit_fps"));
        assertEquals(Integer.valueOf(100), PresetManager.defaultValues().get("liquid_capture_scale"));
        assertEquals(Integer.valueOf(-88), PresetManager.defaultValues().get("indicator_landscape_y_tenths"));
        assertEquals(Integer.valueOf(118), PresetManager.defaultValues().get("indicator_portrait_y_tenths"));
    }

    @Test
    public void defaultPresetWritesEveryValueAndCommits() {
        RecordingEditor editor = new RecordingEditor();

        PresetManager.applyDefault(editor);

        assertEquals(PresetManager.defaultValues(), editor.values);
        assertEquals(true, editor.committed);
    }

    private static final class RecordingEditor implements SharedPreferences.Editor {
        final Map<String, Object> values = new LinkedHashMap<>();
        boolean committed;

        @Override public SharedPreferences.Editor putString(String key, String value) {
            values.put(key, value);
            return this;
        }

        @Override public SharedPreferences.Editor putStringSet(String key, Set<String> values) {
            this.values.put(key, values);
            return this;
        }

        @Override public SharedPreferences.Editor putInt(String key, int value) {
            values.put(key, value);
            return this;
        }

        @Override public SharedPreferences.Editor putLong(String key, long value) {
            values.put(key, value);
            return this;
        }

        @Override public SharedPreferences.Editor putFloat(String key, float value) {
            values.put(key, value);
            return this;
        }

        @Override public SharedPreferences.Editor putBoolean(String key, boolean value) {
            values.put(key, value);
            return this;
        }

        @Override public SharedPreferences.Editor remove(String key) {
            values.remove(key);
            return this;
        }

        @Override public SharedPreferences.Editor clear() {
            values.clear();
            return this;
        }

        @Override public boolean commit() {
            committed = true;
            return true;
        }

        @Override public void apply() {}
    }
}
