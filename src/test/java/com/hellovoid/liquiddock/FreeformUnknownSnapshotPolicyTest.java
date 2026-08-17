package com.hellovoid.liquiddock;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** APP freeform uncertainty must not replace a valid app backdrop with wallpaper. */
public class FreeformUnknownSnapshotPolicyTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of(relative));
    }

    @Test public void unavailableSnapshotIsUnknownRatherThanClaimedVisibleFreeform() {
        FreeformTaskLeashResolver.Resolution unknown =
                FreeformTaskLeashResolver.Resolution.unavailable(false);
        assertFalse(unknown.isKnown());
        assertFalse(unknown.hasVisibleFreeformTasks());
        assertFalse(unknown.isSafe());

        FreeformTaskLeashResolver.Resolution none =
                FreeformTaskLeashResolver.Resolution.noFreeform();
        assertTrue(none.isKnown());
        assertFalse(none.hasVisibleFreeformTasks());
        assertTrue(none.isSafe());
    }

    @Test public void finalGateDefersUnknownSnapshotInsteadOfSwitchingModeOneToWallpaper()
            throws Exception {
        String gate = source(
                "src/main/java/com/hellovoid/liquiddock/FreeformCaptureLeashHook.java");
        assertTrue(gate.contains("if (!resolution.isKnown())"));
        assertTrue(gate.contains("FreeformSnapshotDeferredException"));
        assertTrue(gate.contains("DEFER_UNKNOWN_SNAPSHOT"));

        String dock = source(
                "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java");
        assertTrue(dock.contains("error instanceof FreeformSnapshotDeferredException"));
        assertTrue(dock.contains("freeform-snapshot-retry"));
        assertTrue(dock.contains("keeping current APP backdrop"));
    }

    @Test public void explicitUnsafeVisibleSnapshotStillHasWallpaperFailClosedPath()
            throws Exception {
        String gate = source(
                "src/main/java/com/hellovoid/liquiddock/FreeformCaptureLeashHook.java");
        assertTrue(gate.contains("WALLPAPER_FAIL_CLOSED"));
        assertTrue(gate.contains("args[5] = 2"));
    }
}
