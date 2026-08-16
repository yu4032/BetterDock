package com.hellovoid.liquiddock;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Contract for the opt-in HyperOS 3.0.307+ MiuiX material demo pipeline. */
public class Miuix307DemoPipelineContractTest {
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
        assertTrue(hook.contains("Miuix307DemoPipeline.install"));
        assertTrue(hook.contains("legacy liquid capture bypassed"));
    }

    @Test
    public void demoUsesNativeMiuixBlurWithoutCapturePipeline() throws IOException {
        Path pipeline = Paths.get("src/main/java/com/hellovoid/liquiddock/Miuix307DemoPipeline.java");
        assertTrue("demo pipeline source must exist", Files.exists(pipeline));
        String source = read(pipeline.toString());
        assertTrue(source.contains("HotSeatsListContentMiuiXBlurBackground"));
        assertTrue(source.contains("mBlurUiHelper"));
        assertTrue(source.contains("refreshBlur"));
        assertFalse(source.contains("LiveScreenCapture"));
        assertFalse(source.contains("CaptureSceneState"));
    }
}
