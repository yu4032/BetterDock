package com.hellovoid.liquiddock;

import org.junit.After;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

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

    @Test
    public void productionSnapshotLoadingDoesNotOpenRemotePreferencesWriter() {
        Map<String, Object> values = new HashMap<>();
        values.put("liquiddock_enabled", false);
        TestSharedPreferences remote = new TestSharedPreferences(values);

        ConfigReader reader = ConfigReader.load(remote);

        assertEquals(false, reader.b("liquiddock_enabled", true));
        assertEquals(0, remote.editCount());
        assertEquals(0, remote.commitCount());
        assertEquals(values, remote.getAll());
    }
}
