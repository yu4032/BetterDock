package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Production contract for choosing either supported home-grid profile. */
public class GridProfileSelectionContractTest {
    @Test public void composeSettingsExposeEightByFourAndTenBySixProfiles() throws Exception {
        String compose = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));
        String arrays = Files.readString(Path.of("src/main/res/values/arrays.xml"));
        String entry = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/ModuleMain.java"));

        assertTrue(compose.contains("import com.hellovoid.liquiddock.config.GridProfileConfig"));
        assertTrue(compose.contains("GridProfileConfig.PROFILE_KEY"));
        assertTrue(compose.contains("GridProfileConfig.DEFAULT_PROFILE"));
        assertTrue(compose.contains("R.array.home_grid_profile_entries"));
        assertTrue(compose.contains("R.array.home_grid_profile_values"));
        assertTrue(compose.contains("StringDropdown("));
        assertTrue(arrays.contains("<item>8x4</item>"));
        assertTrue(arrays.contains("<item>10x6</item>"));
        assertTrue(entry.contains("GridProfileConfig.DEFAULT_PROFILE"));
        assertFalse(entry.contains(": \"10x6\""));
    }
}
