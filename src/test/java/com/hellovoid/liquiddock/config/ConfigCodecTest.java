package com.hellovoid.liquiddock.config;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfigCodecTest {
    @Test
    public void legacyTenthsRoundTripKeepsExactDecimal() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("width_offset", 7);
        prefs.put("width_offset_tenths", 73);

        Map<String, Object> exported = ConfigCodec.exportValues(prefs);
        assertEquals(7.3d, ((Number) exported.get("width_offset")).doubleValue(), 0.0001d);

        Map<String, Object> imported = ConfigCodec.importValues(exported);
        assertEquals(7, imported.get("width_offset"));
        assertEquals(73, imported.get("width_offset_tenths"));
    }

    @Test
    public void importRoundsTenthsAndPreservesRawCompanion() {
        Map<String, Object> json = new HashMap<>();
        json.put("height_offset", 1.25d);

        Map<String, Object> imported = ConfigCodec.importValues(json);
        assertEquals(1, imported.get("height_offset"));
        assertEquals(13, imported.get("height_offset_tenths"));
    }

    @Test
    public void directIntegerRoundTripDoesNotCreateTenthsCompanion() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("blur_radius", 137);

        Map<String, Object> exported = ConfigCodec.exportValues(prefs);
        assertEquals(137, exported.get("blur_radius"));

        Map<String, Object> imported = ConfigCodec.importValues(exported);
        assertEquals(137, imported.get("blur_radius"));
        assertFalse(imported.containsKey("blur_radius_tenths"));
    }

    @Test
    public void booleanRoundTrip() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("liquid_glass", true);

        Map<String, Object> exported = ConfigCodec.exportValues(prefs);
        assertEquals(Boolean.TRUE, exported.get("liquid_glass"));

        Map<String, Object> imported = ConfigCodec.importValues(exported);
        assertEquals(Boolean.TRUE, imported.get("liquid_glass"));
    }

    @Test
    public void stringRoundTrip() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("liquid_blur_mode", "advanced_material");

        Map<String, Object> exported = ConfigCodec.exportValues(prefs);
        assertEquals("advanced_material", exported.get("liquid_blur_mode"));

        Map<String, Object> imported = ConfigCodec.importValues(exported);
        assertEquals("advanced_material", imported.get("liquid_blur_mode"));
    }

    @Test
    public void importClampsDirectInteger() {
        Map<String, Object> json = new HashMap<>();
        json.put("blur_radius", 9999);
        Map<String, Object> imported = ConfigCodec.importValues(json);
        assertEquals(400, imported.get("blur_radius"));
    }

    @Test
    public void importClampsDpTenthsInDisplayUnits() {
        Map<String, Object> json = new HashMap<>();
        json.put("width_offset", 999d);
        Map<String, Object> imported = ConfigCodec.importValues(json);
        assertEquals(80, imported.get("width_offset"));
        assertEquals(800, imported.get("width_offset_tenths"));
    }

    @Test
    public void unknownKeysAreIgnored() {
        Map<String, Object> json = new HashMap<>();
        json.put("totally_unknown_key", 123);
        Map<String, Object> imported = ConfigCodec.importValues(json);
        assertFalse(imported.containsKey("totally_unknown_key"));
    }

    @Test
    public void neverExportedKeysStayOut() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("liquiddock_debug_log", true);
        prefs.put("grid_margin_left", 200);
        prefs.put("liquid_capture_fullscreen", false);
        Map<String, Object> exported = ConfigCodec.exportValues(prefs);
        assertFalse(exported.containsKey("liquiddock_debug_log"));
        assertFalse(exported.containsKey("grid_margin_left"));
        assertFalse(exported.containsKey("liquid_capture_fullscreen"));
    }

    @Test
    public void optionalDividerKeysAreNotExportedWhenAbsent() {
        Map<String, Object> exported = ConfigCodec.exportValues(new HashMap<>());
        assertFalse(exported.containsKey("dock_divider_enabled"));
        assertFalse(exported.containsKey("dock_divider_width_dp"));
    }

    @Test
    public void optionalDividerKeysExportWhenPresent() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("dock_divider_enabled", true);
        prefs.put("dock_divider_width_dp", 14);
        Map<String, Object> exported = ConfigCodec.exportValues(prefs);
        assertEquals(Boolean.TRUE, exported.get("dock_divider_enabled"));
        assertEquals(14, exported.get("dock_divider_width_dp"));
    }

    @Test
    public void dpTenthsExportUsesExportDefaultWhenAbsent() {
        Map<String, Object> exported = ConfigCodec.exportValues(new HashMap<>());
        assertEquals(48, exported.get("liquid_capture_bleed_top"));
        assertEquals(16, exported.get("liquid_capture_bleed_bottom"));
    }

    @Test
    public void dpTenthsImportUsesDecimalCompanion() {
        Map<String, Object> json = new HashMap<>();
        json.put("liquid_capture_bleed_top", 17.5d);
        Map<String, Object> imported = ConfigCodec.importValues(json);
        assertEquals(18, imported.get("liquid_capture_bleed_top"));
        assertEquals(175, imported.get("liquid_capture_bleed_top_tenths"));
    }

    @Test
    public void homeSettleHistoricalTenthsRoundTripIsPreserved() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("liquid_home_settle_delay", 1201);
        prefs.put("liquid_home_settle_delay_tenths", 12005);

        Map<String, Object> exported = ConfigCodec.exportValues(prefs);
        assertEquals(1200.5d,
                ((Number) exported.get("liquid_home_settle_delay")).doubleValue(), 0.0001d);

        Map<String, Object> imported = ConfigCodec.importValues(exported);
        assertEquals(1201, imported.get("liquid_home_settle_delay"));
        assertEquals(12005, imported.get("liquid_home_settle_delay_tenths"));
    }

    @Test
    public void dimensionModeExportsStayForcedTrueWhenSourceExplicitlyFalse() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("dock_dimensions_dp", false);
        prefs.put("liquid_dimensions_dp", false);

        Map<String, Object> exported = ConfigCodec.exportValues(prefs);

        assertEquals(Boolean.TRUE, exported.get("dock_dimensions_dp"));
        assertEquals(Boolean.TRUE, exported.get("liquid_dimensions_dp"));
    }

    @Test
    public void absentPreferencesExportCompleteHistoricalDefaults() {
        Map<String, Object> exported = ConfigCodec.exportValues(new HashMap<>());

        assertEquals(126, exported.size());
        assertEquals(Boolean.TRUE, exported.get("liquiddock_enabled"));
        assertEquals(Boolean.FALSE, exported.get("home_grid_8x4"));
        assertEquals(Boolean.FALSE, exported.get("grid_widget_adaptation"));
        assertEquals(Boolean.TRUE, exported.get("grid_margins_dp"));
        assertEquals(100, exported.get("blur_radius"));
        assertEquals(Boolean.TRUE, exported.get("dock_dimensions_dp"));
        assertEquals(4, exported.get("sq_stroke_w"));
        assertEquals(42, exported.get("dock_shadow_radius"));
        assertEquals(Boolean.FALSE, exported.get("liquid_glass"));
        assertEquals(Boolean.TRUE, exported.get("liquid_dimensions_dp"));
        assertEquals(48, exported.get("liquid_capture_bleed_top"));
        assertEquals(1200, exported.get("liquid_home_settle_delay"));
        assertEquals(5, exported.get("liquid_prismal_refraction_inset"));
        assertEquals(79, exported.get("liquid_prismal_fresnel_reflect"));
        assertEquals(100, exported.get("liquid_prismal_transmittance"));
        assertEquals(Boolean.FALSE, exported.get("liquid_prismal_show_normals"));
        assertEquals(Boolean.FALSE, exported.get("workstation_dock_customization"));
        assertEquals(0, exported.get("workstation_all_apps_landscape_top_spacing"));
        assertEquals(0, exported.get("workstation_all_apps_landscape_bottom_spacing"));
        assertEquals(0, exported.get("workstation_all_apps_portrait_top_spacing"));
        assertEquals(0, exported.get("workstation_all_apps_portrait_bottom_spacing"));
        assertFalse(exported.containsKey("dock_divider_enabled"));
        assertFalse(exported.containsKey("dock_divider_width_dp"));
    }

    @Test
    public void exportImportOfPrismalControlsPreservesTypedValues() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("liquid_prismal_displacement_scale", 135);
        prefs.put("liquid_prismal_refraction_inset", 7);
        prefs.put("liquid_prismal_refraction_inset_tenths", 72);
        prefs.put("liquid_prismal_show_normals", true);

        Map<String, Object> exported = ConfigCodec.exportValues(prefs);
        assertEquals(135, exported.get("liquid_prismal_displacement_scale"));
        assertEquals(7.2d,
                ((Number) exported.get("liquid_prismal_refraction_inset")).doubleValue(), 0.0001d);
        assertEquals(Boolean.TRUE, exported.get("liquid_prismal_show_normals"));

        Map<String, Object> imported = ConfigCodec.importValues(exported);
        assertEquals(135, imported.get("liquid_prismal_displacement_scale"));
        assertEquals(7, imported.get("liquid_prismal_refraction_inset"));
        assertEquals(72, imported.get("liquid_prismal_refraction_inset_tenths"));
        assertEquals(Boolean.TRUE, imported.get("liquid_prismal_show_normals"));
    }
}
