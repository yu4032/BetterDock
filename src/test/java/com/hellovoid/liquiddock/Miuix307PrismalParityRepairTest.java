package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.Test;

/** Numerical and zero-copy adaptation checks for the pinned Prismal v1.0.6 baseline. */
public class Miuix307PrismalParityRepairTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final float EPS = 0.0001f;

    @Test
    public void calibratedBaseMatchesPinnedPrismalV106Recipe() {
        assertPrismalV106Base(Miuix307PrismalMaterial.defaults(2f));
    }

    @Test
    public void legacyFallbackProfileMigratesToPinnedPrismalV106Base() {
        LiquidDockConfig config = LiquidDockConfig.from(new ConfigReader(Collections.emptyMap()));
        assertPrismalV106Base(Miuix307PrismalMaterial.fromConfig(config.glass, 2f));
    }

    private static void assertPrismalV106Base(Miuix307PrismalMaterial.Params p) {
        assertEquals(1.55f, p.ior, EPS);
        assertEquals(36f, p.thicknessPx, EPS);
        assertEquals(1.15f, p.normalStrength, EPS);
        assertEquals(1.15f, p.displacementScale, EPS);
        assertEquals(38f, p.heightTransitionWidthPx, EPS);
        assertEquals(1.8f, p.sminSmoothingPx, EPS);
        assertEquals(20f, p.refractionInsetPx, EPS);
        assertEquals(4f, p.edgeRefractionFalloff, EPS);
        assertEquals(26f, p.chromaticAberration, EPS);
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
    public void glassShaderIsPureTwoDimensionalPrismalV106Domain() throws Exception {
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
    public void passBlurAdapterOwnsOesMappingAndSynthesizesRefractionGuardBand() throws Exception {
        String shaders = Files.readString(MAIN.resolve("Miuix307PassBlurShaders.java"));
        String normalize = shaders.substring(shaders.indexOf("OES_NORMALIZE_FRAGMENT"),
                shaders.indexOf("GAUSSIAN_BLUR_FRAGMENT"));

        assertTrue(shaders.contains("samplerExternalOES uTexture"));
        assertTrue(shaders.contains("uTexMatrix"));
        assertTrue(shaders.contains("uBackdropRect"));
        assertTrue(shaders.contains("uConfigRot"));
        assertTrue(shaders.contains("uValidDockRect"));
        assertTrue("partial producer coverage needs a continuous GPU guard band",
                normalize.contains("float mirrorIntoValidRange("));
        assertTrue(normalize.contains("vec2 mirrorDockUv("));
        assertTrue(normalize.contains("vec2 sampleDockUv = mirrorDockUv(vUv)"));
        assertTrue(normalize.contains("uBackdropRect.xy + sampleDockUv * uBackdropRect.zw"));
        assertFalse("invalid Dock-local pixels must not remain transparent black",
                normalize.contains("gl_FragColor = vec4(0.0)"));
        assertFalse("Stage-B producer coordinates must still not collapse to a texture edge",
                shaders.contains("return clamp(transformed.xy"));
    }

    @Test
    public void portablePrismalOwnsHalfResolutionTwoPassGaussianAndClearsTargets() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        String renderer = Files.readString(Path.of(
                "prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java"));
        String blurH = Files.readString(Path.of("prismal/src/main/res/raw/prismal_blur_h.glsl"));
        String blurV = Files.readString(Path.of("prismal/src/main/res/raw/prismal_blur_v.glsl"));

        assertTrue(view.contains("rawFramebuffer") && view.contains("renderNormalizationPass"));
        assertTrue(view.contains("prismalRenderer.render("));
        assertTrue(renderer.contains("BLUR_FBO_SCALE = 0.5f"));
        assertTrue(renderer.contains("blurFramebufferH") && renderer.contains("blurFramebufferV"));
        assertTrue(renderer.contains("sourceFramebuffer") && renderer.contains("outputFramebuffer"));
        assertTrue(renderer.contains("glClearColor(0f, 0f, 0f, 0f)"));
        assertTrue(renderer.contains("GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)"));
        assertTrue(blurH.contains("for (float i = -15.0; i <= 15.0; i += 1.0)"));
        assertTrue(blurV.contains("for (float i = -15.0; i <= 15.0; i += 1.0)"));
    }

    @Test
    public void partialCoverageGuardBandProtectsBlurAndPrismalWithoutChangingOptics() throws Exception {
        String shaders = Files.readString(MAIN.resolve("Miuix307PassBlurShaders.java"));
        String shader = Files.readString(MAIN.resolve("Miuix307PrismalShader.java"));
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        assertTrue(shaders.contains("mirrorDockUv(vUv)"));
        assertFalse("Prismal v1.0.6 optical equations should stay producer-geometry agnostic",
                shader.contains("u_validBackdropRect"));
        assertTrue("visible partial coverage is still clipped to the real Dock/window intersection",
                view.contains("mapping.coverage == Miuix307BackdropMapping.Coverage.PARTIAL"));
        assertTrue(view.contains("GLES20.glScissor"));
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
