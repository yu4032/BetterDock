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
        String freeze = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/Miuix307HomeTransitionFreezeHook.java"));

        assertTrue(exclusions.contains("Miuix307MaterialPipeline.isInstalled()"));

        // Device regression: GestureToHome is emitted while APP->HOME still contains
        // SHOW_WALLPAPER/IS_WALLPAPER transition layers. Both native 307 HOME hints keep calling
        // onHomeTransitionStart(), but a specialized hook must replace that legacy immediate-HOME
        // body before it can switch Prismal to wallpaper.
        assertTrue(pipeline.contains("com.miui.home.recents.util.StateNotifyUtils"));
        assertTrue(pipeline.contains("sendStateBroadcast"));
        assertTrue(pipeline.contains("\"toHome\""));
        assertTrue(pipeline.contains("MiuixGlassHook.onHomeTransitionStart()"));
        assertTrue(entry.contains("Miuix307HomeTransitionFreezeHook.install()"));
        assertTrue(freeze.contains("MiuixGlassHook.class, \"onHomeTransitionStart\""));
        assertTrue(freeze.contains("freezeLastAppBackdrop"));
        assertTrue(freeze.contains("cancelPendingCaptureWork"));
        assertTrue(freeze.contains("return null"));

        // Once the legacy immediate HOME target is suppressed, the request/response ownership
        // resolver must be explicitly refreshed at this same native transition boundary. Otherwise
        // launcherResumed can remain APP indefinitely and the preserved APP bitmap never hands off.
        assertTrue("307 HOME freeze must trigger a fresh SystemUI ownership query",
                freeze.contains("HomeOwnershipRuntime.request(\"miuix307-toHome\")"));

        // While frozen, no observation/pointer/lifecycle request may replace the installed APP
        // bitmap. Existing onLauncherFocused() owns the configured settle timing; its focus-home
        // request is the normal release point. Exact Overview or a new Dock touch while APP is
        // still authoritative are safe cancellation paths for an interrupted HOME transition.
        assertTrue(freeze.contains("DockLiquidGlassView.class, \"requestStateCapture\""));
        assertTrue(freeze.contains("\"focus-home\""));
        assertTrue(freeze.contains("\"overview-enter-\""));
        assertTrue(freeze.contains("\"dock-touch\""));
        assertTrue(freeze.contains("launcherResumed"));
        assertTrue(freeze.contains("releaseFrozenBackdrop"));
    }
}
