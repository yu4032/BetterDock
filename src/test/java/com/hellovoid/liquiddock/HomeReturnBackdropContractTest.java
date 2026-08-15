package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression contract for APP -> HOME animation backdrop continuity. */
public class HomeReturnBackdropContractTest {
    private static String controller() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/LauncherSceneController.java"));
    }

    private static String glass() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java"));
    }

    @Test public void gestureHomeFromExternalStopsLiveAppCapture() throws Exception {
        String s = controller();
        int method = s.indexOf("private void prearmGestureCaptureTarget");
        assertTrue(method >= 0);
        String body = s.substring(method, Math.min(s.length(), method + 2200));
        assertFalse("HOME return must not keep APP FULL_DISPLAY prearm alive",
                body.contains("prearmAppBackdrop(\"gesture-home-unconfirmed\")"));
        assertTrue("external HOME gesture should switch the visual target to HOME",
                body.contains("glass.prearmHomeReturnBackdrop(\"gesture-home\")"));
    }

    @Test public void launcherFocusGainAlsoPrearmsCleanHomeBackdrop() throws Exception {
        String s = controller();
        int hook = s.indexOf("onWindowFocusChanged");
        assertTrue(hook >= 0);
        String body = s.substring(hook, Math.min(s.length(), hook + 5500));
        assertTrue("Back-button/app-finish returns may not emit GestureToHome",
                body.contains("glass.prearmHomeReturnBackdrop(\"focus-gain\")"));
    }

    @Test public void homeReturnPrearmInvalidatesInFlightAppFrames() throws Exception {
        String s = glass();
        int method = s.indexOf("void prearmHomeReturnBackdrop(String reason)");
        assertTrue(method >= 0);
        String body = s.substring(method, Math.min(s.length(), method + 1800));
        assertTrue(body.contains("cancelPendingCaptureWork();"));
        assertTrue(body.contains("sceneState.setGestureTarget(\"HOME\", System.nanoTime());"));
        assertTrue(body.contains("requestStateCapture(\"home-return-prearm-\" + reason);"));
    }

    @Test public void appToHomeBarrierDoesNotClearLastFrameOnCacheMiss() throws Exception {
        String s = glass();
        int barrier = s.indexOf("private void applyBackdropTransitionBarrier");
        assertTrue(barrier >= 0);
        String body = s.substring(barrier, Math.min(s.length(), barrier + 3200));
        assertTrue(body.contains("BackdropTransitionPolicy.shouldHoldInstalledUntilReplacement("));
        assertTrue(body.contains("holding installed backdrop until HOME replacement"));
    }
}
