package com.hellovoid.liquiddock.config;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ConfigCodecTest {
    @Test
    public void exportUsesHistoricalExportDefaultsInsteadOfUiDefaults() {
        Map<String, Object> exported = ConfigCodec.exportValues(new HashMap<>());

        assertEquals(4, exported.get("sq_stroke_w"));
        assertEquals(8, exported.get("sq_stroke_off"));
        assertEquals(42, exported.get("dock_shadow_radius"));
        assertEquals(52, exported.get("dock_shadow_size"));
        assertEquals(12, exported.get("dock_shadow_y"));
        assertEquals(48, exported.get("liquid_capture_bleed_top"));
        assertEquals(16, exported.get("liquid_capture_bleed_bottom"));
    }

    @Test
    public void exportUsesTenthsSidecarsAndForcesDimensionModes() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("width_offset", 2);
        prefs.put("width_offset_tenths", 25);
        prefs.put("dock_dimensions_dp", false);
        prefs.put("liquid_dimensions_dp", false);

        Map<String, Object> exported = ConfigCodec.exportValues(prefs);

        assertEquals(2.5d, ((Number) exported.get("width_offset")).doubleValue(), 0.0001d);
        assertEquals(Boolean.TRUE, exported.get("dock_dimensions_dp"));
        assertEquals(Boolean.TRUE, exported.get("liquid_dimensions_dp"));
    }

    @Test
    public void exportSkipsConditionalKeysWhenAbsent() {
        Map<String, Object> exported = ConfigCodec.exportValues(new HashMap<>());

        assertFalse(exported.containsKey("dock_divider_enabled"));
        assertFalse(exported.containsKey("dock_divider_width_dp"));
    }

    @Test
    public void importClampsAndStoresDpTenths() {
        Map<String, Object> json = new HashMap<>();
        json.put("width_offset", 81.25d);
        json.put("dock_divider_width_dp", 16.05d);

        Map<String, Object> imported = ConfigCodec.importValues(json);

        assertEquals(80, imported.get("width_offset"));
        assertEquals(800, imported.get("width_offset_tenths"));
        assertEquals(16, imported.get("dock_divider_width_dp"));
        assertEquals(161, imported.get("dock_divider_width_dp_tenths"));
    }

    @Test
    public void importUnknownKeysAreIgnored() {
        Map<String, Object> json = new HashMap<>();
        json.put("totally_unknown", 123);
        json.put("blur_radius", 222);

        Map<String, Object> imported = ConfigCodec.importValues(json);

        assertFalse(imported.containsKey("totally_unknown"));
        assertEquals(222, imported.get("blur_radius"));
    }

    @Test
    public void exportPreservesStringBlurMode() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("liquid_blur_mode", "advanced_material");

        Map<String, Object> exported = ConfigCodec.exportValues(prefs);

        assertEquals("advanced_material", exported.get("liquid_blur_mode"));
    }

    @Test
    public void importPreservesStringBlurMode() {
        Map<String, Object> json = new HashMap<>();
        json.put("liquid_blur_mode", "advanced_material");

        Map<String, Object> imported = ConfigCodec.importValues(json);

        assertEquals("advanced_material", imported.get("liquid_blur_mode"));
    }

    @Test
    public void legacyPerEdgeMarginsMapIntoAxisDistanceKeys() {
        Map<String, Object> json = new HashMap<>();
        json.put("grid_landscape_margin_left", 20.0d);
        json.put("grid_landscape_margin_right", 40.0d);
        json.put("grid_landscape_margin_top", 10.0d);
        json.put("grid_landscape_margin_bottom", 30.0d);
        json.put("grid_portrait_margin_left", 12.0d);
        json.put("grid_portrait_margin_right", 28.0d);
        json.put("grid_portrait_margin_top", 14.0d);
        json.put("grid_portrait_margin_bottom", 34.0d);

        Map<String, Object> imported = ConfigCodec.importValues(json);

        assertEquals(30, imported.get("grid_landscape_horizontal_distance"));
        assertEquals(300, imported.get("grid_landscape_horizontal_distance_tenths"));
        assertEquals(10, imported.get("grid_landscape_top_distance"));
        assertEquals(30, imported.get("grid_landscape_bottom_distance"));
        assertEquals(20, imported.get("grid_portrait_horizontal_distance"));
        assertEquals(14, imported.get("grid_portrait_top_distance"));
        assertEquals(34, imported.get("grid_portrait_bottom_distance"));
    }

    @Test
    public void currentAxisDistanceKeysWinOverLegacyPerEdgeMargins() {
        Map<String, Object> json = new HashMap<>();
        json.put("grid_landscape_horizontal_distance", 7.5d);
        json.put("grid_landscape_margin_left", 20.0d);
        json.put("grid_landscape_margin_right", 40.0d);

        Map<String, Object> imported = ConfigCodec.importValues(json);

        assertEquals(8, imported.get("grid_landscape_horizontal_distance"));
        assertEquals(75, imported.get("grid_landscape_horizontal_distance_tenths"));
    }

    @Test
    public void dividerLegacyTenthsRoundTripRemainsDirect() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("dock_divider_width_dp", 11);
        prefs.put("dock_divider_width_dp_tenths", 999);
        prefs.put("dock_divider_y_offset", -7);
        prefs.put("dock_divider_y_offset_tenths", -999);

        Map<String, Object> exported = ConfigCodec.exportValues(prefs);

        assertEquals(11, exported.get("dock_divider_width_dp"));
        assertEquals(-7, exported.get("dock_divider_y_offset"));
    }

    @Test
    public void homeSettleDelayPreservesHistoricalTenthsRoundTrip() {
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

        assertEquals(104, exported.size());
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
        assertEquals(Boolean.FALSE, exported.get("workstation_dock_customization"));
        assertEquals(0, exported.get("workstation_all_apps_landscape_top_spacing"));
        assertEquals(0, exported.get("workstation_all_apps_landscape_bottom_spacing"));
        assertEquals(0, exported.get("workstation_all_apps_portrait_top_spacing"));
        assertEquals(0, exported.get("workstation_all_apps_portrait_bottom_spacing"));
        assertFalse(exported.containsKey("dock_divider_enabled"));
        assertFalse(exported.containsKey("dock_divider_width_dp"));
    }

    @Test
    public void legacyHorizontalMarginsPopulateBothEdges() {
        Map<String, Object> json = new HashMap<>();
        json.put("grid_landscape_margin_horizontal", 41);
        json.put("grid_portrait_margin_horizontal", 27);

        Map<String, Object> imported = ConfigCodec.importValues(json);

        assertEquals(41, imported.get("grid_landscape_horizontal_distance"));
        assertEquals(27, imported.get("grid_portrait_horizontal_distance"));
    }
}
