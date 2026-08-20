package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.hellovoid.prismal.PrismalParams;
import com.hellovoid.prismal.PrismalSampling;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contracts that keep Prismal refraction continuous as scene content approaches the Dock edge. */
public class Miuix307EdgeOverscanContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path PRISMAL = Path.of("prismal/src/main/java/com/hellovoid/prismal");

    @Test
    public void zeroCopyBackdropKeepsRealPixelsBeyondTheVisibleDock() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        String renderer = Files.readString(PRISMAL.resolve("PrismalRenderer.java"));
        String fragment = Files.readString(Path.of("prismal/src/main/res/raw/prismal_fragment.glsl"));

        assertTrue("normalization FBO must keep the 32dp base and asymmetric resolved insets",
                view.contains("EDGE_OVERSCAN_DP")
                        && view.contains("horizontalOverscanPx()")
                        && view.contains("SamplingInsets")
                        && view.contains("insets.left")
                        && view.contains("insets.right"));
        assertTrue("Dock adapter must place the visible glass inside the larger Prismal framebuffer",
                view.contains("createPrismalGeometry(mapping)")
                        && view.contains("mapping.dockUvLeft")
                        && view.contains("mapping.dockUvBottom")
                        && view.contains("mapping.dockUvWidth * mapping.sampleWidth")
                        && view.contains("mapping.dockUvHeight * mapping.sampleHeight"));
        assertTrue("portable Prismal must own a full-frame source and output target",
                renderer.contains("sourceFramebuffer") && renderer.contains("outputFramebuffer"));
        assertTrue("official Prismal backdrop sampling stays in framebuffer UV",
                fragment.contains("return clamp(scaled + offset, vec2(0.0), vec2(1.0));"));
    }

    @Test
    public void overscanValidityDoesNotReplaceVisibleDockScissorValidity() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        assertTrue(view.contains("validSampleLeft") && view.contains("validSampleTop"));
        assertTrue(view.contains("validDockLeft") && view.contains("validDockTop"));
        assertTrue("normalization mirror guard must use overscan-sample validity",
                view.contains("mapping.validSampleLeft, mapping.validSampleBottom")
                        && view.contains("mapping.validSampleRight, mapping.validSampleTop"));
        assertTrue("final coverage/scissor must remain tied to the visible Dock",
                view.contains("producerCoverage = dock.coverage")
                        && view.contains("mapping.validDockLeft * mapping.visibleWidth")
                        && view.contains("mapping.validDockBottom * mapping.visibleHeight"));
    }

    @Test
    public void opticalSamplingGuardCoversOfficialPrismalDisplacementBudget() {
        PrismalParams defaults = Miuix307PrismalAdapter.toPortable(
                Miuix307PrismalMaterial.defaults(1f));
        int horizontal = PrismalSampling.requiredGuardPx(defaults, 2302, 233, true);
        int vertical = PrismalSampling.requiredGuardPx(defaults, 2302, 233, false);

        assertTrue("default horizontal optics need substantially more than the old fixed 32dp ring",
                horizontal >= 256);
        assertTrue("default vertical optics must exceed the historical 16px bottom guard",
                vertical >= 128);
        assertTrue("wide Dock chromatic/bulge reach should require more horizontal guard",
                horizontal > vertical);
    }

    @Test
    public void halfResolutionGaussianBlurHaloIsOwnedByPortablePrismalSampling() throws Exception {
        String sampling = Files.readString(PRISMAL.resolve("PrismalSampling.java"));
        assertTrue(sampling.contains("BLUR_FBO_SCALE = 0.5f"));
        assertTrue(sampling.contains("BLUR_KERNEL_RADIUS = 15"));
        assertTrue(sampling.contains("BLUR_KERNEL_RADIUS / BLUR_FBO_SCALE"));
        assertTrue("blur halo must be part of the portable model sampling budget",
                sampling.contains("+ chromatic + reflection + blur + 2f"));
    }

    @Test
    public void resolvedSamplingInsetsUsePortableOpticalGuardWithoutDiscardingGuiExtras() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        assertTrue(view.contains("private SamplingInsets resolveSamplingInsets(int width, int height)"));
        assertTrue(view.contains("PrismalSampling.requiredGuardPx("));
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
