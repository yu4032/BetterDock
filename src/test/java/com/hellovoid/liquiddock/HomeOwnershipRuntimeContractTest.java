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

    @Test public void specialized307DoesNotDriveOwnershipFromLauncherFocusDuringGestureTransitions()
            throws Exception {
        String ownership = source("Miuix307CaptureOwnershipHook.java");

        // Device regression: onWindowFocusChanged can fire repeatedly while HyperOS is merging
        // APP<->HOME/Recents transitions. Each HomeOwnershipRuntime.request() immediately emits an
        // UNKNOWN baseline, which resets DockLiquidGlassView scene/capture state. The specialized
        // 307 pipeline must not restore this legacy lifecycle hook as an ownership driver.
        assertFalse("307 must not install a Launcher focus ownership bridge",
                ownership.contains("installHomeOwnershipRefreshBridge"));
        assertFalse("307 must not query ownership from Launcher focus churn",
                ownership.contains("HomeOwnershipRuntime.request(\"miuix307-focus\")"));
    }

    @Test public void specialized307DoesNotInstallExperimentalHomeTransitionFreeze()
            throws Exception {
        String entry = source("ModuleMain.java");

        // The freeze experiment suppresses the native HOME hand-off and then depends on async
        // ownership/lifecycle convergence to thaw the last APP bitmap. Device logs show that this
        // produces repeated UNKNOWN/APP transitions during the same system gesture. Keep the
        // experimental hook out of the production 307 path.
        assertFalse("307 must not install the experimental HOME freeze hook",
                entry.contains("Miuix307HomeTransitionFreezeHook.install()"));
    }

    @Test public void brokerExposesProviderLifecycleWithoutPolling() throws Exception {
        String broker = source("FreeformLeashBrokerClient.java");
        assertTrue(broker.contains("ProviderListener"));
        assertTrue(broker.contains("onProviderChanged"));
    }
}
