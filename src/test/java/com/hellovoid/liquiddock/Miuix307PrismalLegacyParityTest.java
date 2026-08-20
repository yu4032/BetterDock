package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/**
 * Frozen parity contract against styropyr0/Prismal View/OpenGL release v1.0.6.
 *
 * Upstream provenance verified on 2026-08-20:
 * - tag v1.0.6 -> commit bf3c6b4dd020e39675cd6226b868f95cfb8e7b66
 * - PrismalLiquidGlass.kt blob e22577b274c53cd2823df9070b567aa05d876b18
 * - fragment_shader.glsl blob 0ddc74b2ee3293fb5d6de31bf8fb39b07891ca2f
 * - v1.0.6..master changes only README; rendering sources are unchanged.
 *
 * LiquidDock therefore treats v1.0.6 as the legacy iOS-26-era optical baseline, not as an
 * iOS-27 material model. "iOS-26-era" is a LiquidDock provenance classification, not an
 * upstream Prismal release label. A future iOS-27-style migration must update this pinned
 * provenance and the numerical/model contracts deliberately.
 */
public class Miuix307PrismalLegacyParityTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path KOTLIN = Path.of("src/main/kotlin/com/hellovoid/liquiddock");
    private static final String PRISMAL_BASELINE =
            "Prismal v1.0.6 @ bf3c6b4dd020e39675cd6226b868f95cfb8e7b66";

    private static String material() throws Exception {
        return Files.readString(MAIN.resolve("Miuix307PrismalMaterial.java"));
    }

    private static String shader() throws Exception {
        return Files.readString(MAIN.resolve("Miuix307PrismalShader.java"));
    }

    @Test
    public void zeroCopyShaderCarriesPrismalV106GeometryAndOptics() throws Exception {
        String source = shader();
        String[] required = new String[]{
                "smin_poly", "smax_poly", "sdRoundBox", "pxNorm", "smallGlass", "edgePunch",
                "N_meniscus", "menBlend", "dropLens", "u_displacementScale",
                "u_heightTransitionWidth", "u_sminSmoothing", "u_refractionInset",
                "u_fresnelReflect", "refract(-V", "refract(refIn", "u_parallaxScale"
        };
        for (String marker : required) {
            assertTrue("missing " + PRISMAL_BASELINE + " geometry/optics marker: " + marker,
                    source.contains(marker));
        }
    }

    @Test
    public void zeroCopyShaderCarriesPrismalV106ColorReflectionAndLighting() throws Exception {
        String source = shader();
        String[] required = new String[]{
                "applyVibrancy", "u_vibrancy", "u_plainHighlight", "u_dispersionR", "u_dispersionB",
                "reflW", "reflUv", "skyHaze", "u_shadowColor", "innerShadow",
                "specP", "specS", "pairOpp", "streakOpp", "u_lightDir",
                "u_transmittance", "u_causticIntensity", "u_glassColor",
                "u_pressProgress", "u_backdropPinch", "u_glowCenter", "u_glowStrength"
        };
        for (String marker : required) {
            assertTrue("missing " + PRISMAL_BASELINE + " lighting/color marker: " + marker,
                    source.contains(marker));
        }
        assertFalse(source.contains("displacementPx = 14.0"));
        assertFalse(source.contains("uHighlightAlpha"));
        assertFalse(source.contains("uEdgeBand"));
    }

    @Test
    public void prismalV106StaticOpticalParametersRemainExposedThroughLiquidDockGuiAndRuntimeConfig()
            throws Exception {
        String schema = Files.readString(MAIN.resolve("config/ConfigSchema.java"));
        String config = Files.readString(MAIN.resolve("LiquidDockConfig.java"));
        String compose = Files.readString(KOTLIN.resolve("ComposeSettingsActivity.kt"));
        String material = material();

        String[] keys = new String[]{
                "PRISMAL_REFRACTION_INSET", "PRISMAL_DISPLACEMENT_SCALE",
                "PRISMAL_HEIGHT_TRANSITION_WIDTH", "PRISMAL_SMIN_SMOOTHING",
                "PRISMAL_EDGE_REFRACTION_FALLOFF", "PRISMAL_FRESNEL_REFLECT",
                "PRISMAL_DISPERSION_R", "PRISMAL_DISPERSION_B", "PRISMAL_VIBRANCY",
                "PRISMAL_PLAIN_HIGHLIGHT", "PRISMAL_LIGHT_DIR_X", "PRISMAL_LIGHT_DIR_Y",
                "PRISMAL_SHADOW_RED", "PRISMAL_SHADOW_GREEN", "PRISMAL_SHADOW_BLUE",
                "PRISMAL_SHADOW_ALPHA", "PRISMAL_SHADOW_SOFTNESS",
                "PRISMAL_TRANSMITTANCE", "PRISMAL_BACKDROP_SCALE_X",
                "PRISMAL_BACKDROP_SCALE_Y", "PRISMAL_PARALLAX_SCALE"
        };
        for (String key : keys) {
            assertTrue("missing ConfigSchema key for " + PRISMAL_BASELINE + ": " + key,
                    schema.contains(key));
            assertTrue("missing runtime config mapping for " + PRISMAL_BASELINE + ": " + key,
                    config.contains(key));
            assertTrue("missing Compose GUI control for " + PRISMAL_BASELINE + ": " + key,
                    compose.contains(key));
        }

        String[] fields = new String[]{
                "glass.prismalRefractionInset", "glass.prismalDisplacementScale",
                "glass.prismalHeightTransitionWidth", "glass.prismalSminSmoothing",
                "glass.prismalFresnelReflect", "glass.prismalDispersionR", "glass.prismalDispersionB",
                "glass.prismalVibrancy", "glass.prismalPlainHighlight", "glass.prismalLightDirX",
                "glass.prismalLightDirY", "glass.prismalShadowAlpha", "glass.prismalShadowSoftness",
                "glass.prismalTransmittance", "glass.prismalBackdropScaleX",
                "glass.prismalBackdropScaleY", "glass.prismalParallaxScale", "glass.blur"
        };
        for (String field : fields) {
            assertTrue("missing GUI-to-" + PRISMAL_BASELINE + " mapping: " + field,
                    material.contains(field));
        }
    }

    @Test
    public void prismalV106ModelUsesSeparateZeroCopyOesAdapterAndLiveGuiSync() throws Exception {
        String material = material();
        String shader = shader();
        String adapter = Files.readString(MAIN.resolve("Miuix307PassBlurShaders.java"));
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));

        assertTrue(adapter.contains("samplerExternalOES uTexture"));
        assertTrue(adapter.contains("uBackdropRect"));
        assertTrue(adapter.contains("uConfigRot"));
        assertTrue(adapter.contains("textureScaleX") && adapter.contains("textureOffsetX"));
        assertTrue(adapter.contains("uTexMatrix"));
        assertFalse(shader.contains("samplerExternalOES"));
        assertFalse(shader.contains("uBackdropRect"));
        assertTrue(view.contains("setGlassConfig(LiquidDockConfig.Glass glassConfig)"));
        assertTrue(renderer.contains("gpuBackdrop.setGlassConfig(glassConfig)"));
        assertTrue(material.contains("fromConfig(LiquidDockConfig.Glass glass, float density)"));
        assertFalse(adapter.contains("Bitmap") || adapter.contains("captureScreenAsync")
                || adapter.contains("glReadPixels"));
    }
}
