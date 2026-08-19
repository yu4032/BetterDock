package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Compose Grid page must expose one master and exactly the two supported profile choices. */
public class GridProfileUiContractTest {

    @Test
    public void gridPageUsesCanonicalMasterAndTwoProfileSelector() throws Exception {
        String ui = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));

        assertTrue(ui.contains("ConfigSchema.Grid.ENABLED"));
        assertTrue(ui.contains("ConfigSchema.Grid.PROFILE.name()"));
        assertTrue(ui.contains("\"8×4 / 4×8\" to \"8x4\""));
        assertTrue(ui.contains("\"10×6 / 6×10\" to \"10x6\""));
        assertTrue(ui.contains("enabled = masterEnabled && customGrid"));
        assertFalse(ui.contains("\"system\" to"));
        assertFalse(ui.contains("\"default\" to"));
    }

    @Test
    public void legacyEightByFourLabelIsNoLongerTheMasterUi() throws Exception {
        String ui = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));
        String zh = Files.readString(Path.of("src/main/res/values-zh-rCN/strings.xml"));

        assertTrue(zh.contains("name=\"enable_extended_grid\""));
        assertTrue(zh.contains("name=\"grid_profile\""));
        assertFalse(ui.contains("R.string.enable_grid_8x4"));
    }
}
