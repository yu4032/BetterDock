package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Runtime must prefer canonical grid settings while remaining safe with pre-migration snapshots. */
public class GridRuntimeConfigCompatibilityTest {

    @Test
    public void canonicalTenBySixLoadsAsTypedProfile() {
        Map<String, Object> values = new HashMap<>();
        values.put("home_grid_extended", true);
        values.put("grid_profile", "10x6");

        LiquidDockConfig.Grid grid = LiquidDockConfig.from(new ConfigReader(values)).grid;
        assertTrue(grid.enabled);
        assertEquals(HomeGridProfile.GRID_10X6, grid.profile);
    }

    @Test
    public void legacyEightByFourStillEnablesBaseGridBeforeMigrationRuns() {
        Map<String, Object> values = new HashMap<>();
        values.put("home_grid_8x4", true);

        LiquidDockConfig.Grid grid = LiquidDockConfig.from(new ConfigReader(values)).grid;
        assertTrue(grid.enabled);
        assertEquals(HomeGridProfile.GRID_8X4, grid.profile);
    }

    @Test
    public void canonicalMasterWinsOverLegacyTrue() {
        Map<String, Object> values = new HashMap<>();
        values.put("home_grid_extended", false);
        values.put("home_grid_8x4", true);
        values.put("grid_profile", "10x6");

        LiquidDockConfig.Grid grid = LiquidDockConfig.from(new ConfigReader(values)).grid;
        assertFalse(grid.enabled);
        assertEquals(HomeGridProfile.GRID_10X6, grid.profile);
    }

    @Test
    public void invalidProfileFailsClosedToEightByFour() {
        Map<String, Object> values = new HashMap<>();
        values.put("home_grid_extended", true);
        values.put("grid_profile", "invalid");

        LiquidDockConfig.Grid grid = LiquidDockConfig.from(new ConfigReader(values)).grid;
        assertTrue(grid.enabled);
        assertEquals(HomeGridProfile.GRID_8X4, grid.profile);
    }
}
