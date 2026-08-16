package com.hellovoid.liquiddock;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class LauncherSceneOwnershipPolicyTest {
    @Test public void freeformForegroundKeepsLauncherSceneWhenLifecyclePauses() {
        assertTrue(LauncherSceneOwnershipPolicy.launcherOwnsScene(false, 5));
    }

    @Test public void fullscreenForegroundStillMovesCaptureToApp() {
        assertFalse(LauncherSceneOwnershipPolicy.launcherOwnsScene(false, 1));
    }

    @Test public void resumedLauncherAlwaysOwnsItsScene() {
        assertTrue(LauncherSceneOwnershipPolicy.launcherOwnsScene(true, 1));
    }

    @Test public void mainHookRoutesLifecycleOwnershipThroughWindowingModePolicy()
            throws IOException {
        String source = Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/MainHook.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("foregroundTaskWindowingMode"));
        assertTrue(source.contains("LauncherSceneOwnershipPolicy.launcherOwnsScene"));
    }
}
