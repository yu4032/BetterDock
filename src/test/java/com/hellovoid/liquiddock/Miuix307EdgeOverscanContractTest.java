package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contracts that keep Prismal refraction continuous as scene content approaches the Dock edge. */
public class Miuix307EdgeOverscanContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void zeroCopyBackdropKeepsRealPixelsBeyondTheVisibleDock() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        String shader = Files.readString(MAIN.resolve("Miuix307PrismalShader.java"));

        assertTrue("normalization FBO must keep the 32dp base and asymmetric resolved insets",
                view.contains("EDGE_OVERSCAN_DP")
                        && view.contains("horizontalOverscanPx()")
                        && view.contains("SamplingInsets")
                        && view.contains("insets.left")
                        && view.contains("insets.right")
                        && view.contains("uDockUvRect"));
        assertTrue("Prismal must map Dock-local UV into the larger overscan texture",
                shader.contains("uniform vec4  u_dockUvRect")
                        && shader.contains("u_dockUvRect.xy + dockUv * u_dockUvRect.zw"));
        assertFalse("Dock-local UV must not be clamped before it can enter the overscan ring",
                shader.contains("return clamp(scaled + offset, vec2(0.0), vec2(1.0));"));
    }

    @Test
    public void overscanValidityDoesNotReplaceVisibleDockScissorValidity() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        assertTrue(view.contains("validSampleLeft") && view.contains("validSampleTop"));
        assertTrue(view.contains("validDockLeft") && view.contains("validDockTop"));
        assertTrue("normalization mirror guard must use overscan-sample validity",
                view.contains("validSampleLeft, validSampleBottom, validSampleRight, validSampleTop"));
        assertTrue("material coverage/scissor must remain tied to the visible Dock",
                view.contains("producerCoverage = dock.coverage")
                        && view.contains("validDockLeft * outputWidth")
                        && view.contains("validDockBottom * outputHeight"));
    }

    @Test
    public void opticalSamplingGuardCoversCurrentPrismalDisplacementBudget() throws Exception {
        Method method;
        try {
            method = Miuix307PrismalMaterial.class.getDeclaredMethod(
                    "requiredSampleGuardPx",
                    Miuix307PrismalMaterial.Params.class, int.class, int.class, boolean.class);
        } catch (NoSuchMethodException missing) {
            fail("Miuix307PrismalMaterial.requiredSampleGuardPx must size overscan from optics");
            return;
        }
        method.setAccessible(true);
        Miuix307PrismalMaterial.Params defaults = Miuix307PrismalMaterial.defaults(1f);
        int horizontal = (Integer) method.invoke(null, defaults, 2302, 233, true);
        int vertical = (Integer) method.invoke(null, defaults, 2302, 233, false);

        assertTrue("default horizontal optics need substantially more than the old fixed 32dp ring",
                horizontal >= 256);
        assertTrue("default vertical optics must exceed the historical 16px bottom guard",
                vertical >= 128);
        assertTrue("wide Dock chromatic/bulge reach should require more horizontal guard",
                horizontal > vertical);
    }

    @Test
    public void halfResolutionGaussianBlurAddsItsOwnFullResolutionHalo() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        assertTrue(view.contains("private static final int BLUR_KERNEL_RADIUS_TEXELS = 15;"));
        assertTrue(view.contains("private int blurSamplingGuardPx()"));
        assertTrue(view.contains(
                "BLUR_KERNEL_RADIUS_TEXELS / Math.max(BLUR_FBO_SCALE, 0.0001f)"));
        assertTrue("blur halo must be added after the material optical reach is known",
                view.contains("int opticalX = Miuix307PrismalMaterial.requiredSampleGuardPx(")
                        && view.contains("int opticalY = Miuix307PrismalMaterial.requiredSampleGuardPx(")
                        && view.contains("int blurGuard = blurSamplingGuardPx();")
                        && view.contains("opticalX += blurGuard;")
                        && view.contains("opticalY += blurGuard;"));
    }

    @Test
    public void resolvedSamplingInsetsUseOpticalGuardAsAMinimumWithoutDiscardingGuiExtras() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        assertTrue(view.contains("private SamplingInsets resolveSamplingInsets(int width, int height)"));
        assertTrue(view.contains("Miuix307PrismalMaterial.requiredSampleGuardPx("));
        assertTrue(view.contains("Math.max(horizontalOverscanPx() + Math.max(0, leftExtraOverscanPx), opticalX)"));
        assertTrue(view.contains("Math.max(horizontalOverscanPx() + Math.max(0, rightExtraOverscanPx), opticalX)"));
        assertTrue(view.contains("Math.max(Math.max(0, topOverscanPx), opticalY)"));
        assertTrue(view.contains("Math.max(Math.max(0, bottomOverscanPx), opticalY)"));
    }

    @Test
    public void oversizedSamplingInsetsFitInsideTheGpuTextureLimitWithoutLosingAsymmetry() throws Exception {
        Method method;
        try {
            method = Miuix307PassBlurTextureView.class.getDeclaredMethod(
                    "fitInsetPairToTextureLimit",
                    int.class, int.class, int.class, int.class);
        } catch (NoSuchMethodException missing) {
            fail("fitInsetPairToTextureLimit must constrain overscan before FBO allocation");
            return;
        }
        method.setAccessible(true);

        int[] symmetric = (int[]) method.invoke(null, 2302, 3500, 3500, 8192);
        assertEquals(5890, symmetric[0] + symmetric[1]);
        assertTrue(Math.abs(symmetric[0] - symmetric[1]) <= 1);

        int[] asymmetric = (int[]) method.invoke(null, 2302, 4000, 2000, 8192);
        assertEquals(5890, asymmetric[0] + asymmetric[1]);
        assertTrue("hardware limiting should preserve the requested left/right proportion",
                Math.abs(asymmetric[0] - asymmetric[1] * 2) <= 2);

        int[] alreadyFits = (int[]) method.invoke(null, 2302, 300, 150, 8192);
        assertEquals(300, alreadyFits[0]);
        assertEquals(150, alreadyFits[1]);
    }

    @Test
    public void textureLimitIsQueriedBeforeFirstFboAllocationAndSharedByMapping() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        assertTrue(view.contains("private volatile int maxTextureSize;"));
        assertTrue(view.contains("GLES20.GL_MAX_TEXTURE_SIZE"));
        assertTrue(view.contains("private void queryMaxTextureSize()"));
        assertTrue(view.contains("fitInsetPairToTextureLimit(width, left, right, maxTextureSize)"));
        assertTrue(view.contains("fitInsetPairToTextureLimit(height, top, bottom, maxTextureSize)"));
        assertTrue("first FBO allocation must wait until the queried limit is reflected in mapping",
                view.contains("queryMaxTextureSize();")
                        && view.contains("updateBackdropMapping();")
                        && view.contains("finishOutputAttach"));
    }
}
