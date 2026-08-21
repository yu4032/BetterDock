package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GridProfileRuntimeWiringTest {
    @Test public void moduleMainUsesPersistedProfileAndInstallsOnlyProductionHooks() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/ModuleMain.java"));

        assertTrue(source.contains("GridProfileConfig.PROFILE_KEY"));
        assertTrue(source.contains("GridProfileConfig.DEFAULT_PROFILE"));
        assertTrue(source.contains("HomeGridProfile.fromPersisted"));
        assertTrue(source.contains("HomeGridProfileOverlayHook.install"));
        assertTrue(source.contains("HomeGridDeviceConfigCountHook.install"));
        assertTrue(source.contains("HomeGridHorizontalCenteringHook.install"));
        assertTrue(source.contains("HomeGridVerticalBoundsHook.install"));
        assertTrue(source.contains("HomeGridDragBoundsHook.install"));
        assertFalse(source.contains("HomeGridRotationBridge"));
        assertFalse(source.contains("HomeGridDragCoordinateProbe"));
        assertFalse(source.contains(": \"10x6\""));
    }
}
