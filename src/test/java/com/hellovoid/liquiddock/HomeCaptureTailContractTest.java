package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/** Guards the bounded trailing HOME samples that capture the final wallpaper settle position. */
public class HomeCaptureTailContractTest {
    private static String glassSource() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java"));
    }

    @Test public void homeFocusSchedulesBoundedTrailingSamples() throws Exception {
        String glass = glassSource();
        assertTrue("HOME return must define a short one-shot tail schedule",
                glass.contains("HOME_SETTLE_TAIL_OFFSETS_MS = {0L, 80L, 160L, 280L}"));

        int start = glass.indexOf("void onLauncherFocused()");
        int end = glass.indexOf("/** Public entry for MainHook", start);
        assertTrue(start >= 0 && end > start);
        String method = glass.substring(start, end);
        assertTrue("HOME return should schedule every bounded tail offset",
                method.contains("for (long tailOffset : HOME_SETTLE_TAIL_OFFSETS_MS)"));
        assertTrue("tail callbacks must force a fresh event-driven sample",
                method.contains("requestStateCapture(\"focus-home-tail-\" + tailOffset)"));
    }

    @Test public void leavingHomeInvalidatesPendingTailCallbacks() throws Exception {
        String glass = glassSource();
        assertTrue("tail callbacks need a generation token",
                glass.contains("private int homeSettleTailToken;"));

        int lostStart = glass.indexOf("void onLauncherFocusLost()");
        int lostEnd = glass.indexOf("/** Launcher gained window focus", lostStart);
        assertTrue(lostStart >= 0 && lostEnd > lostStart);
        String lost = glass.substring(lostStart, lostEnd);
        assertTrue("leaving HOME must invalidate queued trailing captures",
                lost.contains("homeSettleTailToken++;"));

        int focusStart = glass.indexOf("void onLauncherFocused()");
        int focusEnd = glass.indexOf("/** Public entry for MainHook", focusStart);
        String focus = glass.substring(focusStart, focusEnd);
        assertTrue("queued samples must reject stale HOME generations",
                focus.contains("if (token != homeSettleTailToken || !isCaptureAllowed()) return;"));
    }
}
