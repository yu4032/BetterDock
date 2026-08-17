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
    public void native307BackgroundIsGeometryOnlyAndPrismalOwnsConfiguredBlur() throws Exception {
        String pipeline = read("Miuix307MaterialPipeline.java");
        String glassHook = read("MiuixGlassHook.java");

        // theme(3): SurfaceFlinger reports Floating Dock regionblurRadius=5 even while LiquidDock
        // captures a raw blurRadius=0 frame. Keeping the vendor background visible and forcing
        // Prismal blur to zero therefore post-processes/overlays the already rendered liquid
        // glass at the Floating Dock Surface. Both native backgrounds are geometry sources only:
        // disable vendor GPU blur, let DockLiquidGlassView hide the source after capture, and
        // leave the blur configured by LiquidGlassFactory intact for Prismal itself.
        assertTrue(pipeline.contains("HotSeatsListContentMiuiXBlurBackground"));
        assertTrue(pipeline.contains("HotSeatsListContentBlurBackground2"));
        assertTrue(glassHook.contains("suppressVendorGpuBlur"));
        assertTrue(glassHook.contains("setPassWindowBlurRadius(dockBg, 0)"));
        assertFalse(glassHook.contains("enforcePrismalOpticalOnly"));
        assertFalse(glassHook.contains("installNativeBackgroundPreserver"));
        assertFalse(glassHook.contains("nativeBackgroundHiddenByGlass"));
        assertTrue(glassHook.contains("Prismal owns blur"));
    }

    @Test
    public void thirdPartyBackground2PreservesVendorMiShadowLikeDefaultMaterial() throws Exception {
        String pipeline = read("Miuix307MaterialPipeline.java");
        String glassHook = read("MiuixGlassHook.java");

        // Full default->theme trace: both backgrounds receive the same HotSeats MiShadow
        // (radius=143, offsetY=34). The themed instance gets its first shadow before LiquidDock
        // rebinds it, so bound-instance suppression is both late and conceptually wrong.
        assertFalse(pipeline.contains("installCompatMiShadowSuppression(classLoader)"));
        assertFalse(pipeline.contains("shouldSuppressCompatMiShadow"));
        assertFalse(glassHook.contains("shouldSuppressCompatMiShadow"));
        assertFalse(glassHook.contains("compat BlurBackground2 MiShadow suppressed"));
    }

    @Test
    public void thirdPartyBackground2DisablesVendorGpuBlurAtBlurUtilitiesBoundary() throws Exception {
        String pipeline = read("Miuix307MaterialPipeline.java");
        String glassHook = read("MiuixGlassHook.java");

        // BlurBackground2.addBlur() reaches BlurUtilities.setBackgroundBlur(View,int,...), whose
        // result becomes compositor region blur on the entire Floating Dock Surface. Correcting
        // 100 to LiquidDock's 5 still leaves a vendor post-process above Prismal. For the exact
        // themed HotSeats background, every positive vendor radius must become zero before it
        // reaches View; radius<=0 and all vendor blend arrays still pass through unchanged.
        assertTrue(pipeline.contains("installCompatBackgroundBlurSuppression(classLoader)"));
        assertTrue(pipeline.contains("com.miui.home.launcher.common.BlurUtilities"));
        assertTrue(pipeline.contains("\"setBackgroundBlur\""));
        assertTrue(pipeline.contains("View.class, int.class, float[].class, int[][].class"));
        assertTrue(pipeline.contains("MiuixGlassHook.suppressCompatBackgroundBlurRadius"));
        assertTrue(glassHook.contains("suppressCompatBackgroundBlurRadius"));
        assertTrue(glassHook.contains("requestedRadius <= 0"));
        assertTrue(glassHook.contains("COMPAT_BACKGROUND_CLASS.equals(dockBg.getClass().getName())"));
        assertTrue(glassHook.contains("compat BlurBackground2 GPU background blur suppressed"));
        assertFalse(glassHook.contains("targetRadius = Math.round"));
    }
}
