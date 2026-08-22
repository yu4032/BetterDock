package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Guards folder-only PassBlur throttling from leaking into the independent Dock renderer. */
public class LauncherGlassProducerIdleContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void defaultBridgeBindRemainsContinuousForDock() throws Exception {
        String bridge = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));

        assertTrue(bridge.contains(
                "return bindInternal(materialHost, producerSurface, requestedScale, false);"));
    }

    @Test
    public void folderSessionUsesExplicitOnDemandBinding() throws Exception {
        String bridge = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));
        String session = Files.readString(MAIN.resolve("LauncherGlassSession.java"));

        assertTrue(bridge.contains("static Binding bindOnDemand("));
        assertTrue(bridge.contains(
                "return bindInternal(materialHost, producerSurface, requestedScale, true);"));
        assertTrue(session.contains("Miuix307PassBlurBridge.bindOnDemand("));
    }

    @Test
    public void onlyManagedBindingsExposePauseAndRealtimeControl() throws Exception {
        String bridge = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));

        assertTrue(bridge.contains("final boolean callerManagedUpdates;"));
        assertTrue(bridge.contains("static void requestSingleUpdate(Binding binding, View host)"));
        assertTrue(bridge.contains("static void setRealtimeUpdates(Binding binding, boolean enabled)"));
        assertTrue(bridge.contains("static void pauseUpdates(Binding binding)"));
    }
}
