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

    @Test public void brokerExposesProviderLifecycleWithoutPolling() throws Exception {
        String broker = source("FreeformLeashBrokerClient.java");
        assertTrue(broker.contains("ProviderListener"));
        assertTrue(broker.contains("onProviderChanged"));
    }
}
