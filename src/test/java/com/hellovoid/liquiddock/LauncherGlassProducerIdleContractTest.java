package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Guards folder-only PassBlur throttling from leaking into the independent Dock renderer. */
public class LauncherGlassProducerIdleContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void rootOwnedFolderSessionMayThrottleButDockMaterialHostStaysContinuous() throws Exception {
        String bridge = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));

        assertTrue(bridge.contains("final boolean callerManagedUpdates;"));
        assertTrue(bridge.contains("materialHost.getRootView() == materialHost"));
        assertTrue(bridge.contains("if (binding.callerManagedUpdates)"));
        assertTrue(bridge.contains("schedulePauseUpdates(materialHost, binding, INITIAL_UPDATE_FRAMES)"));
    }

    @Test
    public void pauseAndSingleRefreshCannotChangeContinuousDockBinding() throws Exception {
        String bridge = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));

        assertTrue(bridge.contains("static void requestSingleUpdate(Binding binding, View host)"));
        assertTrue(bridge.contains("static void pauseUpdates(Binding binding)"));
        assertTrue(bridge.contains("!binding.callerManagedUpdates"));
    }
}
