package com.hellovoid.liquiddock.config;

import android.content.SharedPreferences;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConfigPresetTest {
    @Test
    public void defaultPresetKeepsLayoutDefaultsAndUsesOfficialPrismalOptics() {
        Map<String, Object> values = PresetManager.defaultValues();

        // Unrelated layout/dock defaults remain stable.
        assertEquals(Boolean.TRUE, values.get("liquiddock_enabled"));
        assertEquals(Boolean.FALSE, values.get("home_grid_8x4"));
        assertEquals(Boolean.FALSE, values.get("grid_widget_adaptation"));
        assertEquals(Integer.valueOf(300), values.get("grid_landscape_horizontal_distance_tenths"));
        assertEquals(Integer.valueOf(-88), values.get("indicator_landscape_y_tenths"));
        assertEquals(Integer.valueOf(118), values.get("indicator_portrait_y_tenths"));
        assertEquals(Integer.valueOf(30), values.get("liquid_capture_power_limit_fps"));
        assertEquals(Integer.valueOf(100), values.get("liquid_capture_scale"));

        // Effective Prismal Quick Start: FrameLayout defaults followed by applyBase().
        assertEquals(Integer.valueOf(155), values.get("liquid_ior"));
        assertEquals(Integer.valueOf(115), values.get("liquid_normal_strength"));
        assertEquals(Integer.valueOf(130), values.get("liquid_dome"));
        assertEquals(Integer.valueOf(26), values.get("liquid_chromatic"));
        assertEquals(Integer.valueOf(2), values.get("liquid_blur"));
        assertEquals(Integer.valueOf(20), values.get("liquid_blur_tenths"));
        assertEquals(Integer.valueOf(13), values.get("liquid_lens_refraction_tenths"));
        assertEquals(Integer.valueOf(152), values.get("liquid_specular_strength"));
        assertEquals(Integer.valueOf(122), values.get("liquid_rim_light"));
        assertEquals(Integer.valueOf(0), values.get("liquid_tint_r"));
        assertEquals(Integer.valueOf(0), values.get("liquid_tint_g"));
        assertEquals(Integer.valueOf(255), values.get("liquid_tint_b"));
        assertEquals(Integer.valueOf(35), values.get("liquid_tint_alpha"));

        assertEquals(Integer.valueOf(200), values.get("liquid_prismal_refraction_inset_tenths"));
        assertEquals(Integer.valueOf(115), values.get("liquid_prismal_displacement_scale"));
        assertEquals(Integer.valueOf(190), values.get("liquid_prismal_height_transition_width_tenths"));
        assertEquals(Integer.valueOf(18), values.get("liquid_prismal_smin_smoothing_tenths"));
        assertEquals(Integer.valueOf(400), values.get("liquid_prismal_edge_refraction_falloff"));
        assertEquals(Integer.valueOf(198), values.get("liquid_prismal_fresnel_reflect"));
        assertEquals(Integer.valueOf(1000), values.get("liquid_prismal_shadow_softness"));
        assertEquals(Boolean.FALSE, values.get("liquid_prismal_show_normals"));

        // Precision-sensitive dp controls must always carry their sidecar representation.
        assertTrue(values.containsKey("liquid_lens_refraction_tenths"));
        assertTrue(values.containsKey("liquid_prismal_smin_smoothing_tenths"));
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
