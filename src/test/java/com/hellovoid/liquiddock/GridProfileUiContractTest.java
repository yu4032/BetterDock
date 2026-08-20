package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/** Experimental UI keeps the existing grid master and adds only a profile selector. */
public class GridProfileUiContractTest {
    @Test public void gridPageOffersEightByFourAndTenBySixProfiles() throws Exception {
        String ui = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));
        assertTrue(ui.contains("GridProfileConfig.PROFILE_KEY"));
        assertTrue(ui.contains("\"8×4 / 4×8\" to \"8x4\""));
        assertTrue(ui.contains("\"10×6 / 6×10\" to \"10x6\""));
        assertTrue(ui.contains("enabled = masterEnabled && customGrid"));
        assertTrue(ui.contains("ConfigSchema.Grid.ENABLED"));
    }
}
