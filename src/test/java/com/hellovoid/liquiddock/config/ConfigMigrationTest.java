package com.hellovoid.liquiddock.config;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ConfigMigrationTest {
    @Test
    public void legacyGridMarginsPlace1601608080InBothOrientations() {
        Map<String, Integer> values = ConfigMigration.legacyGridPlacements(160, 160, 80, 80);

        assertEquals(Integer.valueOf(160), values.get("grid_landscape_margin_left"));
        assertEquals(Integer.valueOf(160), values.get("grid_landscape_margin_right"));
        assertEquals(Integer.valueOf(80), values.get("grid_landscape_margin_top"));
        assertEquals(Integer.valueOf(80), values.get("grid_landscape_margin_bottom"));
        assertEquals(Integer.valueOf(80), values.get("grid_portrait_margin_left"));
        assertEquals(Integer.valueOf(80), values.get("grid_portrait_margin_right"));
        assertEquals(Integer.valueOf(160), values.get("grid_portrait_margin_top"));
        assertEquals(Integer.valueOf(160), values.get("grid_portrait_margin_bottom"));
    }

    @Test
    public void gridOffsetBaselinesAre57_28And1() {
        assertEquals(0, ConfigMigration.gridOffset("grid_landscape_margin_left", 57));
        assertEquals(0, ConfigMigration.gridOffset("grid_landscape_margin_right", 57));
        assertEquals(0, ConfigMigration.gridOffset("grid_landscape_margin_top", 28));
        assertEquals(0, ConfigMigration.gridOffset("grid_landscape_margin_bottom", 28));
        assertEquals(0, ConfigMigration.gridOffset("grid_portrait_margin_left", 28));
        assertEquals(0, ConfigMigration.gridOffset("grid_portrait_margin_right", 28));
        assertEquals(0, ConfigMigration.gridOffset("grid_portrait_margin_top", 57));
        assertEquals(0, ConfigMigration.gridOffset("grid_portrait_margin_bottom", 57));
        assertEquals(0, ConfigMigration.gridOffset("grid_landscape_row_gap", 1));
        assertEquals(0, ConfigMigration.gridOffset("grid_portrait_row_gap", 1));
    }

    @Test
    public void gridDpConversionUsesDensityAndExistingClampBounds() {
        assertEquals(81, ConfigMigration.gridDpValue(161, 2f));
        assertEquals(600, ConfigMigration.gridDpValue(1200, 2f));
        assertEquals(600, ConfigMigration.gridDpValue(1201, 2f));
        assertEquals(-600, ConfigMigration.gridDpValue(-1201, 2f));
    }

    @Test
    public void axisDistanceFallbackAveragesLeftAndRight() {
        assertEquals(15.1f, ConfigMigration.axisDistance(12.4f, 17.8f), 0.0001f);
        assertEquals(12.4f, ConfigMigration.axisDistance(12.4f, null), 0.0001f);
    }

    @Test
    public void dpPreferenceValuesStoreRoundedDirectIntAndTenths() {
        assertEquals(3, ConfigMigration.directDpValue(2.54f));
        assertEquals(25, ConfigMigration.tenthsDpValue(2.54f));
    }
}
