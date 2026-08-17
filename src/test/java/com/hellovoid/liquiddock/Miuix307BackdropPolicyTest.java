package com.hellovoid.liquiddock;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
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
        // Reuse the existing scene policy: HOME is wallpaper-backed, while APP and confirmed
        // RECENTS remain composed full-display sources.
        assertEquals(CaptureSourcePolicy.Source.WALLPAPER,
                CaptureSourcePolicy.sourceFor(CaptureScene.HOME, false, false));
        assertEquals(CaptureSourcePolicy.Source.FULL_DISPLAY,
                CaptureSourcePolicy.sourceFor(CaptureScene.APP, false, false));
        assertEquals(CaptureSourcePolicy.Source.FULL_DISPLAY,
                CaptureSourcePolicy.sourceFor(CaptureScene.RECENTS, false, true));

        // Mode-1 capture must not sample SystemUI layers that native pass-window blur sees above
        // the Dock rather than as part of its backdrop. Generic prefixes intentionally match
        // concrete SurfaceFlinger names such as NavigationBar0 and GestureStubLeft/Right.
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

        // The generic Dock capture code already has the correct scene barrier: a HOME target
        // immediately makes the scene revision stale for any in-flight APP bitmap, requests a
        // fresh capture, and expires after 1550 ms if the gesture is cancelled.
        assertTrue(dock.contains("void setGestureCaptureTarget(String target)"));
        assertTrue(dock.contains("sceneState.setGestureTarget(target"));
        assertTrue(dock.contains("requestStateCapture(\"gesture-target-\""));
        assertTrue(dock.contains("1550L"));

        // 307 must opt its existing full-display exclusion merge into the SystemUI-safe set;
        // legacy paths keep the old merge unchanged while Miuix307MaterialPipeline is inactive.
        assertTrue(exclusions.contains("Miuix307MaterialPipeline.isInstalled()"));

        // Decompiled 307 Launcher emits StateNotifyUtils.sendStateBroadcast(..., \"toHome\", ...)
        // at the start of APP->HOME. Bridge only that native transition to the existing HOME
        // capture target; do not restore the old generic gesture/Recents hook bundle.
        assertTrue(pipeline.contains("com.miui.home.recents.util.StateNotifyUtils"));
        assertTrue(pipeline.contains("sendStateBroadcast"));
        assertTrue(pipeline.contains("\"toHome\""));
        assertTrue(pipeline.contains("MiuixGlassHook.onHomeTransitionStart()"));
        assertTrue(glassHook.contains("static void onHomeTransitionStart()"));
        assertTrue(glassHook.contains("glass.setGestureCaptureTarget(\"HOME\")"));
    }
}
