package com.hellovoid.liquiddock;

import org.junit.After;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;

public class ConfigLoadPolicyTest {
    @After
    public void restoreWidgetAdaptationState() {
        WidgetGridSizing.setWidgetAdaptationEnabled(false);
    }

    @Test
    public void loadingConfigDoesNotEnableWidgetAdaptation() {
        WidgetGridSizing.setWidgetAdaptationEnabled(false);
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("home_grid_8x4", true);
        prefs.put("grid_widget_adaptation", true);

        LiquidDockConfig.from(new ConfigReader(prefs));

        assertArrayEquals(new int[]{0, 0, 0, 0}, WidgetGridSizing.gridRect(
                0, 0, 1, 1, new int[]{0}, new int[]{0}, 100, 100, 0, 0));
    }
}
