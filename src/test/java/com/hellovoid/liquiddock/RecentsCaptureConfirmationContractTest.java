package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Guards the timing boundary between speculative Recents prearm and confirmed live Recents. */
public class RecentsCaptureConfirmationContractTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/" + name));
    }

    @Test public void gestureTargetDoesNotClaimConfirmedOverview() throws Exception {
        String glass = source("DockLiquidGlassView.java");
        int start = glass.indexOf("void setGestureCaptureTarget(String target)");
        int end = glass.indexOf("void setForegroundOwnership(ForegroundOwnership ownership)", start);
        assertTrue(start >= 0 && end > start);
        String gestureMethod = glass.substring(start, end);
        assertFalse("gesture construction must remain prearm-only",
                gestureMethod.contains("overviewActive = \"RECENTS\".equals(target);"));
        assertTrue("gesture intent must still update scene state",
                gestureMethod.contains("sceneState.setGestureTarget(target, System.nanoTime());"));
    }

    @Test public void exactOverviewLifecycleOwnsLiveConfirmation() throws Exception {
        String glass = source("DockLiquidGlassView.java");
        int start = glass.indexOf("void setOverviewActive(boolean active, String reason)");
        int end = glass.indexOf("/** A touch event on the Dock area", start);
        assertTrue(start >= 0 && end > start);
        String overviewMethod = glass.substring(start, end);
        assertTrue(overviewMethod.contains("overviewActive = active;"));
        assertTrue(overviewMethod.contains("lastCaptureStartNanos = 0L;"));
        assertTrue(overviewMethod.contains("requestStateCapture(active ? \"overview-enter-\" + reason"));
    }

    @Test public void startCapturePassesOverviewConfirmationSeparately() throws Exception {
        String glass = source("DockLiquidGlassView.java");
        int start = glass.indexOf("private void startCapture()");
        int end = glass.indexOf("private void handleCaptureResult", start);
        assertTrue(start >= 0 && end > start);
        String startCapture = glass.substring(start, end);
        assertTrue(startCapture.contains("CaptureSourcePolicy.sourceFor("));
        assertTrue("runtime source selection must still pass confirmed Overview state explicitly",
                startCapture.contains("requestScene, false, isRecentsVisible(), liveHomeBehindFreeform"));
        assertTrue("freeform HOME live-capture flag must remain a separate source input",
                startCapture.contains("liveHomeBehindFreeform"));
    }
}
