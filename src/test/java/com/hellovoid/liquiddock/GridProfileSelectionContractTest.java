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
        String schema = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java"));
        String runtime = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java"));
        String entry = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/ModuleMain.java"));

        assertTrue(preferences.contains("<ListPreference"));
        assertTrue(preferences.contains("android:key=\"grid_profile\""));
        assertTrue(arrays.contains("<item>8x4</item>"));
        assertTrue(arrays.contains("<item>10x6</item>"));
        assertTrue(schema.contains("PROFILE = string("));
        assertTrue(runtime.contains("final HomeGridProfile profile"));
        assertTrue(entry.contains("runtimeConfig.grid.profile"));
        assertFalse(entry.contains(": \"10x6\""));
    }
}
