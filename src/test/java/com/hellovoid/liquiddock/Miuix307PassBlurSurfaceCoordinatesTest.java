package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contracts for mapping the independent SurfaceView into the PassBlur producer texture. */
public class Miuix307PassBlurSurfaceCoordinatesTest {
    private static final Path VIEW = Path.of(
            "src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurGpuView.java");

    @Test
    public void cropUsesSurfaceViewCompositorRectInsteadOfRawWindowCoordinates() throws Exception {
        String source = Files.readString(VIEW);
        int start = source.indexOf("private void updateCrop()");
        int end = source.indexOf("private void logCoordinateDiagnostics", start);
        assertTrue(start >= 0 && end > start);
        String crop = source.substring(start, end);

        assertTrue("crop must read SurfaceView.mScreenRect, the rect actually used to position its SurfaceControl",
                crop.contains("readSurfaceViewScreenRect") && source.contains("\"mScreenRect\""));
        assertTrue("crop must normalize against ViewRootImpl.mSurfaceSize rather than root View dimensions",
                crop.contains("readSurfaceGeometry")
                        && crop.contains("surfaceWidth")
                        && crop.contains("surfaceHeight"));
        assertFalse("raw getLocationInWindow coordinates are in the wrong space under configRot",
                crop.contains("getLocationInWindow"));
        assertFalse("crop must not pre-flip Y before SurfaceTexture's transform matrix",
                crop.contains("1f - (top + height)")
                        || crop.contains("1.0f - (top + height)"));
    }

    @Test
    public void shaderConvertsLocalBottomLeftUvToSurfaceTopLeftOnlyOnce() throws Exception {
        String source = Files.readString(VIEW);

        assertTrue("SurfaceTexture transform remains the final buffer sampling transform",
                source.contains("uTexMatrix * vec4(rootUv, 0.0, 1.0)"));
        assertTrue("local GLSL bottom-left UV must be converted to the Surface top-left axis before that matrix",
                source.contains("uCrop.y + (1.0 - lensUv.y) * uCrop.w"));
        assertFalse("old shader path must not advance surface Y in the same direction as GL vUv",
                source.contains("uCrop.y + lensUv.y * uCrop.w"));
    }
}
