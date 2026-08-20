package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contracts for restoring independent top/bottom zero-copy overscan controls in the GUI. */
public class Miuix307VerticalOverscanGuiContractTest {
    private static final Path MAIN = Path.of("src/main");

    @Test
    public void guiExposesLegacyTopBottomSamplingKeysAsPixelIntegers() throws Exception {
        String schema = Files.readString(MAIN.resolve("java/com/hellovoid/liquiddock/config/ConfigSchema.java"));
        String ui = Files.readString(MAIN.resolve("kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));
        String preset = Files.readString(MAIN.resolve("java/com/hellovoid/liquiddock/config/PresetManager.java"));

        assertTrue(schema.contains("CAPTURE_BLEED_TOP = integer(")
                && schema.contains("\"liquid_capture_bleed_top\", 48, 48, 48, 0, 256"));
        assertTrue(schema.contains("CAPTURE_BLEED_BOTTOM = integer(")
                && schema.contains("\"liquid_capture_bleed_bottom\", 16, 16, 16, 0, 256"));
        assertTrue(ui.contains("IntSpec(ConfigSchema.Glass.CAPTURE_BLEED_TOP, \"上额外采样高度\", \"px\")"));
        assertTrue(ui.contains("IntSpec(ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM, \"下额外采样高度\", \"px\")"));
        assertTrue(preset.contains("values.put(\"liquid_capture_bleed_top\", 48);")
                && preset.contains("values.put(\"liquid_capture_bleed_bottom\", 16);"));
        assertFalse("pixel-valued bleed defaults must not recreate DP_TENTHS sidecars",
                preset.contains("putDp(values, \"liquid_capture_bleed_top\"")
                        || preset.contains("putDp(values, \"liquid_capture_bleed_bottom\""));
    }

    @Test
    public void zeroCopyRendererUsesIndependentVerticalOverscanValues() throws Exception {
        String config = Files.readString(MAIN.resolve("java/com/hellovoid/liquiddock/LiquidDockConfig.java"));
        String view = Files.readString(MAIN.resolve("java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java"));

        assertTrue(config.contains("captureBleedTopPx") && config.contains("captureBleedBottomPx"));
        assertTrue(view.contains("horizontalOverscanPx")
                && view.contains("topOverscanPx")
                && view.contains("bottomOverscanPx"));
        assertTrue(view.contains("height + insets.top + insets.bottom"));
        assertTrue(view.contains(
                "Math.max(Math.max(0, topOverscanPx), opticalY)"));
        assertTrue(view.contains(
                "Math.max(Math.max(0, bottomOverscanPx), opticalY)"));
        assertTrue(view.contains("insets.bottom / (float) sampleHeight"));
        assertFalse("fixed symmetric vertical overscan must no longer control FBO height",
                view.contains("height + overscanPx * 2"));
    }

    @Test
    public void unsupportedGlassGenerationDropsBleedInsteadOfConvertingIt() throws Exception {
        String migration = Files.readString(
                MAIN.resolve("java/com/hellovoid/liquiddock/config/ConfigMigration.java"));

        assertTrue(migration.contains("resetUnsupportedGlassConfigGeneration(preferences)"));
        assertTrue(migration.contains("key.startsWith(\"liquid_\")"));
        assertFalse(migration.contains("migrateCaptureBleedToPixels")
                || migration.contains("CAPTURE_BLEED_PIXELS_V4")
                || migration.contains("migrateLiquidDimensionsToDp"));
    }
}
