package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class CaptureCadenceTest {
    @Test public void appPowerLimitWinsWhenIdle() {
        CaptureCadence cadence = new CaptureCadence(60);
        cadence.setDynamicFps(120, 10);
        cadence.setPowerLimitFps(20);
        assertEquals(50_000_000L,
                cadence.intervalNanos(CaptureScene.APP, true, 100L, 1L));
    }

    @Test public void staticAppUsesProbeRateWhenIdle() {
        CaptureCadence cadence = new CaptureCadence(60);
        cadence.setDynamicFps(60, 4);
        cadence.setPowerLimitFps(60);
        assertEquals(250_000_000L,
                cadence.intervalNanos(CaptureScene.APP, true, 0L, 1L));
    }

    @Test public void activePointerOverridesStaticAppProbeAndPowerLimit() {
        CaptureCadence cadence = new CaptureCadence(60);
        cadence.setDynamicFps(5, 1);
        cadence.setPowerLimitFps(5);
        cadence.noteInteraction(1_000_000_000L);
        assertEquals(16_666_666L,
                cadence.intervalNanos(CaptureScene.APP, true, 0L, 1_050_000_000L));
    }

    @Test public void clearingPointerReturnsAppToIdleProbePolicy() {
        CaptureCadence cadence = new CaptureCadence(60);
        cadence.setDynamicFps(5, 1);
        cadence.setPowerLimitFps(5);
        cadence.noteInteraction(1_000_000_000L);
        cadence.clearInteraction();
        assertEquals(1_000_000_000L,
                cadence.intervalNanos(CaptureScene.APP, true, 0L, 1_050_000_000L));
    }

    @Test public void homeUsesBaseRate() {
        CaptureCadence cadence = new CaptureCadence(30);
        cadence.setPowerLimitFps(60);
        assertEquals(33_333_333L,
                cadence.intervalNanos(CaptureScene.HOME, true, Long.MAX_VALUE, 1L));
    }

    @Test public void homeIgnoresAppPowerLimit() {
        CaptureCadence cadence = new CaptureCadence(60);
        cadence.setPowerLimitFps(5);
        assertEquals(16_666_666L,
                cadence.intervalNanos(CaptureScene.HOME, true, Long.MAX_VALUE, 1L));
    }

    @Test public void recentsIgnoresAppPowerLimit() {
        CaptureCadence cadence = new CaptureCadence(60);
        cadence.setDynamicFps(5, 1);
        cadence.setPowerLimitFps(5);
        assertEquals(16_666_666L,
                cadence.intervalNanos(CaptureScene.RECENTS, true, Long.MAX_VALUE, 1L));
    }
}
