package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Test public void miuix307UsesNativeGestureTrackerAsPerMoveCaptureOwner() throws Exception {
        Path hookPath = Paths.get(
                "src/main/java/com/hellovoid/liquiddock/Miuix307GestureCaptureHook.java");
        assertTrue("307 needs a gesture-specific capture bridge", Files.exists(hookPath));
        String source = Files.readString(hookPath, StandardCharsets.UTF_8);

        assertTrue(source.contains("com.miui.home.recents.GestureTouchEventTracker"));
        assertTrue(source.contains("onTouchEvent"));
        assertTrue(source.contains("MotionEvent.ACTION_MOVE"));
        assertTrue(source.contains("onDockGestureMotion"));
        assertTrue(source.contains("requestCapture(\"miuix307-gesture-move\")"));
        assertTrue("one gesture may recover an opened capture breaker once",
                source.contains("circuitRecoveryUsed"));

        String module = Files.readString(Paths.get(
                "src/main/java/com/hellovoid/liquiddock/ModuleMain.java"),
                StandardCharsets.UTF_8);
        assertTrue(module.contains("Miuix307GestureCaptureHook.install(classLoader);"));
    }
}
