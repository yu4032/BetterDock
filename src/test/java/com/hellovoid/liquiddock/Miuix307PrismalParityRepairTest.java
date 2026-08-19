package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.Test;

public class Miuix307PrismalParityRepairTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final float EPS = 0.0001f;

    @Test
    public void calibratedBaseMatchesCurrentUpstreamPrismalRecipe() {
        assertUpstreamBase(Miuix307PrismalMaterial.defaults(2f));
    }

    @Test
    public void legacyFallbackProfileMigratesToCurrentUpstreamBase() {
        LiquidDockConfig config = LiquidDockConfig.from(new ConfigReader(Collections.emptyMap()));
        assertUpstreamBase(Miuix307PrismalMaterial.fromConfig(config.glass, 2f));
    }

    private static void assertUpstreamBase(Miuix307PrismalMaterial.Params p) {
        assertEquals(1.55f, p.ior, EPS);
        assertEquals(36f, p.thicknessPx, EPS);
        assertEquals(1.15f, p.normalStrength, EPS);
        assertEquals(1.15f, p.displacementScale, EPS);
        assertEquals(38f, p.heightTransitionWidthPx, EPS);
        assertEquals(1.8f, p.sminSmoothingPx, EPS);
        assertEquals(20f, p.refractionInsetPx, EPS);
        assertEquals(4f, p.edgeRefractionFalloff, EPS);
        assertEquals(1.08f, p.brightness, EPS);
        assertEquals(1.22f, p.rimLight, EPS);
        assertEquals(1.52f, p.specularStrength, EPS);
        assertEquals(88f, p.specularSharp, EPS);
        assertEquals(0.28f, p.causticIntensity, EPS);
        assertEquals(-0.5f, p.lightDirX, EPS);
        assertEquals(-0.8f, p.lightDirY, EPS);
        assertEquals(10f, p.shadowSoftness, EPS);
        assertEquals(0f, p.tintR, EPS);
        assertEquals(0f, p.tintG, EPS);
        assertEquals(1f, p.tintB, EPS);
        assertEquals(35f / 255f, p.tintA, EPS);
        assertEquals(1f, p.shadowR, EPS);
        assertEquals(1f, p.shadowG, EPS);
        assertEquals(1f, p.shadowB, EPS);
        assertEquals(35f / 255f, p.shadowA, EPS);
    }

    @Test
    public void glassShaderIsPureTwoDimensionalUpstreamPrismalDomain() throws Exception {
        String shader = Files.readString(MAIN.resolve("Miuix307PrismalShader.java"));

        assertTrue(shader.contains("uniform sampler2D u_backgroundTexture"));
        assertTrue(shader.contains("uniform sampler2D u_blurredTexture"));
        assertTrue(shader.contains("uniform int       u_useBlurredTexture"));
        assertFalse(shader.contains("samplerExternalOES"));
        assertFalse(shader.contains("uTexMatrix"));
        assertFalse(shader.contains("uBackdropRect"));
        assertFalse(shader.contains("uConfigRot"));
        assertFalse(shader.contains("uBlurRadiusPx"));
        assertFalse(shader.contains("uHighlightAlpha"));
        assertFalse(shader.contains("uEdgeBand"));

        assertTrue(shader.contains("pow(smoothstep(refractionHeight, 0.0, edgeDist), 0.82)"));
        assertTrue(shader.contains("gl_FragColor = vec4(color, opacity * u_transmittance);"));
        assertFalse(shader.contains("gl_FragColor = vec4(clamp(color"));
    }

    @Test
    public void passBlurAdapterOwnsOesMappingAndProducerValidityMask() throws Exception {
        String shaders = Files.readString(MAIN.resolve("Miuix307PassBlurShaders.java"));

        assertTrue(shaders.contains("samplerExternalOES uTexture"));
        assertTrue(shaders.contains("uTexMatrix"));
        assertTrue(shaders.contains("uBackdropRect"));
        assertTrue(shaders.contains("uConfigRot"));
        assertTrue(shaders.contains("uValidDockRect"));
        assertTrue(shaders.contains("gl_FragColor = vec4(0.0)"));
        assertFalse("invalid Stage-B coordinates must not be hidden by explicit edge clamping",
                shaders.contains("return clamp(transformed.xy"));
    }

    @Test
    public void textureViewUsesHalfResolutionTwoPassGaussianAndClearsTargets() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        String shaders = Files.readString(MAIN.resolve("Miuix307PassBlurShaders.java"));

        assertTrue(view.contains("BLUR_FBO_SCALE = 0.5f"));
        assertTrue(view.contains("rawFramebuffer"));
        assertTrue(view.contains("blurFramebufferH"));
        assertTrue(view.contains("blurFramebufferV"));
        assertTrue(view.contains("renderNormalizationPass"));
        assertTrue(view.contains("renderBlurPasses"));
        assertTrue(view.contains("renderMaterialPass"));
        assertTrue(view.contains("glClearColor(0f, 0f, 0f, 0f)"));
        assertTrue(view.contains("GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)"));
        assertTrue(shaders.contains("GAUSSIAN_BLUR_FRAGMENT"));
        assertTrue(shaders.contains("uDirection"));
        assertTrue(shaders.contains("uSigma"));
    }

    @Test
    public void gaussianBlurRenormalizesOnlyProducerValidSamples() throws Exception {
        String shaders = Files.readString(MAIN.resolve("Miuix307PassBlurShaders.java"));
        String blur = shaders.substring(shaders.indexOf("GAUSSIAN_BLUR_FRAGMENT"));

        assertTrue("blur must receive the producer-valid Dock rect",
                blur.contains("uniform vec4 uValidDockRect"));
        assertTrue("invalid taps must be skipped rather than averaged as black",
                blur.contains("if (uv.x < uValidDockRect.x"));
        assertTrue("remaining valid tap weights must be renormalized",
                blur.contains("norm > 0.000001"));
        assertFalse("blur must not fold invalid pixels in through texture-edge clamping",
                blur.contains("vec2 uv = clamp(vUv + delta, 0.0, 1.0)"));
    }

    @Test
    public void prismalRefractionFadesBeforeSamplingOutsideProducerDomain() throws Exception {
        String shader = Files.readString(MAIN.resolve("Miuix307PrismalShader.java"));
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        assertTrue(shader.contains("uniform vec4  u_validBackdropRect"));
        assertTrue(shader.contains("uniform float u_backdropSafeInsetPx"));
        assertTrue(shader.contains("float backdropSafety("));
        assertTrue(shader.contains("baseOffset *= refractionSafety"));
        assertTrue("RGB dispersion must use the already-safe base offset",
                shader.contains("baseOffset + chromaPush * u_dispersionR"));
        assertTrue(view.contains("\"u_validBackdropRect\""));
        assertTrue(view.contains("\"u_backdropSafeInsetPx\""));
    }

    @Test
    public void parityPathRemainsZeroReadback() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        String shaders = Files.readString(MAIN.resolve("Miuix307PassBlurShaders.java"));
        String material = Files.readString(MAIN.resolve("Miuix307PrismalMaterial.java"));
        String shader = Files.readString(MAIN.resolve("Miuix307PrismalShader.java"));
        String all = view + shaders + material + shader;

        assertFalse(all.contains("captureScreenAsync"));
        assertFalse(all.contains("glReadPixels"));
        assertFalse(all.contains("Bitmap"));
    }
}
