package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
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
}
