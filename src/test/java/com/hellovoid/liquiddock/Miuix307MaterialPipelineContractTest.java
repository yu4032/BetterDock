package com.hellovoid.liquiddock;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Contract for the opt-in HyperOS 3.0.307+ MiuiX material pipeline. */
public class Miuix307MaterialPipelineContractTest {
    private static String read(String path) throws IOException {
        return Files.readString(Paths.get(path), StandardCharsets.UTF_8);
    }

    @Test
    public void switchIsExplicitAndDefaultsOff() throws IOException {
        String schema = read("src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java");
        String compose = read("src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt");
        assertTrue(schema.contains("MIUIX_307_PIPELINE"));
        assertTrue(schema.contains("\"liquid_miuix_307_pipeline\""));
        assertTrue(compose.contains("ConfigSchema.Glass.MIUIX_307_PIPELINE"));
        assertTrue(compose.contains("HyperOS 3.0.307+ 新材质管线"));
    }

    @Test
    public void enabledSwitchSelectsNewPipelineBeforeLegacyCapture() throws IOException {
        String config = read("src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java");
        String hook = read("src/main/java/com/hellovoid/liquiddock/MainHook.java");
        assertTrue(config.contains("miuix307Pipeline"));
        assertTrue(hook.contains("config.glass.miuix307Pipeline"));
        assertTrue(hook.contains("Miuix307MaterialPipeline.install"));
        assertTrue(hook.contains("legacy liquid capture bypassed"));
    }

    @Test
    public void materialPipelineKeepsNativeMiuixBlurAndAddsPrismalGlass() throws IOException {
        Path pipelinePath = Paths.get(
                "src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java");
        Path glassHookPath = Paths.get(
                "src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java");
        assertTrue("material pipeline source must exist", Files.exists(pipelinePath));
        assertTrue("MiuiX glass hook source must exist", Files.exists(glassHookPath));

        String pipeline = read(pipelinePath.toString());
        String glassHook = read(glassHookPath.toString());
        String blurBridge = read("src/main/java/com/hellovoid/liquiddock/MiBlurBridge.java");

        assertTrue(pipeline.contains("HotSeatsListContentMiuiXBlurBackground"));
        assertTrue(pipeline.contains("MiuixGlassHook.install"));
        assertTrue(glassHook.contains("LiquidGlassFactory.create"));
        assertTrue(glassHook.contains("DockLiquidGlassHostView"));
        assertTrue(glassHook.contains("MiBlurBridge.applyPassWindowBlur"));
        assertTrue(blurBridge.contains("setPassWindowBlurEnabled"));
        assertFalse(glassHook.contains("setBackground(null)"));
        assertFalse(pipeline.contains("LiveScreenCapture"));
        assertFalse(pipeline.contains("CaptureSceneState"));
    }

    @Test
    public void sideSwipeHomeGestureConvergesOnExistingWallpaperPrearm() throws IOException {
        String pipeline = read(
                "src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java");

        assertTrue("307 side-swipe HOME boundary must hook the native GestureToHome event",
                pipeline.contains("com.miui.home.launcher.dock.v3.GestureToHome"));
        assertTrue("GestureToHome must cover runtime constructor variants",
                pipeline.contains("getDeclaredConstructors()"));
        assertTrue("GestureToHome hook must use reflected constructor identity",
                pipeline.contains("HookUtil.hook(ctor"));
        assertTrue("side-swipe HOME must reuse the existing wallpaper prearm",
                pipeline.contains("MiuixGlassHook.onHomeTransitionStart()"));

        // Keep the already-working vendor state-broadcast boundary as a second signal. Both
        // paths must converge on the same semantic entry point instead of duplicating state.
        assertTrue(pipeline.contains("com.miui.home.recents.util.StateNotifyUtils"));
        assertTrue(pipeline.contains("\"toHome\""));
        int first = pipeline.indexOf("MiuixGlassHook.onHomeTransitionStart()");
        int second = pipeline.indexOf("MiuixGlassHook.onHomeTransitionStart()", first + 1);
        assertTrue("GestureToHome and StateNotifyUtils must both call the same prearm method",
                first >= 0 && second > first);
        assertTrue("GestureToHome compatibility hook must fail open on vendor changes",
                pipeline.contains("GestureToHome prearm unavailable"));
    }
}
