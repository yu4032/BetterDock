package com.hellovoid.liquiddock;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;

/** Contracts derived from the decompiled HyperOS 3.0.307+ native Dock backdrop behavior. */
public class Miuix307BackdropPolicyTest {
    private static String[] exclusions307(String dock, String drag, Collection<String> freeform)
            throws Exception {
        final Method method;
        try {
            method = CaptureExclusionNames.class.getDeclaredMethod(
                    "mergeMiuix307", String.class, String.class, Collection.class);
        } catch (NoSuchMethodException missing) {
            fail("CaptureExclusionNames must expose the 307 SystemUI exclusion set");
            throw missing;
        }
        method.setAccessible(true);
        return (String[]) method.invoke(null, dock, drag, freeform);
    }

    @Test public void miuix307BackdropMatchesNativeSemantics() throws Exception {
        assertEquals(CaptureSourcePolicy.Source.WALLPAPER,
                CaptureSourcePolicy.sourceFor(CaptureScene.HOME, false, false));
        assertEquals(CaptureSourcePolicy.Source.FULL_DISPLAY,
                CaptureSourcePolicy.sourceFor(CaptureScene.APP, false, false));
        assertEquals(CaptureSourcePolicy.Source.FULL_DISPLAY,
                CaptureSourcePolicy.sourceFor(CaptureScene.RECENTS, false, true));

        assertArrayEquals(new String[]{
                        "Floating Dock", "drag-layer",
                        "NavigationBar", "StatusBar", "GestureStub", "DockAssistantView",
                        "freeform#1"},
                exclusions307("Floating Dock", "drag-layer",
                        Arrays.asList("freeform#1", "NavigationBar", "")));

        String exclusions = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/CaptureExclusionNames.java"));
        String dock = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java"));
        String glassHook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java"));
        String pipeline = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java"));

        assertTrue(dock.contains("void setGestureCaptureTarget(String target)"));
        assertTrue(exclusions.contains("Miuix307MaterialPipeline.isInstalled()"));

        // Device regression: GestureToHome is emitted while APP->HOME still contains the
        // SHOW_WALLPAPER/IS_WALLPAPER transition layers. It must therefore freeze the last valid
        // APP bitmap, not immediately switch Prismal to the HOME wallpaper source.
        assertTrue(pipeline.contains("com.miui.home.recents.util.StateNotifyUtils"));
        assertTrue(pipeline.contains("sendStateBroadcast"));
        assertTrue(pipeline.contains("\"toHome\""));
        assertTrue(pipeline.contains("MiuixGlassHook.onHomeTransitionStart()"));
        assertTrue(glassHook.contains("static void onHomeTransitionStart()"));
        assertTrue(glassHook.contains("glass.freezeBackdropForHomeTransition("));
        assertFalse(glassHook.contains("glass.setGestureCaptureTarget(\"HOME\")"));

        // Freeze is a capture-scheduling barrier only: invalidate in-flight work but never clear
        // capture/captureShader/installedCaptureScene. That keeps the last APP pixels visible
        // until authoritative HOME ownership takes over.
        int freezeStart = dock.indexOf("void freezeBackdropForHomeTransition(String reason)");
        int freezeEnd = dock.indexOf("void releaseBackdropFromHomeTransition", freezeStart);
        assertTrue(freezeStart >= 0 && freezeEnd > freezeStart);
        String freeze = dock.substring(freezeStart, freezeEnd);
        assertTrue(freeze.contains("cancelPendingCaptureWork()"));
        assertFalse(freeze.contains("capture = null"));
        assertFalse(freeze.contains("captureShader = null"));
        assertFalse(freeze.contains("installedCaptureScene = null"));
        assertTrue(dock.contains("if (homeTransitionBackdropFrozen)"));
    }
}
