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
                schema.contains("\"liquid_prismal_shadow_softness\", 1000, 1000, 100, 0, 2000"));
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

    @Test
    public void everyVisibleZeroCopyOpticalUniformIsActuallyConsumed() throws Exception {
        String shader = Files.readString(MAIN.resolve("Miuix307PrismalShader.java"));
        String material = Files.readString(MAIN.resolve("Miuix307PrismalMaterial.java"));
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        String[] uniforms = new String[]{
                "u_refractionInset", "u_sminSmoothing", "u_edgeRefractionFalloff",
                "u_ior", "u_glassThickness", "u_normalStrength", "u_displacementScale",
                "u_heightTransitionWidth", "u_lensRefractionPx", "u_lensDepthEffect",
                "u_chromaticAberration", "u_dispersionR", "u_dispersionB", "u_vibrancy",
                "u_plainHighlight", "u_liquidDome", "u_fresnelReflect", "u_brightness",
                "u_glassColor", "u_highlightWidth", "u_lightDir", "u_specular",
                "u_shininess", "u_rimStrength", "u_shadowColor", "u_shadowSoftness",
                "u_causticIntensity", "u_transmittance", "u_backdropSampleScale",
                "u_parallaxScale", "u_showNormals"
        };
        for (String uniform : uniforms) {
            assertTrue("visible optical uniform declared but not consumed: " + uniform,
                    occurrences(shader, uniform) >= 2);
        }
        String[] configFields = new String[]{
                "glass.blur", "glass.thickness", "glass.ior", "glass.normalStrength",
                "glass.dome", "glass.lensRefraction", "glass.depthEffect",
                "glass.chromatic", "glass.highlightWidth",
                "glass.brightness", "glass.specularStrength", "glass.specularSharp",
                "glass.rimLight", "glass.caustics", "glass.prismalRefractionInset",
                "glass.prismalDisplacementScale", "glass.prismalHeightTransitionWidth",
                "glass.prismalSminSmoothing", "glass.prismalEdgeRefractionFalloff",
                "glass.prismalFresnelReflect", "glass.prismalDispersionR",
                "glass.prismalDispersionB", "glass.prismalVibrancy",
                "glass.prismalPlainHighlight", "glass.prismalLightDirX",
                "glass.prismalLightDirY", "glass.prismalShadowSoftness",
                "glass.prismalTransmittance", "glass.prismalBackdropScaleX",
                "glass.prismalBackdropScaleY", "glass.prismalParallaxScale"
        };
        for (String field : configFields) {
            assertTrue("visible GUI field does not reach material: " + field,
                    material.contains(field));
        }
        assertTrue(view.contains("glassConfig.samplingExtraTopPx"));
        assertTrue(view.contains("glassConfig.samplingExtraBottomPx"));
        assertTrue(view.contains("glassConfig.samplingExtraLeftPx"));
        assertTrue(view.contains("glassConfig.samplingExtraRightPx"));
        assertTrue(view.contains("portablePrismalParams")
                && view.contains("prismalRenderer.render("));
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
