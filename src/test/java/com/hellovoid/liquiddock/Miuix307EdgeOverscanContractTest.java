package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

        assertTrue("normalization FBO must keep the 32dp base and support asymmetric horizontal overscan",
                view.contains("EDGE_OVERSCAN_DP")
                        && view.contains("horizontalOverscanPx()")
                        && view.contains("leftOverscanPx")
                        && view.contains("rightOverscanPx")
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
}
