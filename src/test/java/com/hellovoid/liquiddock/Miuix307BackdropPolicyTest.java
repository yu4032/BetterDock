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
        String pipeline = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java"));
        String entry = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/ModuleMain.java"));
        String glassHook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java"));

        assertTrue(exclusions.contains("Miuix307MaterialPipeline.isInstalled()"));

        // The specialized 307 pipeline already exposes native toHome/GestureToHome boundaries.
        // Keep that route simple: it may select HOME through the established scene target, but it
        // must not install a second freeze/ownership state machine on top of the system gesture.
        assertTrue(pipeline.contains("com.miui.home.recents.util.StateNotifyUtils"));
        assertTrue(pipeline.contains("sendStateBroadcast"));
        assertTrue(pipeline.contains("\"toHome\""));
        assertTrue(pipeline.contains("MiuixGlassHook.onHomeTransitionStart()"));
        assertTrue(glassHook.contains("static void onHomeTransitionStart()"));
        assertTrue(glassHook.contains("glass.setGestureCaptureTarget(\"HOME\")"));
        assertFalse(entry.contains("Miuix307HomeTransitionFreezeHook.install()"));
    }
}
