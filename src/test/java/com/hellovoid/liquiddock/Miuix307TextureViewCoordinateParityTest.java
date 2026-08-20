package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contracts copied from HyperOS 3.0.307 ViewRootImpl PassBlur geometry semantics. */
public class Miuix307TextureViewCoordinateParityTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path VIEW = MAIN.resolve("Miuix307PassBlurTextureView.java");

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
                region.contains("nextRotation == 1 || nextRotation == 3"));
        assertTrue("quarter turns must swap mSurfaceSize into SurfaceTexture buffer dimensions",
                region.contains("bufferWidth = surfaceHeight")
                        && region.contains("bufferHeight = surfaceWidth"));
    }

    @Test
    public void backdropMappingUsesWindowFrameAndPureCoverageHelperNotSurfaceAllocation() throws Exception {
        String source = source();
        int start = source.indexOf("private void updateBackdropMapping");
        int end = source.indexOf("private ProducerGeometry readSurfaceGeometry", start);
        assertTrue(start >= 0 && end > start);
        String region = source.substring(start, end);

        assertTrue("mapping must read ViewRootImpl mWinFrameInScreen",
                region.contains("mWinFrameInScreen"));
        assertTrue("TextureView screen coordinates must feed the pure window-frame mapping helper",
                region.contains("getLocationOnScreen(viewScreen)")
                        && region.contains("Miuix307BackdropMapping.compute"));
        assertFalse("material parent geometry must not be mixed into the TextureView/FBO UV domain",
                region.contains("materialHost.getLocationOnScreen")
                        || region.contains("materialHost.getWidth()")
                        || region.contains("materialHost.getHeight()"));
        assertTrue("mapping helper must receive window-frame content geometry",
                region.contains("winFrame.left")
                        && region.contains("winFrame.top")
                        && region.contains("winFrame.width()")
                        && region.contains("winFrame.height()"));
        assertTrue("snapshot may carry producer allocation only as generation metadata",
                region.contains("boundSurfaceWidth, boundSurfaceHeight")
                        && region.contains("boundBufferWidth, boundBufferHeight"));
        assertTrue("sample mapping must be anchored to TextureView position plus resolved insets",
                region.contains("Miuix307BackdropMapping.Result sample = Miuix307BackdropMapping.compute(")
                        && region.contains("viewScreen[0] - insets.left")
                        && region.contains("viewScreen[1] - insets.top"));
        assertTrue("visible Dock mapping must be anchored to TextureView position",
                region.contains("Miuix307BackdropMapping.Result dock = Miuix307BackdropMapping.compute(")
                        && region.contains("viewScreen[0], viewScreen[1], visibleWidth, visibleHeight"));
    }

    @Test
    public void extractedMappingKeepsUnclampedBackdropAndReportsProducerCoverage() throws Exception {
        String mapping = Files.readString(MAIN.resolve("Miuix307BackdropMapping.java"));
        assertTrue(mapping.contains("float backdropX = (hostLeft - frameLeft) / (float) frameWidth"));
        assertTrue(mapping.contains("float backdropY = 1f - (top + backdropH)"));
        assertTrue(mapping.contains("Coverage.PARTIAL"));
        assertTrue(mapping.contains("Coverage.OUTSIDE"));
        assertFalse("invalid host positions must not be hidden by clamping backdrop coordinates",
                mapping.contains("clamp01(backdropX)") || mapping.contains("clamp01(backdropY)"));
    }

    @Test
    public void diagnosticsExposeWindowFrameSurfaceAllocationAndCoverageSeparately() throws Exception {
        String source = source();
        assertTrue(source.contains("winFrame="));
        assertTrue(source.contains("rootSurface="));
        assertTrue(source.contains("producerBuffer="));
        assertTrue(source.contains("coverage="));
        assertTrue(source.contains("validDockRect="));
    }
}
