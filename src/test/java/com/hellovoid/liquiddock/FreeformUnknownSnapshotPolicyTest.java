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
        assertTrue(gate.contains("Miuix307FreeformCaptureDeferral.defer"));
        assertTrue(gate.contains("DEFER_UNKNOWN_SNAPSHOT"));

        String deferral = source(
                "src/main/java/com/hellovoid/liquiddock/Miuix307FreeformCaptureDeferral.java");
        assertTrue(deferral.contains("retireCaptureAttempt"));
        assertTrue(deferral.contains("sourceDirty"));
        assertTrue(deferral.contains("freeform-snapshot-retry"));
        assertTrue(deferral.contains("keeping current APP backdrop"));

        String dragBridge = source(
                "src/main/java/com/hellovoid/liquiddock/Miuix307DragCaptureHook.java");
        assertTrue(dragBridge.contains("static DockLiquidGlassView currentGlass()"));
    }

    @Test public void explicitUnsafeVisibleSnapshotStillHasWallpaperFailClosedPath()
            throws Exception {
        String gate = source(
                "src/main/java/com/hellovoid/liquiddock/FreeformCaptureLeashHook.java");
        assertTrue(gate.contains("WALLPAPER_FAIL_CLOSED"));
        assertTrue(gate.contains("args[5] = 2"));
    }
}
