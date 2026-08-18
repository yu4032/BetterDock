package com.hellovoid.liquiddock;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class HomeOwnershipRuntimeContractTest {
    private static String source(String name) throws Exception {
        return Files.readString(Paths.get(
                "src/main/java/com/hellovoid/liquiddock/" + name), StandardCharsets.UTF_8);
    }

    @Test public void resolverIsAsyncAndContainsNoLauncherTaskInference() throws Exception {
        String resolver = source("HomeOwnershipResolver.java");
        assertFalse(resolver.contains("ActivityManager"));
        assertFalse(resolver.contains("RunningTaskInfo"));
        assertFalse(resolver.contains("getRunningTasks"));
        assertFalse(resolver.contains("CountDownLatch"));
        assertFalse(resolver.contains("await("));
        assertTrue(resolver.contains("IBinder.FLAG_ONEWAY"));
        assertTrue(resolver.contains("RECHECK_DELAY_MS"));
        assertTrue(resolver.contains("retryRecommended"));
    }

    @Test public void runtimeFailsClosedAndUsesSystemUiResultsForBoundaries() throws Exception {
        String runtime = source("HomeOwnershipRuntime.java");
        assertTrue(runtime.contains("Baseline.UNKNOWN"));
        assertTrue(runtime.contains("setLauncherState(false, false)"));
        assertTrue(runtime.contains("setLauncherState(true, false)"));
        assertTrue(runtime.contains("setLauncherState(true, true)"));
        assertTrue(runtime.contains("onLauncherFocusLost"));
        assertTrue(runtime.contains("onLauncherFocused"));
        assertTrue(runtime.contains("prearmAppBackdrop"));
        assertFalse(runtime.contains("ActivityManager"));
        assertFalse(runtime.contains("getRunningTasks"));
    }

    @Test public void confirmedHomeHas307SettleFallbackWhenFocusHomeCaptureIsTemporarilyBlocked()
            throws Exception {
        String runtime = source("HomeOwnershipRuntime.java");
        String glass = source("DockLiquidGlassView.java");
        String freeze = source("Miuix307HomeTransitionFreezeHook.java");

        int homeState = runtime.indexOf("glass.setLauncherState(true, true)");
        int focus = runtime.indexOf("glass.onLauncherFocused()", homeState);
        assertTrue("SystemUI HOME state must still arm the existing focus-settle path",
                homeState >= 0 && focus > homeState);
        assertTrue("existing focus-home capture remains the preferred HOME refresh",
                glass.contains("requestStateCapture(\"focus-home\")"));
        assertTrue("the existing focus-home path can legitimately be skipped by capture gating",
                glass.contains("if (!isCaptureAllowed()) return;"));

        assertTrue("307 freeze hook must observe confirmed HOME focus settle",
                freeze.contains("\"onLauncherFocused\""));
        assertTrue("fallback must preserve the same configured APP->HOME settle delay",
                freeze.contains("homeSettleDelayMs"));
        assertTrue("fallback expiry must release the preserved APP frame even when focus-home"
                        + " capture was skipped",
                freeze.contains("releaseFrozenBackdrop(glass, \"home-settle-fallback\")"));
        assertTrue("fallback release needs a generation token so an old HOME timer cannot"
                        + " thaw a newer transition",
                freeze.contains("freezeGeneration"));
    }

    @Test public void brokerExposesProviderLifecycleWithoutPolling() throws Exception {
        String broker = source("FreeformLeashBrokerClient.java");
        assertTrue(broker.contains("ProviderListener"));
        assertTrue(broker.contains("onProviderChanged"));
    }
}
