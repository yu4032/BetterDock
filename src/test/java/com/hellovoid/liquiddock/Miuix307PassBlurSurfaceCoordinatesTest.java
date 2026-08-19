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
    public void cropUsesRenderWorkerSurfacePositionBeforeLayoutFallback() throws Exception {
        String source = Files.readString(VIEW);
        int start = source.indexOf("private void updateCrop()");
        int end = source.indexOf("private void logCoordinateDiagnostics", start);
        assertTrue(start >= 0 && end > start);
        String crop = source.substring(start, end);

        assertTrue("hardware-accelerated SurfaceView diagnostics must prefer RenderWorker's final compositor rect",
                crop.contains("readSurfaceViewRenderPosition")
                        && source.contains("getSurfaceRenderPosition"));
        assertTrue("mScreenRect may remain only as a diagnostic/startup fallback",
                source.contains("readSurfaceViewScreenRect")
                        && crop.contains("readSurfaceViewScreenRect"));
        assertTrue("diagnostic crop must normalize the compositor rect against ViewRootImpl.mSurfaceSize",
                crop.contains("readSurfaceGeometry")
                        && crop.contains("surfaceWidth")
                        && crop.contains("surfaceHeight"));
        assertFalse("raw getLocationInWindow coordinates are not the hardware compositor position",
                crop.contains("getLocationInWindow"));
    }

    @Test
    public void diagnosticsExposeRenderWorkerAndLayoutRectsSeparately() throws Exception {
        String source = Files.readString(VIEW);
        assertTrue("device logs must distinguish the final RenderWorker rect from mScreenRect fallback",
                source.contains("renderRect=")
                        && source.contains("layoutRect="));
    }

    @Test
    public void shaderLeavesProducerOrientationAndCropEntirelyToSurfaceTextureMatrix() throws Exception {
        String source = Files.readString(VIEW);
        int shaderStart = source.indexOf("private static final String FRAGMENT_SHADER");
        int shaderEnd = source.indexOf("private static final class ProducerGeometry", shaderStart);
        assertTrue(shaderStart >= 0 && shaderEnd > shaderStart);
        String shader = source.substring(shaderStart, shaderEnd);

        assertTrue("SurfaceTexture transform must directly consume the lens UV",
                shader.contains("uTexMatrix * vec4(lensUv, 0.0, 1.0)"));
        assertFalse("manual top-left/bottom-left crop conversion must stay out of sampling",
                shader.contains("uCrop")
                        || shader.contains("rootUv")
                        || shader.contains("1.0 - lensUv.y"));
    }
}
