package com.hellovoid.liquiddock;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class HomeOwnershipConvergenceContractTest {
    @Test public void mainHookNoLongerInfersHomeOwnershipFromLauncherTasks() throws Exception {
        String source = Files.readString(Paths.get(
                "src/main/java/com/hellovoid/liquiddock/MainHook.java"),
                StandardCharsets.UTF_8);
        assertFalse(source.contains("getRunningTasks("));
        assertFalse(source.contains("foregroundTaskWindowingMode"));
        assertFalse(source.contains("LauncherSceneOwnershipPolicy"));
        assertFalse(source.contains("seedLauncherLifecycleState"));
        assertFalse(source.contains("launcherOwnsScene"));
        assertFalse(source.contains("launcherLifecycleKnown"));
        assertFalse(source.contains("launcherResumed"));
        assertTrue(source.contains("HomeOwnershipRuntime.bind"));
        assertTrue(source.contains("HomeOwnershipRuntime.request(\"focus\")"));
    }

    @Test public void productionOwnershipPolicyFileIsRemoved() {
        assertFalse(Files.exists(Paths.get(
                "src/main/java/com/hellovoid/liquiddock/LauncherSceneOwnershipPolicy.java")));
    }
}
