package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class Miuix307GlassCustomizationContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path CONFIG = MAIN.resolve("config/ConfigSchema.java");
    private static final Path UI = Path.of("src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt");

    @Test
    public void legacy12SCurveIsExplicitOptInAndWiredEndToEnd() throws Exception {
        String schema = Files.readString(CONFIG);
        String config = Files.readString(MAIN.resolve("LiquidDockConfig.java"));
        String material = Files.readString(MAIN.resolve("Miuix307PrismalMaterial.java"));
        String shader = Files.readString(MAIN.resolve("Miuix307PrismalShader.java"));
        String ui = Files.readString(UI);

        assertTrue(schema.contains("LEGACY_S_CURVE"));
        assertTrue(schema.contains("\"liquid_legacy_s_curve\", 0, 0, 0, 0, 200"));
        assertTrue(config.contains("legacySCurveStrength"));
        assertTrue(material.contains("legacySCurveStrength"));
        assertTrue(material.contains("u_legacySCurveStrength"));
        assertTrue(shader.contains("uniform float u_legacySCurveStrength"));
        assertTrue(shader.contains("legacyRefractionHeight = clamp(u_glassSize.y * 0.48"));
        assertTrue(shader.contains("legacyStrength <= 1.0"));
        assertTrue(shader.contains("legacyOffset * legacyStrength"));
        assertTrue(ui.contains("v1.2 S形折射"));
        assertTrue(ui.contains("0=关闭，100=复现 v1.2.0，200=双倍"));
    }

    @Test
    public void previouslyDeadVisibleControlsReachShaderMath() throws Exception {
        String config = Files.readString(MAIN.resolve("LiquidDockConfig.java"));
        String material = Files.readString(MAIN.resolve("Miuix307PrismalMaterial.java"));
        String shader = Files.readString(MAIN.resolve("Miuix307PrismalShader.java"));
        String ui = Files.readString(UI);

        assertTrue(config.contains("depthEffect"));
        assertTrue(material.contains("lensDepthEffect"));
        assertTrue(material.contains("glass.depthEffect"));
        assertTrue(material.contains("\"u_lensDepthEffect\", p.lensDepthEffect"));
        assertTrue("edge falloff must affect shader math, not only be declared",
                occurrences(shader, "u_edgeRefractionFalloff") >= 2);
        assertTrue("highlight width must affect shader math, not only be declared",
                occurrences(shader, "u_highlightWidth") >= 2);
        assertTrue(ui.contains("ConfigSchema.Glass.DEPTH_EFFECT"));
        assertFalse(ui.contains("兼容控制 Prismal lens-depth 倍率"));
        assertFalse(ui.contains("兼容控制 Prismal 边缘高光带宽"));
    }

    @Test
    public void liquidResetButtonUsesCurrentPresetAndDecimalStorageContract() throws Exception {
        String schema = Files.readString(CONFIG);
        String ui = Files.readString(UI);

        assertTrue(ui.contains("PresetManager.defaultValues()"));
        assertTrue(ui.contains("config.storageMode() == ConfigKey.StorageMode.DP_TENTHS"));
        assertTrue(ui.contains("resetValue"));
        assertTrue("official Prismal shadow softness must remain reachable from the GUI",
                schema.contains("\"liquid_prismal_shadow_softness\", 100, 100, 100, 0, 2000"));
        assertTrue(ui.contains("透镜折射倍率"));
    }

    @Test
    public void currentGuiDescriptionsMatchZeroCopyPrismalSemantics() throws Exception {
        String ui = Files.readString(UI);

        assertTrue(ui.contains("透镜方向向中心偏转"));
        assertTrue(ui.contains("越高越集中在边缘"));
        assertTrue(ui.contains("Prismal 边缘透镜位移倍率"));
        assertTrue(ui.contains("zero-copy"));
        assertFalse(ui.contains("SurfaceFlinger 捕获分辨率"));
        assertFalse(ui.contains("动画和动态应用实时捕获的统一帧率上限"));
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int start = 0;
        while ((start = text.indexOf(needle, start)) >= 0) {
            count++;
            start += needle.length();
        }
        return count;
    }
}
