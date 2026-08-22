package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Structural contract for bounded PassBlur producer recovery after a successful bind. */
public class PassBlurProducerLivenessContractTest {
    @Test
    public void boundProducerHasBoundedFirstFrameWatchdogAndTerminalCleanup() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/Miuix307ZeroCopyRenderer.java"));
        assertTrue("successful producer bind must arm a first-frame watchdog",
                source.contains("armFirstFrameWatchdog("));
        assertTrue("an active producer must cancel the first-frame watchdog",
                source.contains("cancelFirstFrameWatchdog()"));
        assertTrue("producer recovery must be explicitly bounded",
                source.contains("MAX_STALLED_PRODUCER_RECOVERIES"));
        assertTrue("exhausted producer recovery must terminally clear and unbind",
                source.contains("producer-stall-exhausted") && source.contains("clear();"));
    }
}
