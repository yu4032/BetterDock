package com.hellovoid.liquiddock;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Source contract for the 307 local GPU-backed refraction overlay. */
public class Miuix307RefractionContractTest {
    private static final Path MAIN = Paths.get("src/main/java/com/hellovoid/liquiddock");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    public void refractionViewUsesLocalFullDisplayGpuCaptureWithoutLegacyStateMachine()
            throws IOException {
        Path view = MAIN.resolve("Miuix307RefractionView.java");
        assertTrue("MiuiX 307 refraction source must exist", Files.exists(view));
        String source = read(view);

        assertTrue(source.contains("uniform shader content"));
        assertTrue(source.contains("captureScreenAsync"));
        assertTrue(source.contains("MAX_CAPTURE_FPS = 30"));
        assertTrue(source.contains("MAX_CAPTURE_SCALE = 0.50f"));
        assertTrue(source.contains("CAPTURE_MODE_FULL_DISPLAY = 1"));
        assertTrue(source.contains("getLocationOnScreen"));
        assertTrue(source.contains("BitmapShader"));
        assertTrue(source.contains("setInputShader(\"content\""));

        assertFalse(source.contains("import com.hellovoid.liquiddock.CaptureSceneState"));
        assertFalse(source.contains("new CaptureSceneState("));
        assertFalse(source.contains("import com.hellovoid.liquiddock.BackdropTransitionPolicy"));
        assertFalse(source.contains("new BackdropTransitionPolicy("));
        assertFalse(source.contains("captureWallpaper("));
    }

    @Test
    public void materialPipelineUsesRefractionOverlayInsteadOfHighlightOnlyOverlay()
            throws IOException {
        String pipeline = read(MAIN.resolve("Miuix307MaterialPipeline.java"));
        assertTrue(pipeline.contains("Miuix307RefractionView"));
        assertFalse(pipeline.contains("new Miuix307HighlightView"));
    }

    @Test
    public void refractionCaptureIsBoundedAndLeavesNativeBlurVisible() throws IOException {
        String source = read(MAIN.resolve("Miuix307RefractionView.java"));
        assertTrue(source.contains("MIN_CAPTURE_SCALE = 0.25f"));
        assertTrue(source.contains("Math.min(MAX_CAPTURE_FPS"));
        assertTrue(source.contains("Math.min(MAX_CAPTURE_SCALE"));
        assertTrue(source.contains("Floating Dock"));
        assertTrue(source.contains("highlight"));
        assertTrue(source.contains("refractedAlpha"));
        assertFalse(source.contains("setMiBackgroundBlurRadius"));
        assertFalse(source.contains("setPassWindowBlurEnabled"));
    }

    @Test
    public void shaderFailureKeepsHighlightOverlayAlive() throws IOException {
        String source = read(MAIN.resolve("Miuix307RefractionView.java"));
        assertTrue(source.contains("refraction shader unavailable; highlight-only"));
        assertTrue(source.contains("if (refraction != null"));
        assertTrue(source.contains("drawHighlight(canvas"));
    }
}
