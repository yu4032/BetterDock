package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Safety contract for the first, read-only device-diagnostic rotation bridge. */
public class HomeGridRotationBridgeContractTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/com/hellovoid/liquiddock/HomeGridRotationBridge.java");

    @Test public void diagnosticBridgeIsStrictlyReadOnly() throws Exception {
        assertTrue(Files.exists(SOURCE));
        String source = Files.readString(SOURCE);
        assertTrue(source.contains("OBSERVE_ONLY = true"));
        assertTrue(source.contains("HomeGridRotationPlanner.plan("));
        assertTrue(source.contains("[DC][GRID10][PLAN]"));
        assertTrue(source.contains("[DC][GRID10][API]"));
        assertTrue(source.contains("updateCellOccupiedMarks"));
        assertTrue(source.contains("relayoutByOccupiedCells"));
        assertTrue(source.contains("setupLayoutParam"));

        assertFalse(source.contains("setIntField(item, \"cellX\""));
        assertFalse(source.contains("setIntField(item, \"cellY\""));
        assertFalse(source.contains("addOrMoveItemInDatabase"));
        assertFalse(source.contains("updateItemInDatabase"));
        assertFalse(source.contains("deleteItemFromDatabase"));
    }

    @Test public void bridgeUsesWeakWorkspaceAndOnlyRunsFor10x6() throws Exception {
        String source = Files.readString(SOURCE);
        assertTrue(source.contains("WeakReference<android.view.View>"));
        assertTrue(source.contains("selectedProfile != HomeGridProfile.GRID_10X6"));
        assertTrue(source.contains("onConfigurationChanged"));
        assertTrue(source.contains("setupViews"));
        assertTrue(source.contains("mWorkspace"));
    }

    @Test public void moduleInstallsBridgeAfterProfileOverlay() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/ModuleMain.java"));
        assertTrue(source.contains("HomeGridRotationBridge.install"));
        assertTrue(source.indexOf("HomeGridProfileOverlayHook.install")
                < source.indexOf("HomeGridRotationBridge.install"));
    }
}
