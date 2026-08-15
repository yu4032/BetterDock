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
    @Test public void sceneControllerRoutesTaskWindowingModeThroughOwnershipPolicy()
            throws IOException {
        String resolver = Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/ForegroundTaskResolver.java"),
                StandardCharsets.UTF_8);
        String controller = Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/LauncherSceneController.java"),
                StandardCharsets.UTF_8);
        assertTrue(resolver.contains("getWindowingMode"));
        assertTrue(resolver.contains("LauncherSceneOwnershipPolicy.launcherOwnsScene"));
        assertTrue(controller.contains("observation.windowingMode"));
        assertTrue(controller.contains("LauncherSceneOwnershipPolicy.launcherOwnsScene"));
    }
}
