package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Runtime compatibility contract while MainHook still reads the historical grid key. */
public class GridRuntimeConfigCompatibilityTest {
    @Test
    public void canonicalMasterOverridesLegacyTrueWhenDisabled() {
        Map<String, Object> values = new HashMap<>();
        values.put("home_grid_8x4", true);
        values.put("home_grid_extended", false);

        LiquidDockConfig config = LiquidDockConfig.from(new ConfigReader(values));

        assertFalse(config.grid.enabled);
    }

    @Test
    public void canonicalMasterOverridesLegacyFalseWhenEnabled() {
        Map<String, Object> values = new HashMap<>();
        values.put("home_grid_8x4", false);
        values.put("home_grid_extended", true);

        LiquidDockConfig config = LiquidDockConfig.from(new ConfigReader(values));

        assertTrue(config.grid.enabled);
    }

    @Test
    public void legacyValueStillWorksWhenCanonicalMasterIsAbsent() {
        Map<String, Object> values = new HashMap<>();
        values.put("home_grid_8x4", true);

        LiquidDockConfig config = LiquidDockConfig.from(new ConfigReader(values));

        assertTrue(config.grid.enabled);
    }
}
