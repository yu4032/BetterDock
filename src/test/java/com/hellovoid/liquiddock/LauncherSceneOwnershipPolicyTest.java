package com.hellovoid.liquiddock;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class LauncherSceneOwnershipPolicyTest {
    @Test public void freeformPreservesKnownHomeOwnership() {
        assertTrue(LauncherSceneOwnershipPolicy.launcherOwnsScene(
                false, 5, true, true));
    }

    @Test public void freeformPreservesKnownAppOwnership() {
        assertFalse(LauncherSceneOwnershipPolicy.launcherOwnsScene(
                false, 5, false, true));
    }

    @Test public void freeformWithoutPreviousOwnershipKeepsSafeHomeFallback() {
        assertTrue(LauncherSceneOwnershipPolicy.launcherOwnsScene(
                false, 5, false, false));
    }

    @Test public void fullscreenForegroundUsesCurrentLauncherSignal() {
        assertFalse(LauncherSceneOwnershipPolicy.launcherOwnsScene(
                false, 1, true, true));
        assertTrue(LauncherSceneOwnershipPolicy.launcherOwnsScene(
                true, 1, false, true));
    }

    @Test public void mainHookPreservesPreviousOwnershipAcrossFreeformOverlay()
            throws IOException {
        String source = Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/MainHook.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("foregroundTaskWindowingMode"));
        assertTrue(source.contains("boolean previousOwnershipKnown = launcherLifecycleKnown;"));
        assertTrue(source.contains("boolean previousLauncherOwnership = launcherResumed;"));
        assertTrue(source.contains("previousLauncherOwnership, previousOwnershipKnown"));
    }
}
