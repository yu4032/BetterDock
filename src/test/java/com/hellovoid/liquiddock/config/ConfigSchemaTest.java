package com.hellovoid.liquiddock.config;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConfigSchemaTest {
    private static void assertComposeIntSpec(ConfigKey<Integer> key, int uiDefault,
                                             int min, int max) {
        assertEquals(key.name(), Integer.valueOf(uiDefault), key.uiDefault());
        assertEquals(key.name(), Integer.valueOf(min), key.minInt());
        assertEquals(key.name(), Integer.valueOf(max), key.maxInt());
    }

    @Test
    public void allKeyNamesAreUnique() {
        Set<String> seen = new HashSet<>();
        for (ConfigKey<?> key : ConfigSchema.all()) {
            assertTrue("duplicate key: " + key.name(), seen.add(key.name()));
        }
    }

    @Test
    public void widgetAdaptationKeepsCurrentDefault() {
        assertEquals("grid_widget_adaptation", ConfigSchema.Grid.WIDGET_ADAPTATION.name());
        assertEquals(Boolean.FALSE, ConfigSchema.Grid.WIDGET_ADAPTATION.uiDefault());
        assertEquals(Boolean.FALSE, ConfigSchema.Grid.WIDGET_ADAPTATION.runtimeFallback());
        assertEquals(ConfigKey.ExportMode.ALWAYS,
                ConfigSchema.Grid.WIDGET_ADAPTATION.exportMode());
    }

    @Test
    public void integerDefaultsAreInsideDeclaredImportBounds() {
        for (ConfigKey<?> key : ConfigSchema.all()) {
            if (key.type() != ConfigKey.Type.INT || key.minInt() == null) continue;
            int value = (Integer) key.uiDefault();
            assertTrue(key.name(), value >= key.minInt());
            assertTrue(key.name(), value <= key.maxInt());
        }
    }

    @Test
    public void legacyAndCurrentDefaultsRemainDistinctWhereRequired() {
        assertEquals(Integer.valueOf(48), ConfigSchema.Glass.CAPTURE_BLEED_TOP.runtimeFallback());
        assertEquals(Integer.valueOf(48), ConfigSchema.Glass.CAPTURE_BLEED_TOP.uiDefault());
        assertEquals(Integer.valueOf(48), ConfigSchema.Glass.CAPTURE_BLEED_TOP.exportDefault());
        assertEquals(Integer.valueOf(16), ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM.runtimeFallback());
        assertEquals(Integer.valueOf(16), ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM.uiDefault());
        assertEquals(Integer.valueOf(16), ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM.exportDefault());
        assertEquals(ConfigKey.StorageMode.DIRECT, ConfigSchema.Glass.CAPTURE_BLEED_TOP.storageMode());
        assertEquals(ConfigKey.StorageMode.DIRECT, ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM.storageMode());

        assertEquals(Integer.valueOf(4), ConfigSchema.Dock.SQUIRCLE_STROKE_WIDTH.runtimeFallback());
        assertEquals(Integer.valueOf(1), ConfigSchema.Dock.SQUIRCLE_STROKE_WIDTH.uiDefault());
        assertEquals(Integer.valueOf(4), ConfigSchema.Dock.SQUIRCLE_STROKE_WIDTH.exportDefault());
    }

    @Test
    public void legacyGridImportAliasesRemainKnownButAreNeverExported() {
        assertEquals("grid_landscape_margin_horizontal",
                ConfigSchema.Grid.LEGACY_LANDSCAPE_HORIZONTAL_MARGIN.name());
        assertEquals(ConfigKey.ExportMode.NEVER,
                ConfigSchema.Grid.LEGACY_LANDSCAPE_HORIZONTAL_MARGIN.exportMode());
        assertEquals("grid_margin_left", ConfigSchema.Grid.LEGACY_MARGIN_LEFT.name());
        assertEquals(ConfigKey.ExportMode.NEVER, ConfigSchema.Grid.LEGACY_MARGIN_LEFT.exportMode());
    }

    @Test
    public void composeGridSpecsKeepTheirCurrentDefaultsAndBounds() {
        assertComposeIntSpec(ConfigSchema.Grid.LANDSCAPE_HORIZONTAL_DISTANCE, 0, -600, 600);
        assertComposeIntSpec(ConfigSchema.Grid.LANDSCAPE_TOP_DISTANCE, 0, -600, 600);
        assertComposeIntSpec(ConfigSchema.Grid.LANDSCAPE_BOTTOM_DISTANCE, 0, -600, 600);
        assertComposeIntSpec(ConfigSchema.Grid.PORTRAIT_HORIZONTAL_DISTANCE, 0, -600, 600);
        assertComposeIntSpec(ConfigSchema.Grid.PORTRAIT_TOP_DISTANCE, 0, -600, 600);
        assertComposeIntSpec(ConfigSchema.Grid.PORTRAIT_BOTTOM_DISTANCE, 0, -600, 600);
        assertComposeIntSpec(ConfigSchema.Grid.LANDSCAPE_ROW_GAP, 0, -200, 400);
        assertComposeIntSpec(ConfigSchema.Grid.PORTRAIT_ROW_GAP, 0, -200, 400);
        assertComposeIntSpec(ConfigSchema.Grid.LANDSCAPE_INDICATOR_Y, 0, -160, 160);
        assertComposeIntSpec(ConfigSchema.Grid.PORTRAIT_INDICATOR_Y, 0, -160, 160);
    }

    @Test
    public void composeDockSpecsKeepTheirCurrentDefaultsAndBounds() {
        assertComposeIntSpec(ConfigSchema.Dock.BLUR_RADIUS, 100, 0, 400);
        assertComposeIntSpec(ConfigSchema.Dock.HEIGHT_OFFSET, 0, -80, 80);
        assertComposeIntSpec(ConfigSchema.Dock.WIDTH_OFFSET, 0, -80, 80);
        assertComposeIntSpec(ConfigSchema.Dock.CORNER_OFFSET, -1, -50, 100);
        assertComposeIntSpec(ConfigSchema.Dock.BLUR_CORNER_OFFSET, 0, -50, 100);
        assertComposeIntSpec(ConfigSchema.Dock.SPACING, 0, -8, 12);
        assertComposeIntSpec(ConfigSchema.Dock.BOTTOM_OFFSET, 0, -30, 40);
    }

    @Test
    public void composeDividerSpecsKeepTheirCurrentDefaultsAndBounds() {
        assertComposeIntSpec(ConfigSchema.Divider.WIDTH_DP, 10, 0, 160);
        assertComposeIntSpec(ConfigSchema.Divider.HEIGHT_SCALE, 60, 0, 100);
        assertComposeIntSpec(ConfigSchema.Divider.Y_OFFSET_DP, 0, -80, 80);
        assertComposeIntSpec(ConfigSchema.Divider.COLOR_RED, 255, 0, 255);
        assertComposeIntSpec(ConfigSchema.Divider.COLOR_GREEN, 255, 0, 255);
        assertComposeIntSpec(ConfigSchema.Divider.COLOR_BLUE, 255, 0, 255);
        assertComposeIntSpec(ConfigSchema.Divider.ALPHA, 128, 0, 255);
    }

    @Test
    public void composeWorkstationSpecsKeepTheirCurrentDefaultsAndBounds() {
        assertComposeIntSpec(ConfigSchema.Workstation.DOCK_WIDTH_OFFSET, 0, -240, 240);
        assertComposeIntSpec(ConfigSchema.Workstation.GRID_HORIZONTAL_OFFSET, 0, -240, 240);
        assertComposeIntSpec(ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_HORIZONTAL_OFFSET, 0, 0, 240);
        assertComposeIntSpec(ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_VERTICAL_OFFSET, 0, 0, 240);
        assertComposeIntSpec(ConfigSchema.Workstation.ALL_APPS_PORTRAIT_HORIZONTAL_OFFSET, 0, 0, 240);
        assertComposeIntSpec(ConfigSchema.Workstation.ALL_APPS_PORTRAIT_VERTICAL_OFFSET, 0, 0, 240);
        assertComposeIntSpec(ConfigSchema.Workstation.DOCK_ICON_TOP_OFFSET, 0, -48, 48);
        assertComposeIntSpec(ConfigSchema.Workstation.DOCK_ICON_BOTTOM_OFFSET, 0, -48, 48);
    }

    @Test
    public void composeLiquidSpecsExposeOnlyActiveOpticalControls() {
        assertComposeIntSpec(ConfigSchema.Glass.BLUR, 6, 0, 60);
        assertComposeIntSpec(ConfigSchema.Glass.THICKNESS, 18, 1, 60);
        assertComposeIntSpec(ConfigSchema.Glass.IOR, 155, 100, 200);
        assertComposeIntSpec(ConfigSchema.Glass.NORMAL_STRENGTH, 115, 0, 300);
        assertComposeIntSpec(ConfigSchema.Glass.DOME, 100, 0, 200);
        assertComposeIntSpec(ConfigSchema.Glass.LENS_REFRACTION, 12, 0, 60);
        assertComposeIntSpec(ConfigSchema.Glass.CHROMATIC, 2, 0, 40);
        assertComposeIntSpec(ConfigSchema.Glass.CAPTURE_BLEED_TOP, 48, 0, 256);
        assertComposeIntSpec(ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM, 16, 0, 256);
        assertComposeIntSpec(ConfigSchema.Glass.CAPTURE_BLEED_LEFT, 0, 0, 256);
        assertComposeIntSpec(ConfigSchema.Glass.CAPTURE_BLEED_RIGHT, 0, 0, 256);
        assertComposeIntSpec(ConfigSchema.Glass.TINT_ALPHA, 38, 0, 160);
        assertComposeIntSpec(ConfigSchema.Glass.TINT_RED, 238, 0, 255);
        assertComposeIntSpec(ConfigSchema.Glass.TINT_GREEN, 244, 0, 255);
        assertComposeIntSpec(ConfigSchema.Glass.TINT_BLUE, 255, 0, 255);
        assertComposeIntSpec(ConfigSchema.Glass.HIGHLIGHT_WIDTH, 100, 20, 300);
        assertComposeIntSpec(ConfigSchema.Glass.BRIGHTNESS, 108, 50, 200);
        assertComposeIntSpec(ConfigSchema.Glass.SPECULAR_SHARPNESS, 88, 1, 200);
        assertComposeIntSpec(ConfigSchema.Glass.SPECULAR_STRENGTH, 105, 0, 300);
        assertComposeIntSpec(ConfigSchema.Glass.RIM_LIGHT, 100, 0, 300);
        assertComposeIntSpec(ConfigSchema.Glass.CAUSTICS, 28, 0, 100);
    }

    @Test
    public void composePrismalSpecsUseUpstreamDefaultsAndDeclaredGuiBounds() {
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_REFRACTION_INSET, 5, 0, 80);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_DISPLACEMENT_SCALE, 100, 0, 400);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_HEIGHT_TRANSITION_WIDTH, 15, 1, 120);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_SMIN_SMOOTHING, 2, 0, 24);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_EDGE_REFRACTION_FALLOFF, 200, 0, 2000);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_FRESNEL_REFLECT, 79, 0, 500);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_DISPERSION_R, 100, 0, 400);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_DISPERSION_B, 100, 0, 400);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_VIBRANCY, 128, 0, 300);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_PLAIN_HIGHLIGHT, 8, 0, 100);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_LIGHT_DIR_X, 100, -200, 200);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_LIGHT_DIR_Y, 62, -200, 200);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_SHADOW_RED, 0, 0, 255);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_SHADOW_GREEN, 0, 0, 255);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_SHADOW_BLUE, 0, 0, 255);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_SHADOW_ALPHA, 0, 0, 255);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_SHADOW_SOFTNESS, 100, 0, 400);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_TRANSMITTANCE, 100, 0, 100);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_X, 100, 25, 400);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_Y, 100, 25, 400);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_PARALLAX_SCALE, 100, 0, 400);
    }

    @Test
    public void composeStrokeSpecsKeepTheirCurrentDefaultsAndBounds() {
        assertComposeIntSpec(ConfigSchema.Dock.STROKE_RED, 255, 0, 255);
        assertComposeIntSpec(ConfigSchema.Dock.STROKE_GREEN, 255, 0, 255);
        assertComposeIntSpec(ConfigSchema.Dock.STROKE_BLUE, 255, 0, 255);
        assertComposeIntSpec(ConfigSchema.Dock.STROKE_ALPHA, 255, 0, 255);
        assertComposeIntSpec(ConfigSchema.Dock.SQUIRCLE_STROKE_WIDTH, 1, 0, 10);
        assertComposeIntSpec(ConfigSchema.Dock.SQUIRCLE_STROKE_OFFSET, 3, 0, 16);
        assertComposeIntSpec(ConfigSchema.Dock.SQUIRCLE_CONTROL_POINT, 58, 40, 80);
        assertComposeIntSpec(ConfigSchema.Dock.FILL_DIFF_STROKE_WIDTH, 1, 0, 6);
        assertComposeIntSpec(ConfigSchema.Dock.STANDARD_STROKE_WIDTH, 1, 0, 10);
    }

    @Test
    public void composeShadowSpecsKeepTheirCurrentDefaultsAndBounds() {
        assertComposeIntSpec(ConfigSchema.Dock.SHADOW_RADIUS, 15, 1, 40);
        assertComposeIntSpec(ConfigSchema.Dock.SHADOW_SIZE, 18, 1, 60);
        assertComposeIntSpec(ConfigSchema.Dock.SHADOW_ALPHA, 140, 0, 200);
        assertComposeIntSpec(ConfigSchema.Dock.SHADOW_Y, 4, -24, 24);
        assertComposeIntSpec(ConfigSchema.Dock.STROKE_SHADOW_RADIUS, 3, 1, 24);
        assertComposeIntSpec(ConfigSchema.Dock.STROKE_SHADOW_ALPHA, 70, 0, 200);
    }
}
