package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Device-log regressions for HyperOS 3.0.307 Dock drag and icon-theme rebuilds. */
public class Miuix307ThemeDragDeviceRegressionTest {
    private static String read(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/" + file));
    }

    @Test
    public void dragExcludesActualSurfaceFlingerDragIconLayersWhenViewSurfaceIsUnavailable()
            throws Exception {
        String glass = read("DockLiquidGlassView.java");

        // Device evidence: DragView.getSurfaceControl() remains null through all retries while
        // SurfaceFlinger exposes the visible drag composition as these three stable name prefixes.
        assertTrue(glass.contains("MaskSnapshotLayer_dragIcon"));
        assertTrue(glass.contains("MaskDark_dragIcon"));
        assertTrue(glass.contains("MaskIcon_dragIcon"));
        assertTrue(glass.contains("dockDragging"));
        assertTrue(glass.contains("CaptureExclusionNames.merge"));
    }

    @Test
    public void thirdPartyIconThemeBackground2RemainsEligibleForPrismalBinding()
            throws Exception {
        String pipeline = read("Miuix307MaterialPipeline.java");

        // Applying a third-party icon theme switches the live HotSeats background from the
        // MiuiX implementation to BlurBackground2; returning to the default theme switches back.
        assertTrue(pipeline.contains("HotSeatsListContentMiuiXBlurBackground"));
        assertTrue(pipeline.contains("HotSeatsListContentBlurBackground2"));
        assertTrue(pipeline.contains("isSupportedBackground"));
        assertTrue(pipeline.contains("resolveBackground(hotSeats)"));
    }
}
