package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HomeGridProfileOverlayContractTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/com/hellovoid/liquiddock/HomeGridProfileOverlayHook.java");

    @Test public void overlayOwnsOnlyProfileCountsAndRotationMetadata() throws Exception {
        assertTrue(Files.exists(SOURCE));
        String source = Files.readString(SOURCE);
        assertTrue(source.contains("profileRewriteForGridName"));
        assertTrue(source.contains("LayoutTransformRuleGridChanged"));
        assertTrue(source.contains("checkCellCount"));
        assertTrue(source.contains("get4x2WidgetCase"));
        assertTrue(source.contains("getDstBlockXY"));
        assertTrue(source.contains("preflightPrivateApi(classLoader);"));
        assertTrue(source.contains("selectedProfile != HomeGridProfile.GRID_10X6"));
        assertFalse(source.contains("WorkspaceDropPolicy"));
        assertFalse(source.contains("GridOccupancy"));
        assertFalse(source.contains("findVacant"));
    }

    @Test public void overlayFailsClosedOutsideNormalWorkspace() throws Exception {
        String source = Files.readString(SOURCE);
        assertTrue(source.contains("MainHook.isWorkstationMode()"));
        assertTrue(source.contains("isExcludedGridConfigCall()"));
        assertTrue(source.contains(".folder."));
        assertTrue(source.contains("allapps"));
        assertTrue(source.contains(".laptop."));
        assertTrue(source.contains("hotseats"));
        assertTrue(source.contains("dockbar"));
    }
}
