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
    public void ordinaryDockDragWithoutExcludableSurfaceFreezesLastCleanBackdrop() throws Exception {
        String glass = read("DockLiquidGlassView.java");

        // Device log: View.getSurfaceControl() does not exist on this 307 build, so the normal
        // DragController path reaches setDockDragging(true, ..., null). It must not fall through
        // to the old dockDragging capture exception and sample the moving icon.
        assertTrue(glass.contains("dockDragCaptureFrozen"));
        assertTrue(glass.contains("Liquid capture frozen: Dock drag has no excludable Surface"));
        assertTrue(glass.contains("if (dockDragCaptureFrozen) return false;"));
        assertTrue(glass.contains("dock-drag-end"));
    }

    @Test
    public void systemDockDragFreezesCaptureAtMiuiDragListenerBoundary() throws Exception {
        String drag = read("Miuix307DragCaptureHook.java");
        String glass = read("DockLiquidGlassView.java");

        // startDragInDockForSystem() is no-arg in the decompiled build and delegates to MIUI
        // startDragAndDrop. Keep this separate fallback for the system-drag path even though the
        // device trace in this regression used ordinary DragController.startDrag(View[], ...).
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
    public void thirdPartyBackground2KeepsVendorVisualOwnerAndPrismalOpticalOnly() throws Exception {
        String pipeline = read("Miuix307MaterialPipeline.java");
        String glassHook = read("MiuixGlassHook.java");

        // Decompiled BlurBackground2 already owns pass-window blur. Device RenderEngine logs show
        // that making Prismal own blur adds a second large Floating Dock region blur. Keep the
        // vendor backdrop-blur owner, while Prismal remains optical-only and only the native blur
        // radius is clamped to LiquidDock's configured value.
        assertTrue(pipeline.contains("HotSeatsListContentMiuiXBlurBackground"));
        assertTrue(pipeline.contains("HotSeatsListContentBlurBackground2"));
        assertTrue(glassHook.contains("isNativeVisualOwner"));
        assertTrue(glassHook.contains("COMPAT_BACKGROUND_CLASS.equals"));
        assertTrue(glassHook.contains("enforcePrismalOpticalOnly(glass);"));
        assertTrue(glassHook.contains("enforceNativeBlurRadius(dockBg);"));
        assertFalse(glassHook.contains("clearAllBlur"));
        assertFalse(glassHook.contains("Prismal owns blur"));
    }

    @Test
    public void thirdPartyBackground2SuppressesOnlyItsBoundMiShadow() throws Exception {
        String pipeline = read("Miuix307MaterialPipeline.java");
        String glassHook = read("MiuixGlassHook.java");

        // DEX + device trace: HotSeats.showViewShadow() invokes MiShadowUtils.applyViewShadow()
        // on the active Dock background, and radius=143 reaches hidden View.setMiShadow(). Only
        // the currently bound compat BlurBackground2 must skip that call. Default MiuiX material,
        // stale themed instances, Recents TaskView, shortcut menus and arbitrary Views must pass.
        assertTrue(pipeline.contains("installCompatMiShadowSuppression(classLoader)"));
        assertTrue(pipeline.contains("com.miui.home.launcher.common.MiShadowUtils"));
        assertTrue(pipeline.contains("applyViewShadow"));
        assertTrue(pipeline.contains("MiuixGlassHook.shouldSuppressCompatMiShadow"));
        assertTrue(glassHook.contains("shouldSuppressCompatMiShadow"));
        assertTrue(glassHook.contains("COMPAT_BACKGROUND_CLASS.equals(dockBg.getClass().getName())"));
        assertTrue(glassHook.contains("dockBg != backgroundRef"));
        assertTrue(glassHook.contains("compat BlurBackground2 MiShadow suppressed"));
        assertFalse(pipeline.contains("setMiShadow"));
    }
}
