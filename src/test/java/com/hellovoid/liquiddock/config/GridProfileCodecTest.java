package com.hellovoid.liquiddock.config;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/** Selected grid profile must survive settings backup and restore. */
public class GridProfileCodecTest {
    @Test public void gridProfileRoundTripsAndNormalizesUnsupportedValues() {
        Map<String, Object> preferences = new HashMap<>();
        preferences.put(GridProfileConfig.PROFILE_KEY, "10x6");

        Map<String, Object> exported = ConfigCodec.exportValues(preferences);
        assertEquals("10x6", exported.get(GridProfileConfig.PROFILE_KEY));

        Map<String, Object> imported = ConfigCodec.importValues(exported);
        assertEquals("10x6", imported.get(GridProfileConfig.PROFILE_KEY));

        Map<String, Object> invalid = new HashMap<>();
        invalid.put(GridProfileConfig.PROFILE_KEY, "unsupported");
        assertEquals("8x4",
                ConfigCodec.importValues(invalid).get(GridProfileConfig.PROFILE_KEY));
    }
}
