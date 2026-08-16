package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/** Guards the bounded trailing HOME samples that capture the final wallpaper settle position. */
public class HomeCaptureTailContractTest {
    private static String runtimeSource() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/HomeOwnershipRuntime.java"));
    }

    @Test public void appToHomeAddsBoundedTrailingSamplesAfterExistingSettleFrame() throws Exception {
        String runtime = runtimeSource();
        assertTrue("HOME return must define a short one-shot tail schedule",
                runtime.contains("HOME_SETTLE_TAIL_OFFSETS_MS = {80L, 160L, 280L}"));
        assertTrue("tail timing must extend the existing configurable HOME settle delay",
                runtime.contains("long settleDelay = LiquidDockConfig.load().glass.homeSettleDelayMs;"));
        assertTrue("HOME return should schedule every bounded tail offset",
                runtime.contains("for (long tailOffset : HOME_SETTLE_TAIL_OFFSETS_MS)"));
        assertTrue("tail callbacks must request fresh event-driven samples",
                runtime.contains("glass.requestCapture(\"home-settle-tail-\" + tailOffset);"));
    }

    @Test public void staleTailCallbacksCannotCrossOwnershipOrViewBoundaries() throws Exception {
        String runtime = runtimeSource();
        assertTrue("tail callbacks need a generation token",
                runtime.contains("private static int homeCaptureTailToken;"));
        assertTrue("APP ownership must invalidate pending HOME tail captures",
                runtime.contains("homeCaptureTailToken++;"));
        assertTrue("queued samples must reject stale generation, view, and ownership",
                runtime.contains("if (token != homeCaptureTailToken || currentView.get() != glass"));
        assertTrue("tail must remain HOME-only",
                runtime.contains("|| appliedBaseline != HomeOwnershipPolicy.Baseline.HOME) return;"));
    }
}
