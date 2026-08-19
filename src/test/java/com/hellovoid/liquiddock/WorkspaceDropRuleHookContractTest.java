package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class WorkspaceDropRuleHookContractTest {
    private static String read(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/" + name));
    }

    @Test public void customGridBypassesOnlyVendorPatternThroughExplicitPlacementPolicy()
            throws Exception {
        String entry = read("ModuleMain.java");
        String hook = read("WorkspaceDropRuleHook.java");

        assertTrue(entry.contains("WorkspaceDropRuleHook.install(classLoader"));
        assertTrue(entry.contains("runtimeConfig.grid.profile"));
        assertTrue(hook.contains("LayoutDropRuleForSwapPlaces"));
        assertTrue(hook.contains("\"isLegalXY\""));
        assertTrue(hook.contains("int.class, int.class, int.class, int.class"));
        assertTrue(hook.contains("WorkspaceDropPolicy.isPlacementAllowed("));

        // This hook must remain a narrow placement-pattern gate. Bounds, occupancy and vacancy
        // continue to belong to MIUI; do not grow a second Workspace placement engine here.
        assertFalse(hook.contains("hookMethod(gridOccupancy"));
        assertFalse(hook.contains("findVacant"));
        assertFalse(hook.contains("markCells"));
    }

    @Test public void eightByFourStillBypassesTheVendorPattern() {
        assertTrue(WorkspaceDropPolicy.isPlacementAllowed(
                HomeGridProfile.GRID_8X4, 0, 0, 4, 2));
        assertTrue(WorkspaceDropPolicy.isPlacementAllowed(
                HomeGridProfile.GRID_8X4, 4, 2, 4, 2));
        assertTrue(WorkspaceDropPolicy.isPlacementAllowed(
                HomeGridProfile.GRID_8X4, 7, 3, 1, 1));
    }
}
