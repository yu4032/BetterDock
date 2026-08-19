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

        assertTrue("normalization FBO must include an overscan ring around the visible Dock",
                view.contains("EDGE_OVERSCAN_DP")
                        && view.contains("overscanPx")
                        && view.contains("uDockUvRect"));
        assertTrue("Prismal must map Dock-local UV into the larger overscan texture",
                shader.contains("uniform vec4  u_dockUvRect")
                        && shader.contains("u_dockUvRect.xy + dockUv * u_dockUvRect.zw"));
        assertFalse("Dock-local UV must not be clamped before it can enter the overscan ring",
                shader.contains("return clamp(scaled + offset, vec2(0.0), vec2(1.0));"));
    }
}
