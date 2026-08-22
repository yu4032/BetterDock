package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Guards the folder-glass producer against remaining live for the whole Launcher lifetime. */
public class LauncherGlassProducerIdleContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void passBlurProducerPausesAfterInitialWallpaperFrameBurst() throws Exception {
        String bridge = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));

        assertTrue(bridge.contains("INITIAL_UPDATE_FRAMES"));
        assertTrue(bridge.contains("schedulePauseUpdates(materialHost, binding, INITIAL_UPDATE_FRAMES)"));
        assertTrue(bridge.contains("setUpdatesEnabled(binding, false)"));
    }

    @Test
    public void bridgeKeepsAnExplicitSingleUpdateEntryPointForFutureInvalidation() throws Exception {
        String bridge = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));

        assertTrue(bridge.contains("static void requestSingleUpdate(Binding binding, View host)"));
        assertTrue(bridge.contains("setUpdatesEnabled(binding, true)"));
    }
}
