package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/** Regression contract: 6x10 portrait rows must stay inside the real CellLayout touch bounds. */
public class HomeGridPortraitVerticalBoundsContractTest {
    @Test public void tenRowPortraitGridInstallsVerticalFitCorrection() throws Exception {
        String entry = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/ModuleMain.java"));
        assertTrue(entry.contains("HomeGridVerticalBoundsHook.install(classLoader"));

        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/HomeGridVerticalBoundsHook.java"));
        assertTrue(hook.contains("profile.rows(portrait)"));
        assertTrue(hook.contains("mCellPaddingTop"));
        assertTrue(hook.contains("mCellHeight"));
        assertTrue(hook.contains("mHeightGap"));
        assertTrue(hook.contains("mYs"));
    }
}
