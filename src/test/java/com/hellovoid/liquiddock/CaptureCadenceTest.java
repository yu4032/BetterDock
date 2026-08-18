package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class CaptureCadenceTest {
    @Test public void powerLimitAlwaysWinsForDynamicAppCapture() {
        CaptureCadence cadence = new CaptureCadence(60);
        cadence.setDynamicFps(120, 10);
        cadence.setPowerLimitFps(20);
        assertEquals(50_000_000L,
                cadence.intervalNanos(CaptureScene.APP, true, 100L, 1L));
    }

    @Test public void staticAppUsesProbeRate() {
        CaptureCadence cadence = new CaptureCadence(60);
        cadence.setDynamicFps(60, 4);
        cadence.setPowerLimitFps(60);
        assertEquals(250_000_000L,
                cadence.intervalNanos(CaptureScene.APP, true, 0L, 1L));
    }

    @Test public void homeUsesBaseRate() {
        CaptureCadence cadence = new CaptureCadence(30);
        cadence.setPowerLimitFps(60);
        assertEquals(33_333_333L,
                cadence.intervalNanos(CaptureScene.HOME, true, Long.MAX_VALUE, 1L));
    }

    @Test public void recentsUsesDedicatedInteractionRateInsteadOfAppPowerLimit() {
        CaptureCadence cadence = new CaptureCadence(5);
        cadence.setDynamicFps(5, 1);
        cadence.setPowerLimitFps(5);
        assertEquals(16_666_666L,
                cadence.intervalNanos(CaptureScene.RECENTS, true, Long.MAX_VALUE, 1L));
    }

    @Test public void gestureMoveDrivesCaptureAndRecentsDoesNotFreeRun() throws Exception {
        String source = Files.readString(Paths.get(
                "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java"),
                StandardCharsets.UTF_8);

        assertTrue("finger contact must have an explicit capture-active latch",
                source.contains("gestureCaptureActive"));
        assertTrue("every gesture MOVE must dirty/schedule a capture",
                source.contains("requestStateCapture(\"dock-gesture-move\")"));
        assertTrue("gesture cadence must bypass the dynamic APP power/probe policy",
                source.contains("interactionIntervalNanos"));
        assertFalse("ordinary Recents must not run an unconditional capture loop when static",
                source.contains("requestStateCapture(\"recents-continue\")"));
    }
}
