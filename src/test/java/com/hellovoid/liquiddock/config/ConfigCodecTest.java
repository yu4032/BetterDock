package com.hellovoid.liquiddock.config;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ConfigCodecTest {
    @Test
    public void widgetAdaptationRoundTrips() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("grid_widget_adaptation", true);

        Map<String, Object> exported = ConfigCodec.exportValues(prefs);
        assertEquals(Boolean.TRUE, exported.get("grid_widget_adaptation"));

        Map<String, Object> imported = ConfigCodec.importValues(exported);
        assertEquals(Boolean.TRUE, imported.get("grid_widget_adaptation"));
    }

    @Test
    public void decimalDpExportPrefersTenths() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("indicator_landscape_y", -9);
        prefs.put("indicator_landscape_y_tenths", -88);

        assertEquals(-8.8d,
                ((Number) ConfigCodec.exportValues(prefs).get("indicator_landscape_y")).doubleValue(),
                0.0001d);
    }

    @Test
    public void importClampsIntegersToExistingRanges() {
        Map<String, Object> json = new HashMap<>();
        json.put("liquid_capture_power_limit_fps", 100);
        json.put("dock_shadow_alpha", -3);

        Map<String, Object> imported = ConfigCodec.importValues(json);

        assertEquals(60, imported.get("liquid_capture_power_limit_fps"));
        assertEquals(0, imported.get("dock_shadow_alpha"));
    }

    @Test
    public void legacyHorizontalMarginsPopulateBothEdges() {
        Map<String, Object> json = new HashMap<>();
        json.put("grid_landscape_margin_horizontal", 41);
        json.put("grid_portrait_margin_horizontal", -12);

        Map<String, Object> imported = ConfigCodec.importValues(json);

        assertEquals(41, imported.get("grid_landscape_margin_left"));
        assertEquals(41, imported.get("grid_landscape_margin_right"));
        assertEquals(-12, imported.get("grid_portrait_margin_left"));
        assertEquals(-12, imported.get("grid_portrait_margin_right"));
    }

    @Test
    public void preAxisLegacyMarginsConvertWhenModernLandscapeMarginsAreAbsent() {
        Map<String, Object> json = new HashMap<>();
        json.put("grid_margin_left", 450);
        json.put("grid_margin_right", 20);
        json.put("grid_margin_top", 30);
        json.put("grid_margin_bottom", -4);

        Map<String, Object> imported = ConfigCodec.importValues(json);

        assertEquals(400, imported.get("grid_landscape_margin_left"));
        assertEquals(20, imported.get("grid_landscape_margin_right"));
        assertEquals(30, imported.get("grid_landscape_margin_top"));
        assertEquals(0, imported.get("grid_landscape_margin_bottom"));
        assertEquals(30, imported.get("grid_portrait_margin_left"));
        assertEquals(0, imported.get("grid_portrait_margin_right"));
        assertEquals(20, imported.get("grid_portrait_margin_top"));
        assertEquals(400, imported.get("grid_portrait_margin_bottom"));
    }

    @Test
    public void legacyWorkstationAllAppsOffsetsRemainImportable() {
        Map<String, Object> json = new HashMap<>();
        json.put("workstation_all_apps_horizontal_offset", 2.6d);
        json.put("workstation_all_apps_vertical_offset", -3.4d);

        Map<String, Object> imported = ConfigCodec.importValues(json);

        assertEquals(3, imported.get("workstation_all_apps_horizontal_offset"));
        assertEquals(26, imported.get("workstation_all_apps_horizontal_offset_tenths"));
        assertEquals(-3, imported.get("workstation_all_apps_vertical_offset"));
        assertEquals(-34, imported.get("workstation_all_apps_vertical_offset_tenths"));
    }

    @Test
    public void absentOptionalDividerValuesAreNotSynthesized() {
        Map<String, Object> empty = new HashMap<>();

        Map<String, Object> exported = ConfigCodec.exportValues(empty);
        Map<String, Object> imported = ConfigCodec.importValues(empty);

        assertFalse(exported.containsKey("dock_divider_enabled"));
        assertFalse(exported.containsKey("dock_divider_width_dp"));
        assertFalse(imported.containsKey("dock_divider_enabled"));
        assertFalse(imported.containsKey("dock_divider_width_dp"));
    }
}
