package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contracts for the strong diagnostic edge-refraction pass on the TextureView/EGL backend. */
public class Miuix307TextureViewStrongRefractionTest {
    private static final Path VIEW = Path.of(
            "src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java");

    private static String source() throws Exception {
        return Files.readString(VIEW);
    }

    @Test
    public void shaderAppliesRoundedEdgeLensBeforeBackdropMapping() throws Exception {
        String source = source();
        assertTrue(source.contains("uniform vec2 uViewSize"));
        assertTrue(source.contains("sdRoundRect"));
        assertTrue(source.contains("edgeWeight"));
        assertTrue(source.contains("refractedUv"));
        assertTrue(source.contains("lensUv = mix(vUv, refractedUv, edgeWeight)"));
        assertTrue("lens displacement must happen before mapping into the root backdrop rect",
                source.indexOf("lensUv = mix(vUv, refractedUv, edgeWeight)")
                        < source.indexOf("uBackdropRect.xy + lensUv * uBackdropRect.zw"));
    }

    @Test
    public void diagnosticDisplacementIsStrongButBounded() throws Exception {
        String source = source();
        assertTrue("diagnostic lens should use a visibly strong 14 px nominal displacement",
                source.contains("float displacementPx = 14.0"));
        assertTrue(source.contains("displacementPx / uViewSize"));
        assertTrue(source.contains("clamp(vUv - normal * displacementPx / uViewSize, 0.0, 1.0)"));
    }

    @Test
    public void centerRemainsPassthroughAndNoMaterialColorEffectsReturn() throws Exception {
        String source = source();
        assertTrue(source.contains("mix(vUv, refractedUv, edgeWeight)"));
        assertFalse(source.contains("uTint"));
        assertFalse(source.contains("uHighlight"));
        assertFalse(source.contains("chromatic"));
        assertFalse(source.contains("dispersion"));
        assertFalse(source.contains("blurRadius"));
    }

    @Test
    public void drawUploadsTextureViewSizeForPixelStableLens() throws Exception {
        String source = source();
        assertTrue(source.contains("glGetUniformLocation(program, \"uViewSize\")"));
        assertTrue(source.contains("GLES20.glUniform2f"));
        assertTrue(source.contains("outputWidth"));
        assertTrue(source.contains("outputHeight"));
    }
}
