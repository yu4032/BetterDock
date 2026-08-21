package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contracts for signed per-side user adjustments on top of automatic Prismal sampling safety. */
public class Miuix307HorizontalOverscanGuiContractTest {
    private static final Path MAIN = Path.of("src/main");

    @Test
    public void guiExposesSignedSamplingExtrasWithZeroAsPureAutomatic() throws Exception {
        String schema = Files.readString(MAIN.resolve("java/com/hellovoid/liquiddock/config/ConfigSchema.java"));
        String ui = Files.readString(MAIN.resolve("kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));
        String preset = Files.readString(MAIN.resolve("java/com/hellovoid/liquiddock/config/PresetManager.java"));

        assertTrue(schema.contains("SAMPLING_EXTRA_LEFT = integer(")
                && schema.contains("\"liquid_sampling_extra_left\", 0, 0, 0, -256, 256"));
        assertTrue(schema.contains("SAMPLING_EXTRA_RIGHT = integer(")
                && schema.contains("\"liquid_sampling_extra_right\", 0, 0, 0, -256, 256"));
        assertTrue(ui.contains("左安全区额外值") && ui.contains("右安全区额外值"));
        assertTrue(ui.contains("可正可负，0 表示纯自动"));
        assertTrue(preset.contains("values.put(\"liquid_sampling_extra_left\", 0);")
                && preset.contains("values.put(\"liquid_sampling_extra_right\", 0);"));
    }

    @Test
    public void zeroCopyRendererAddsSignedUserExtrasAfterAutomaticGuard() throws Exception {
        String config = Files.readString(MAIN.resolve("java/com/hellovoid/liquiddock/LiquidDockConfig.java"));
        String view = Files.readString(MAIN.resolve("java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java"));

        assertTrue(config.contains("samplingExtraLeftPx") && config.contains("samplingExtraRightPx"));
        assertTrue(view.contains("leftSamplingExtraPx") && view.contains("rightSamplingExtraPx"));
        assertTrue(view.contains("int autoHorizontal = Math.max(horizontalOverscanPx(), opticalX);"));
        assertTrue(view.contains("combineAutoGuardAndUserExtra(autoHorizontal, leftSamplingExtraPx)"));
        assertTrue(view.contains("combineAutoGuardAndUserExtra(autoHorizontal, rightSamplingExtraPx)"));
        assertTrue(view.contains("width + insets.left + insets.right"));
        assertTrue(view.contains("viewScreen[0] - insets.left"));
        assertTrue(view.contains("insets.left / (float) sampleWidth"));
        assertFalse("horizontal FBO width must not assume symmetric overscan",
                view.contains("width + overscanPx * 2"));
    }
}
