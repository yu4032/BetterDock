package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/**
 * Historical filename; the contract is now upstream-Prismal-first.
 * LiquidDock's old RuntimeShader is only a config compatibility source.
 */
public class Miuix307PrismalLegacyParityTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path KOTLIN = Path.of("src/main/kotlin/com/hellovoid/liquiddock");

    private static String material() throws Exception {
        return Files.readString(MAIN.resolve("Miuix307PrismalMaterial.java"));
    }

    @Test
    public void zeroCopyShaderCarriesCurrentUpstreamPrismalGeometryAndOptics() throws Exception {
        String source = material();
        String[] required = new String[]{
                "smin_poly", "smax_poly", "sdRoundBox", "pxNorm", "smallGlass", "edgePunch",
                "N_meniscus", "menBlend", "dropLens", "uDisplacementScale",
                "uHeightTransitionWidth", "uSminSmoothing", "uRefractionInset",
                "uFresnelReflect", "refract(-V", "refract(refIn", "uParallaxScale"
        };
        for (String marker : required) {
            assertTrue("missing upstream Prismal geometry/optics marker: " + marker,
                    source.contains(marker));
        }
    }

    @Test
    public void zeroCopyShaderCarriesCurrentUpstreamPrismalColorReflectionAndLighting() throws Exception {
        String source = material();
        String[] required = new String[]{
                "applyVibrancy", "uVibrancy", "uPlainHighlight", "uDispersionR", "uDispersionB",
                "reflW", "reflUv", "skyHaze", "uShadowColor", "innerShadow",
                "specP", "specS", "pairOpp", "streakOpp", "uLightDir",
                "uTransmittance", "uCausticIntensity", "uGlassColor",
                "uPressProgress", "uBackdropPinch", "uGlowCenter", "uGlowStrength"
        };
        for (String marker : required) {
            assertTrue("missing upstream Prismal lighting/color marker: " + marker,
                    source.contains(marker));
        }
        assertFalse("fixed 14px diagnostic lens must never return",
                source.contains("displacementPx = 14.0"));
    }

    @Test
    public void upstreamStaticOpticalParametersAreExposedThroughLiquidDockGuiAndRuntimeConfig() throws Exception {
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
                "glass.prismalBackdropScaleY", "glass.prismalParallaxScale"
        };
        for (String field : fields) {
            assertTrue("missing GUI-to-Prismal mapping: " + field, material.contains(field));
        }
    }

    @Test
    public void upstreamModelStillUsesValidatedZeroCopyOesMappingAndLiveGuiSync() throws Exception {
        String source = material();
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));

        assertTrue(source.contains("samplerExternalOES uTexture"));
        assertTrue(source.contains("uBackdropRect"));
        assertTrue(source.contains("uConfigRot"));
        assertTrue(source.contains("textureScaleX") && source.contains("textureOffsetX"));
        assertTrue(source.contains("uTexMatrix"));
        assertTrue(view.contains("setGlassConfig(LiquidDockConfig.Glass glassConfig, int blurRadiusPx)"));
        assertTrue(renderer.contains("gpuBackdrop.setGlassConfig(glassConfig, blurRadiusPx)"));
        assertFalse(source.contains("Bitmap"));
        assertFalse(source.contains("captureScreenAsync"));
        assertFalse(source.contains("glReadPixels"));
    }
}
