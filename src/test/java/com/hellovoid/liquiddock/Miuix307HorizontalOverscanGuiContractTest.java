package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contracts for independent left/right extra zero-copy overscan controls in the GUI. */
public class Miuix307HorizontalOverscanGuiContractTest {
    private static final Path MAIN = Path.of("src/main");

    @Test
    public void guiExposesLeftRightExtraSamplingAsDirectPixels() throws Exception {
        String schema = Files.readString(MAIN.resolve("java/com/hellovoid/liquiddock/config/ConfigSchema.java"));
        String ui = Files.readString(MAIN.resolve("kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));
        String preset = Files.readString(MAIN.resolve("java/com/hellovoid/liquiddock/config/PresetManager.java"));

        assertTrue(schema.contains("CAPTURE_BLEED_LEFT = integer(")
                && schema.contains("\"liquid_capture_bleed_left\", 0, 0, 0, 0, 256"));
        assertTrue(schema.contains("CAPTURE_BLEED_RIGHT = integer(")
                && schema.contains("\"liquid_capture_bleed_right\", 0, 0, 0, 0, 256"));
        assertTrue(ui.contains("IntSpec(ConfigSchema.Glass.CAPTURE_BLEED_LEFT, \"左额外采样宽度\", \"px\")"));
        assertTrue(ui.contains("IntSpec(ConfigSchema.Glass.CAPTURE_BLEED_RIGHT, \"右额外采样宽度\", \"px\")"));
        assertTrue(preset.contains("values.put(\"liquid_capture_bleed_left\", 0);")
                && preset.contains("values.put(\"liquid_capture_bleed_right\", 0);"));
        assertFalse(preset.contains("putDp(values, \"liquid_capture_bleed_left\"")
                || preset.contains("putDp(values, \"liquid_capture_bleed_right\""));
    }

    @Test
    public void zeroCopyRendererAddsIndependentLeftRightPixelsToBaseOverscan() throws Exception {
        String config = Files.readString(MAIN.resolve("java/com/hellovoid/liquiddock/LiquidDockConfig.java"));
        String view = Files.readString(MAIN.resolve("java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java"));

        assertTrue(config.contains("captureBleedLeftPx") && config.contains("captureBleedRightPx"));
        assertTrue(view.contains("leftExtraOverscanPx") && view.contains("rightExtraOverscanPx"));
        assertTrue(view.contains(
                "Math.max(horizontalOverscanPx() + Math.max(0, leftExtraOverscanPx), opticalX)"));
        assertTrue(view.contains(
                "Math.max(horizontalOverscanPx() + Math.max(0, rightExtraOverscanPx), opticalX)"));
        assertTrue(view.contains("width + insets.left + insets.right"));
        assertTrue(view.contains("viewScreen[0] - insets.left"));
        assertTrue(view.contains("insets.left / (float) sampleWidth"));
        assertFalse("horizontal FBO width must no longer assume symmetric overscan",
                view.contains("width + overscanPx * 2"));
    }
}
