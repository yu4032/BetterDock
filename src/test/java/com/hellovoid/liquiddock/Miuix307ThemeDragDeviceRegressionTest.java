package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Device/decompilation regressions for HyperOS 3.0.307 Dock drag and icon-theme rebuilds. */
public class Miuix307ThemeDragDeviceRegressionTest {
    private static String read(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/" + file));
    }

    @Test
    public void launcherDragObjectUsesArrayCompatibleDragViewResolution() throws Exception {
        String drag = read("Miuix307DragCaptureHook.java");

        // Decompiled 307 Launcher declares DragObject.mDragViews as DragView[], not List.
        assertTrue(drag.contains("java.lang.reflect.Array"));
        assertTrue(drag.contains("views.getClass().isArray()"));
        assertTrue(drag.contains("Array.getLength(views)"));
        assertTrue(drag.contains("Array.get(views, 0)"));
    }

    @Test
    public void systemDockDragFreezesCaptureAtMiuiDragListenerBoundary() throws Exception {
        String drag = read("Miuix307DragCaptureHook.java");
        String glass = read("DockLiquidGlassView.java");

        // startDragInDockForSystem() delegates to MIUI startDragAndDrop; its anonymous listener
        // implements IMiuiDragListener. These system-owned drag surfaces cannot be reliably
        // addressed by Launcher SurfaceControl, so preserve the last clean backdrop instead.
        assertTrue(drag.contains("android.view.IMiuiDragListener"));
        assertTrue(drag.contains("setSystemDockDragActive(true)"));
        assertTrue(drag.contains("setSystemDockDragActive(false)"));
        assertTrue(glass.contains("systemDockDragActive"));
        assertTrue(glass.contains("Liquid capture frozen: system Dock drag"));
        assertTrue(glass.contains("cancelPendingCaptureWork()"));
        assertTrue(glass.contains("system-dock-drag-end"));
    }

    @Test
    public void disprovenSystemDragLayerNameGuessIsRemoved() throws Exception {
        String exclusions = read("CaptureExclusionNames.java");

        // Device testing showed mode-1 name exclusions did not remove MIUI DragAndDrop masks.
        assertFalse(exclusions.contains("MaskSnapshotLayer_dragIcon"));
        assertFalse(exclusions.contains("MaskDark_dragIcon"));
        assertFalse(exclusions.contains("MaskIcon_dragIcon"));
    }

    @Test
    public void thirdPartyBackground2ClearsVendorBlurAndLetsPrismalOwnBlur() throws Exception {
        String pipeline = read("Miuix307MaterialPipeline.java");
        String glassHook = read("MiuixGlassHook.java");

        // Decompiled BlurBackground2.onAttachedToWindow/setBackgroundRadius call addBlur(), whose
        // implementation calls BlurUtilities.setBackgroundBlur + setBackgroundBlurAlpha.
        // It is therefore not the MiuiX native-material owner and must not be stacked underneath
        // an optical-only Prismal layer.
        assertTrue(pipeline.contains("HotSeatsListContentMiuiXBlurBackground"));
        assertTrue(pipeline.contains("HotSeatsListContentBlurBackground2"));
        assertTrue(glassHook.contains("isNativeMaterialBackground"));
        assertTrue(glassHook.contains("com.miui.home.launcher.common.BlurUtilities"));
        assertTrue(glassHook.contains("clearAllBlur"));
        assertTrue(glassHook.contains("compat BlurBackground2 native blur cleared"));
    }
}
