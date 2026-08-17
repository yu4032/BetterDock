package com.hellovoid.liquiddock.config;

import org.junit.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ConfigCodecTest {
    @Test
    public void representativeExportPreservesCurrentFieldsAndAddsGridProfile() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("liquiddock_enabled", false);
        prefs.put("home_grid_extended", true);
        prefs.put("grid_profile", "10x6");
        prefs.put("home_grid_8x4", true); // legacy key must not leak back into exports
        prefs.put("grid_widget_adaptation", true);
        prefs.put("grid_landscape_horizontal_distance", -3);
        prefs.put("grid_landscape_horizontal_distance_tenths", -27);
        prefs.put("indicator_landscape_y", -8);
        prefs.put("indicator_landscape_y_tenths", -77);
        prefs.put("dock_customization", false);
        prefs.put("width_offset", 12);
        prefs.put("width_offset_tenths", 125);
        prefs.put("sq_stroke_w", 3);
        prefs.put("sq_stroke_w_tenths", 25);
        prefs.put("dock_shadow_alpha", 130);
        prefs.put("dock_divider_enabled", true);
        prefs.put("dock_divider_width_dp", 8);
        prefs.put("dock_divider_width_dp_tenths", 83);
        prefs.put("workstation_dock_customization", true);
        prefs.put("workstation_all_apps_landscape_vertical_offset", -4);
        prefs.put("workstation_all_apps_landscape_vertical_offset_tenths", -36);
        prefs.put("liquid_glass", true);
        prefs.put("liquid_blur", 7);
        prefs.put("liquid_blur_tenths", 73);
        prefs.put("liquid_ior", 170);

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("liquiddock_enabled", false);
        expected.put("home_grid_extended", true);
        expected.put("grid_profile", "10x6");
        expected.put("grid_widget_adaptation", true);
        expected.put("grid_landscape_horizontal_distance", -2.7d);
        expected.put("indicator_landscape_y", -7.7d);
        expected.put("dock_customization", false);
        expected.put("width_offset", 12.5d);
        expected.put("sq_stroke_w", 2.5d);
        expected.put("dock_shadow_alpha", 130);
        expected.put("dock_divider_enabled", true);
        expected.put("dock_divider_width_dp", 8);
        expected.put("workstation_dock_customization", true);
        expected.put("workstation_all_apps_landscape_vertical_offset", -3.6d);
        expected.put("liquid_glass", true);
        expected.put("liquid_blur", 7.3d);
        expected.put("liquid_ior", 170);
        expected.put("dock_dimensions_dp", true);
        expected.put("liquid_dimensions_dp", true);

        Map<String, Object> exported = ConfigCodec.exportValues(prefs);
        for (Map.Entry<String, Object> entry : expected.entrySet()) {
            assertEquals(entry.getValue(), exported.get(entry.getKey()));
        }
        assertFalse(exported.containsKey("home_grid_8x4"));
        assertFalse(exported.containsKey("dock_divider_height_scale"));
        assertFalse(exported.containsKey("dock_divider_y_offset"));
        assertFalse(exported.containsKey("dock_divider_color_r"));
        assertFalse(exported.containsKey("dock_divider_color_g"));
        assertFalse(exported.containsKey("dock_divider_color_b"));
        assertFalse(exported.containsKey("dock_divider_alpha"));
    }

    @Test
    public void representativeLegacyImportProducesExactTypedWrites() {
        Map<String, Object> json = new HashMap<>();
        json.put("grid_margins_dp", true);
        json.put("grid_margins_offset", true);
        json.put("grid_widget_adaptation", true);
        json.put("grid_landscape_margin_horizontal", 41);
        json.put("grid_portrait_margin_horizontal", -12);
        json.put("liquid_capture_power_limit_fps", 100);
        json.put("dock_shadow_alpha", -3);
        json.put("dock_divider_width_dp", 999);
        json.put("workstation_all_apps_horizontal_offset", 2.6d);
        json.put("workstation_all_apps_vertical_offset", -3.4d);

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("grid_margins_dp", true);
        expected.put("grid_margins_offset", true);
        expected.put("grid_widget_adaptation", true);
        expected.put("liquid_capture_power_limit_fps", 60);
        expected.put("dock_shadow_alpha", 0);
        expected.put("dock_divider_width_dp", 160);
        expected.put("grid_landscape_margin_left", 41);
        expected.put("grid_landscape_margin_right", 41);
        expected.put("grid_portrait_margin_left", -12);
        expected.put("grid_portrait_margin_right", -12);
        expected.put("workstation_all_apps_horizontal_offset", 3);
        expected.put("workstation_all_apps_horizontal_offset_tenths", 26);
        expected.put("workstation_all_apps_vertical_offset", -3);
        expected.put("workstation_all_apps_vertical_offset_tenths", -34);

        assertEquals(expected, ConfigCodec.importValues(json));
    }

    @Test
    public void legacyGridBooleanImportsAsMasterSwitchAndEightByFourProfile() {
        Map<String, Object> json = new HashMap<>();
        json.put("home_grid_8x4", true);

        Map<String, Object> imported = ConfigCodec.importValues(json);

        assertEquals(Boolean.TRUE, imported.get("home_grid_extended"));
        assertEquals("8x4", imported.get("grid_profile"));
        assertFalse(imported.containsKey("home_grid_8x4"));
    }

    @Test
    public void canonicalGridImportWinsOverLegacyBoolean() {
        Map<String, Object> json = new HashMap<>();
        json.put("home_grid_extended", false);
        json.put("grid_profile", "10x6");
        json.put("home_grid_8x4", true);

        Map<String, Object> imported = ConfigCodec.importValues(json);

        assertEquals(Boolean.FALSE, imported.get("home_grid_extended"));
        assertEquals("10x6", imported.get("grid_profile"));
        assertFalse(imported.containsKey("home_grid_8x4"));
    }

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
    public void dividerTenthsEncodedIntegersClampWithoutCreatingDpSidecars() {
        Map<String, Object> json = new HashMap<>();
        json.put("dock_divider_width_dp", 999);
        json.put("dock_divider_y_offset", -999);

        Map<String, Object> imported = ConfigCodec.importValues(json);

        assertEquals(160, imported.get("dock_divider_width_dp"));
        assertEquals(-80, imported.get("dock_divider_y_offset"));
        assertFalse(imported.containsKey("dock_divider_width_dp_tenths"));
        assertFalse(imported.containsKey("dock_divider_y_offset_tenths"));
    }

    @Test
    public void dividerExportIgnoresUnhistoricalDpSidecars() {
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
    public void absentPreferencesExportCompleteCurrentDefaults() {
        Map<String, Object> exported = ConfigCodec.exportValues(new HashMap<>());

        assertEquals(105, exported.size());
        assertEquals(Boolean.TRUE, exported.get("liquiddock_enabled"));
        assertEquals(Boolean.FALSE, exported.get("home_grid_extended"));
        assertEquals("8x4", exported.get("grid_profile"));
        assertFalse(exported.containsKey("home_grid_8x4"));
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
