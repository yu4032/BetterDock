package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Historical filename; the contract is now upstream-Prismal-first. */
public class Miuix307PrismalLegacyParityTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path KOTLIN = Path.of("src/main/kotlin/com/hellovoid/liquiddock");

    private static String material() throws Exception {
        return Files.readString(MAIN.resolve("Miuix307PrismalMaterial.java"));
    }

    private static String shader() throws Exception {
        return Files.readString(MAIN.resolve("Miuix307PrismalShader.java"));
    }

    @Test
    public void zeroCopyShaderCarriesCurrentUpstreamPrismalGeometryAndOptics() throws Exception {
        String source = shader();
        String[] required = new String[]{
                "smin_poly", "smax_poly", "sdRoundBox", "pxNorm", "smallGlass", "edgePunch",
                "N_meniscus", "menBlend", "dropLens", "u_displacementScale",
                "u_heightTransitionWidth", "u_sminSmoothing", "u_refractionInset",
                "u_fresnelReflect", "refract(-V", "refract(refIn", "u_parallaxScale"
        };
        for (String marker : required) {
            assertTrue("missing upstream Prismal geometry/optics marker: " + marker,
                    source.contains(marker));
        }
    }

    @Test
    public void zeroCopyShaderCarriesCurrentUpstreamPrismalColorReflectionAndLighting() throws Exception {
        String source = shader();
        String[] required = new String[]{
                "applyVibrancy", "u_vibrancy", "u_plainHighlight", "u_dispersionR", "u_dispersionB",
                "reflW", "reflUv", "skyHaze", "u_shadowColor", "innerShadow",
                "specP", "specS", "pairOpp", "streakOpp", "u_lightDir",
                "u_transmittance", "u_causticIntensity", "u_glassColor",
                "u_pressProgress", "u_backdropPinch", "u_glowCenter", "u_glowStrength"
        };
        for (String marker : required) {
            assertTrue("missing upstream Prismal lighting/color marker: " + marker,
                    source.contains(marker));
        }
        assertFalse(source.contains("displacementPx = 14.0"));
        assertFalse(source.contains("uHighlightAlpha"));
        assertFalse(source.contains("uEdgeBand"));
    }

    @Test
    public void upstreamStaticOpticalParametersRemainExposedThroughLiquidDockGuiAndRuntimeConfig() throws Exception {
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
            assertTrue("missing ConfigSchema key: " + key, schema.contains(key));
            assertTrue("missing runtime config mapping: " + key, config.contains(key));
            assertTrue("missing Compose GUI control: " + key, compose.contains(key));
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
            assertTrue("missing GUI-to-Prismal mapping: " + field, material.contains(field));
        }
    }

    @Test
    public void upstreamModelUsesSeparateZeroCopyOesAdapterAndLiveGuiSync() throws Exception {
        String material = material();
        String shader = shader();
        String adapter = Files.readString(MAIN.resolve("Miuix307PassBlurShaders.java"));
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));

        assertTrue(adapter.contains("samplerExternalOES uTexture"));
        assertTrue(adapter.contains("uBackdropRect"));
        assertTrue(adapter.contains("uConfigRot"));
        assertTrue(adapter.contains("compensateSurfaceTextureCropPreservingOrientation")
                && adapter.contains("orientationBias")
                && adapter.contains("float determinant"));
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
