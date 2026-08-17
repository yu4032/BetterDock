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
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static CaptureSourcePolicy.Source sourceFor307(
            String sceneName, boolean recentsLiveConfirmed, boolean homeTransition)
            throws Exception {
        final Method method;
        try {
            method = CaptureSourcePolicy.class.getDeclaredMethod(
                    "sourceForMiuix307", CaptureScene.class, boolean.class, boolean.class);
        } catch (NoSuchMethodException missing) {
            fail("CaptureSourcePolicy must expose a 307-native backdrop selector");
            throw missing;
        }
        method.setAccessible(true);
        Class<?> scene = Class.forName("com.hellovoid.liquiddock.CaptureScene");
        Object sceneValue = Enum.valueOf((Class<? extends Enum>) scene, sceneName);
        return (CaptureSourcePolicy.Source) method.invoke(
                null, sceneValue, recentsLiveConfirmed, homeTransition);
    }

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
        // Native MiuiX switches HOME / APP->HOME to wallpaper-backed blur, so Launcher icon
        // flight animation must never be frozen into Prismal's sampled bitmap.
        assertEquals(CaptureSourcePolicy.Source.WALLPAPER,
                sourceFor307("APP", false, true));
        assertEquals(CaptureSourcePolicy.Source.WALLPAPER,
                sourceFor307("HOME", false, false));
        assertEquals(CaptureSourcePolicy.Source.WALLPAPER,
                sourceFor307("UNKNOWN", false, false));

        // APP and confirmed RECENTS still need the composed content behind the floating Dock.
        assertEquals(CaptureSourcePolicy.Source.FULL_DISPLAY,
                sourceFor307("APP", false, false));
        assertEquals(CaptureSourcePolicy.Source.FULL_DISPLAY,
                sourceFor307("RECENTS", true, false));
        assertEquals(CaptureSourcePolicy.Source.WALLPAPER,
                sourceFor307("RECENTS", false, false));

        // Mode-1 capture must not sample SystemUI layers that native pass-window blur sees above
        // the Dock rather than as part of its backdrop. Generic prefixes intentionally match
        // concrete SurfaceFlinger names such as NavigationBar0 and GestureStubLeft/Right.
        assertArrayEquals(new String[]{
                        "Floating Dock", "drag-layer",
                        "NavigationBar", "StatusBar", "GestureStub", "DockAssistantView",
                        "freeform#1"},
                exclusions307("Floating Dock", "drag-layer",
                        Arrays.asList("freeform#1", "NavigationBar", "")));

        String dock = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java"));
        String glassHook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java"));
        String pipeline = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java"));

        assertTrue(glassHook.contains("glass.setMiuix307BackdropPolicy(true)"));
        assertTrue(pipeline.contains("com.miui.home.recents.util.StateNotifyUtils"));
        assertTrue(pipeline.contains("sendStateBroadcast"));
        assertTrue(pipeline.contains("\"toHome\""));
        assertTrue(pipeline.contains("MiuixGlassHook.onHomeTransitionStart()"));
        assertTrue(dock.contains("void onMiuix307HomeTransitionStart()"));
        assertTrue(dock.contains("cancelPendingCaptureWork()"));
        assertTrue(dock.contains("miuix307-to-home-wallpaper"));
        assertTrue(dock.contains("CaptureExclusionNames.mergeMiuix307"));
    }
}
