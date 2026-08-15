package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression contract: workstation must never hide both native and glass backdrops. */
public class WorkstationBackdropVisibilityContractTest {
    private static String mainHook() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/MainHook.java"));
    }

    private static String glass() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java"));
    }

    @Test public void workstationSetupNeverHidesGlassHostPermanently() throws Exception {
        String s = mainHook();
        assertFalse("workstation setup must not make the parent host GONE",
                s.contains("liquidGlassHostView.setVisibility(View.GONE)"));
        assertTrue("workstation setup keeps the host available for later live bursts",
                s.contains("liquidGlassHostView.setVisibility(View.VISIBLE)"));
    }

    @Test public void suspendedWorkstationUsesNativeBackdropFallback() throws Exception {
        String s = glass();
        int suspend = s.indexOf("private void suspendWorkstationGlass(String reason)");
        assertTrue(suspend >= 0);
        String body = s.substring(suspend, Math.min(s.length(), suspend + 1800));
        assertTrue("suspended workstation must reveal the native Dock background",
                body.contains("geometrySource.setAlpha(1f);"));
        assertTrue(body.contains("nativeBackgroundHiddenByGlass = false;"));
        assertTrue("only the glass child is suspended",
                body.contains("setVisibility(INVISIBLE);"));
    }

    @Test public void liveWorkstationBurstSwapsNativeForGlass() throws Exception {
        String s = glass();
        int burst = s.indexOf("private void startWorkstationCaptureBurst(String reason)");
        assertTrue(burst >= 0);
        String body = s.substring(burst, Math.min(s.length(), burst + 1800));
        assertTrue(body.contains("setVisibility(VISIBLE);"));
        assertTrue(body.contains("geometrySource.setAlpha(0f);"));
        assertTrue(body.contains("nativeBackgroundHiddenByGlass = true;"));
    }

    @Test public void mainHookDoesNotForceNativeBackdropOffOnWorkstationEntry() throws Exception {
        String s = mainHook();
        int mode = s.indexOf("private static void setWorkstationMode(boolean enabled)");
        assertTrue(mode >= 0);
        String body = s.substring(mode, Math.min(s.length(), mode + 1800));
        assertFalse("MainHook must delegate backdrop swapping to DockLiquidGlassView",
                body.contains("oldBg.setAlpha(0f);"));
    }
}
