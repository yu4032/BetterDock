package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Regression contract: horizontal grid spacing must not resize the vertical axis. */
public class HomeGridAxisIsolationContractTest {
    @Test public void verticalGeometryUsesWidthIndependentSourceForEveryCustomProfile() throws Exception {
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/HomeGridVerticalBoundsHook.java"));

        assertFalse(hook.contains("selectedProfile != HomeGridProfile.GRID_10X6"));
        assertTrue(hook.contains("profile.rows(portrait)"));
        assertTrue(hook.contains("getCellSize"));
        assertTrue(hook.contains("sourceCell"));
        assertTrue(hook.contains("HomeGridVerticalBoundsPolicy.resolve("));
        assertFalse(hook.contains("sourceCellSize, currentGap"));
    }
}
