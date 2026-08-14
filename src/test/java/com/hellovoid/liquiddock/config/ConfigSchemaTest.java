package com.hellovoid.liquiddock.config;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConfigSchemaTest {
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
        assertEquals(Integer.valueOf(-1), ConfigSchema.Glass.CAPTURE_BLEED_TOP.runtimeFallback());
        assertEquals(Integer.valueOf(17), ConfigSchema.Glass.CAPTURE_BLEED_TOP.uiDefault());
        assertEquals(Integer.valueOf(48), ConfigSchema.Glass.CAPTURE_BLEED_TOP.exportDefault());

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
}
