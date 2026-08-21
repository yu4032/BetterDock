package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contracts for signed top/bottom user adjustments on automatic Prismal sampling safety. */
public class Miuix307VerticalOverscanGuiContractTest {
    private static final Path MAIN = Path.of("src/main");

    @Test
    public void guiExposesSignedTopBottomSamplingExtrasAsPixelIntegers() throws Exception {
        String schema = Files.readString(MAIN.resolve("java/com/hellovoid/liquiddock/config/ConfigSchema.java"));
        String ui = Files.readString(MAIN.resolve("kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));
        String preset = Files.readString(MAIN.resolve("java/com/hellovoid/liquiddock/config/PresetManager.java"));

        assertTrue(schema.contains("SAMPLING_EXTRA_TOP = integer(")
                && schema.contains("\"liquid_sampling_extra_top\", 0, 0, 0, -256, 256"));
        assertTrue(schema.contains("SAMPLING_EXTRA_BOTTOM = integer(")
                && schema.contains("\"liquid_sampling_extra_bottom\", 0, 0, 0, -256, 256"));
        assertTrue(ui.contains("IntSpec(ConfigSchema.Glass.SAMPLING_EXTRA_TOP, \"上安全区额外值\", \"px\")"));
        assertTrue(ui.contains("IntSpec(ConfigSchema.Glass.SAMPLING_EXTRA_BOTTOM, \"下安全区额外值\", \"px\")"));
        assertTrue(ui.contains("最终上安全区 = 自动安全区 + 此值；可正可负，0 表示纯自动"));
        assertTrue(ui.contains("最终下安全区 = 自动安全区 + 此值；可正可负，0 表示纯自动"));
        assertTrue(preset.contains("values.put(\"liquid_sampling_extra_top\", 0);")
                && preset.contains("values.put(\"liquid_sampling_extra_bottom\", 0);"));
        assertFalse("signed pixel extras must stay DIRECT rather than recreate DP_TENTHS sidecars",
                preset.contains("putDp(values, \"liquid_sampling_extra_top\"")
                        || preset.contains("putDp(values, \"liquid_sampling_extra_bottom\""));
    }

    @Test
    public void zeroCopyRendererAddsVerticalExtrasAfterAutomaticOpticalGuard() throws Exception {
        String config = Files.readString(MAIN.resolve("java/com/hellovoid/liquiddock/LiquidDockConfig.java"));
        String view = Files.readString(MAIN.resolve("java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java"));

        assertTrue(config.contains("samplingExtraTopPx") && config.contains("samplingExtraBottomPx"));
        assertTrue(view.contains("topSamplingExtraPx") && view.contains("bottomSamplingExtraPx"));
        assertTrue(view.contains("height + insets.top + insets.bottom"));
        assertTrue(view.contains("combineAutoGuardAndUserExtra(opticalY, topSamplingExtraPx)"));
        assertTrue(view.contains("combineAutoGuardAndUserExtra(opticalY, bottomSamplingExtraPx)"));
        assertTrue(view.contains("insets.bottom / (float) sampleHeight"));
        assertFalse("fixed symmetric vertical overscan must not control FBO height",
                view.contains("height + overscanPx * 2"));
    }

    @Test
    public void retiredCaptureBleedKeysArePurgedWithoutBecomingSignedExtras() throws Exception {
        String migration = Files.readString(
                MAIN.resolve("java/com/hellovoid/liquiddock/config/ConfigMigration.java"));

        assertTrue(migration.contains("removeRetiredGlassPreferences(preferences)"));
        assertTrue(migration.contains("e.remove(\"liquid_capture_bleed_top\")"));
        assertTrue(migration.contains("e.remove(\"liquid_capture_bleed_bottom\")"));
        assertFalse(migration.contains("migrateCaptureBleedToPixels")
                || migration.contains("CAPTURE_BLEED_PIXELS_V4")
                || migration.contains("migrateLiquidDimensionsToDp"));
    }
}
