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

    @Test public void runtimeRemainsBootstrapOnlyAndContainsNoLauncherTaskInference() throws Exception {
        String runtime = source("HomeOwnershipRuntime.java");
        assertTrue(runtime.contains("Baseline.UNKNOWN"));
        assertTrue(runtime.contains("setLauncherState(false, false)"));
        assertTrue(runtime.contains("setLauncherState(true, false)"));
        assertTrue(runtime.contains("setLauncherState(true, true)"));
        assertFalse(runtime.contains("ActivityManager"));
        assertFalse(runtime.contains("getRunningTasks"));
        assertFalse("bootstrap runtime must not own live transition focus side effects",
                runtime.contains("onLauncherFocusLost"));
        assertFalse("bootstrap runtime must not own live transition focus side effects",
                runtime.contains("onLauncherFocused"));
    }

    @Test public void genericCapturePathDoesNotRefreshOwnershipFromLauncherFocus() throws Exception {
        String main = source("MainHook.java");
        assertFalse(main.contains("HomeOwnershipRuntime.request(\"focus\")"));
        assertFalse(main.contains("liquid focus boundary="));
    }

    @Test public void specialized307DoesNotDriveOwnershipFromLauncherFocusDuringGestureTransitions()
            throws Exception {
        String ownership = source("Miuix307CaptureOwnershipHook.java");
        assertFalse("307 must not install a Launcher focus ownership bridge",
                ownership.contains("installHomeOwnershipRefreshBridge"));
        assertFalse("307 must not query ownership from Launcher focus churn",
                ownership.contains("HomeOwnershipRuntime.request(\"miuix307-focus\")"));
    }

    @Test public void experimentalHomeTransitionFreezeSourceIsRemoved() throws Exception {
        assertFalse(Files.exists(Paths.get(
                "src/main/java/com/hellovoid/liquiddock/Miuix307HomeTransitionFreezeHook.java")));
        String entry = source("ModuleMain.java");
        assertFalse(entry.contains("Miuix307HomeTransitionFreezeHook.install()"));
    }

    @Test public void brokerExposesProviderLifecycleWithoutPolling() throws Exception {
        String broker = source("FreeformLeashBrokerClient.java");
        assertTrue(broker.contains("ProviderListener"));
        assertTrue(broker.contains("onProviderChanged"));
    }
}
