package com.hellovoid.liquiddock;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/** Wiring regression for the 10x6 overlay over the device-verified 8x4 core. */
public class HomeGridHookProfileContractTest {
    private static String read(String path) throws IOException {
        Path file = Paths.get(path);
        assertTrue("missing expected source: " + path, Files.exists(file));
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    @Test
    public void moduleInstallsProfileOverlayAfterStableHomeGridCore() throws IOException {
        String source = read("src/main/java/com/hellovoid/liquiddock/ModuleMain.java");
        int core = source.indexOf("new MainHook().install(classLoader);");
        int overlay = source.indexOf("HomeGridProfileOverlayHook.install(classLoader);");
        assertTrue("MainHook must remain installed", core >= 0);
        assertTrue("profile overlay must be installed after MainHook", overlay > core);
    }

    @Test
    public void overlayUsesSelectedProfileForAxisAndRotationMetadata() throws IOException {
        String source = read(
                "src/main/java/com/hellovoid/liquiddock/HomeGridProfileOverlayHook.java");
        assertTrue(source.contains("HomeGridProfile.fromPersisted"));
        assertTrue(source.contains("profile.columns(portrait)"));
        assertTrue(source.contains("profile.rows(portrait)"));
        assertTrue(source.contains("profile.blockOrigins(portrait)"));
        assertTrue(source.contains("profile.totalBlocks()"));
        assertTrue(source.contains("profile.matchesCounts(h, v)"));
    }

    @Test
    public void overlayIsTenBySixOnlyAndPreservesWorkstationGeometry() throws IOException {
        String source = read(
                "src/main/java/com/hellovoid/liquiddock/HomeGridProfileOverlayHook.java");
        assertTrue(source.contains("profile != HomeGridProfile.GRID_10X6"));
        assertTrue(source.contains("MainHook.isWorkstationMode()"));
    }

    @Test
    public void existingEightByFourCoreRemainsPresent() throws IOException {
        String source = read("src/main/java/com/hellovoid/liquiddock/HomeGridHook.java");
        assertTrue(source.contains("private static boolean grid8x4Enabled"));
        assertTrue(source.contains("isEightByFourGrid"));
    }
}
