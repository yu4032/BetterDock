package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contracts copied from HyperOS 3.0.307 ViewRootImpl PassBlur geometry semantics. */
public class Miuix307TextureViewCoordinateParityTest {
    private static final Path VIEW = Path.of(
            "src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java");

    private static String source() throws Exception {
        return Files.readString(VIEW);
    }

    @Test
    public void quarterTurnsSwapPassBlurProducerDimensionsLikeViewRootImpl() throws Exception {
        String source = source();
        int start = source.indexOf("private ProducerGeometry readSurfaceGeometry");
        int end = source.indexOf("private void logStageBDiagnostics", start);
        assertTrue(start >= 0 && end > start);
        String region = source.substring(start, end);

        assertTrue("configRot 1/3 must use the ViewRootImpl PassBlur swapped producer domain",
                region.contains("configRotation == 1 || configRotation == 3"));
        assertTrue("quarter turns must swap mSurfaceSize into SurfaceTexture buffer dimensions",
                region.contains("bufferWidth = surfaceHeight")
                        && region.contains("bufferHeight = surfaceWidth"));
    }

    @Test
    public void backdropMappingUsesWindowFrameNotInflatedSurfaceSize() throws Exception {
        String source = source();
        int start = source.indexOf("private void updateBackdropMapping");
        int end = source.indexOf("private ProducerGeometry readSurfaceGeometry", start);
        assertTrue(start >= 0 && end > start);
        String region = source.substring(start, end);

        assertTrue("mapping must read ViewRootImpl mWinFrameInScreen",
                region.contains("mWinFrameInScreen"));
        assertTrue("host screen coordinates must be relative to the stable window frame",
                region.contains("hostScreen[0] - winFrame.left")
                        && region.contains("hostScreen[1] - winFrame.top"));
        assertTrue("mapping denominator must be window-frame content dimensions",
                region.contains("winFrame.width()") && region.contains("winFrame.height()"));
        assertFalse("mSurfaceSize is producer allocation geometry, not backdrop content geometry",
                region.contains("boundSurfaceWidth") || region.contains("boundSurfaceHeight"));
    }

    @Test
    public void diagnosticsExposeWindowFrameAndSurfaceAllocationSeparately() throws Exception {
        String source = source();
        assertTrue(source.contains("winFrame="));
        assertTrue(source.contains("rootSurface="));
        assertTrue(source.contains("producerBuffer="));
    }
}
