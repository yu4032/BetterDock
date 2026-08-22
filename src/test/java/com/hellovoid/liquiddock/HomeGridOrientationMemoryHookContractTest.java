package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HomeGridOrientationMemoryHookContractTest {

    @Test
    public void moduleInstallsOrientationMemoryAfterGridCompatibilityHooks() throws Exception {
        String source = Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/ModuleMain.java"),
                StandardCharsets.UTF_8);
        int profile = source.indexOf("HomeGridProfileOverlayHook.install");
        int memory = source.indexOf("HomeGridOrientationMemoryHook.install");
        assertTrue(profile >= 0);
        assertTrue(memory > profile);
    }

    @Test
    public void hookUsesLauncherWorkspaceAndSidecarPreferencesOnly() throws Exception {
        String source = Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/HomeGridOrientationMemoryHook.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("com.miui.home.launcher.Launcher"));
        assertTrue(source.contains("setupViews"));
        assertTrue(source.contains("onConfigurationChanged"));
        assertTrue(source.contains("HomeGridSharedPreferencesMemoryStore"));
        assertTrue(source.contains("mWorkspace"));
        assertTrue(source.contains("applySnapshotAtomically"));
        assertTrue(source.contains("captureCurrent"));
        assertFalse(source.contains("updateItemInDatabase"));
        assertFalse(source.contains("GridOccupancyController"));
    }

    @Test
    public void rotationCapturePrecedesNativeProceedAndRestoreFollowsIt() throws Exception {
        String source = Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/HomeGridOrientationMemoryHook.java"),
                StandardCharsets.UTF_8);
        int before = source.indexOf("captureCurrent(sourceOrientation");
        int proceed = source.indexOf("chain.proceed", before);
        int restore = source.indexOf("scheduleTargetResolution", proceed);
        assertTrue(before >= 0);
        assertTrue(proceed > before);
        assertTrue(restore > proceed);
    }

    @Test
    public void runtimeNeverWritesNativeTransposedOccupancyMatrices() throws Exception {
        String source = Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/HomeGridOrientationMemoryHook.java"),
                StandardCharsets.UTF_8);
        assertFalse(source.contains("mOccupied"));
        assertFalse(source.contains("transformToHVArray"));
        assertFalse(source.contains("addOccupied"));
    }
}
