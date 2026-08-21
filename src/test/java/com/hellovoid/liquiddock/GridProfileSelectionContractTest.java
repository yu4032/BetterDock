package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Production contract for choosing either supported home-grid profile. */
public class GridProfileSelectionContractTest {
    @Test public void settingsExposeEightByFourAndTenBySixProfiles() throws Exception {
        String preferences = Files.readString(Path.of("src/main/res/xml/preferences.xml"));
        String arrays = Files.readString(Path.of("src/main/res/values/arrays.xml"));
        String entry = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/ModuleMain.java"));

        assertTrue(preferences.contains("<ListPreference"));
        assertTrue(preferences.contains("android:key=\"grid_profile\""));
        assertTrue(preferences.contains("android:dependency=\"home_grid_8x4\""));
        assertTrue(arrays.contains("<item>8x4</item>"));
        assertTrue(arrays.contains("<item>10x6</item>"));
        assertTrue(entry.contains("GridProfileConfig.DEFAULT_PROFILE"));
        assertFalse(entry.contains(": \"10x6\""));
    }
}
