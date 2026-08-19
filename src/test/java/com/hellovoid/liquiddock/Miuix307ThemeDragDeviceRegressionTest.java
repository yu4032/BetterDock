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
        assertTrue(drag.contains("java.lang.reflect.Array"));
        assertTrue(drag.contains("views.getClass().isArray()"));
        assertTrue(drag.contains("Array.getLength(views)"));
        assertTrue(drag.contains("Array.get(views, 0)"));
    }

    @Test
    public void ordinaryDockDragWithoutExcludableSurfaceFreezesLastCleanBackdrop() throws Exception {
        String glass = read("DockLiquidGlassView.java");
        assertTrue(glass.contains("dockDragCaptureFrozen"));
        assertTrue(glass.contains("Liquid capture frozen: Dock drag has no excludable Surface"));
        assertTrue(glass.contains("if (dockDragCaptureFrozen) return false;"));
        assertTrue(glass.contains("dock-drag-end"));
    }

    @Test
    public void systemDockDragFreezesCaptureAtMiuiDragListenerBoundary() throws Exception {
        String drag = read("Miuix307DragCaptureHook.java");
        String glass = read("DockLiquidGlassView.java");
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
        assertFalse(exclusions.contains("MaskSnapshotLayer_dragIcon"));
        assertFalse(exclusions.contains("MaskDark_dragIcon"));
        assertFalse(exclusions.contains("MaskIcon_dragIcon"));
    }

    @Test
    public void native307BackgroundKeepsParentBlurOffWhileNeutralPassBlurGpuChildOwnsBackdrop()
            throws Exception {
        String pipeline = read("Miuix307MaterialPipeline.java");
        String glassHook = read("MiuixGlassHook.java");
        String renderer = read("Miuix307ZeroCopyRenderer.java");
        String passBlur = read("Miuix307PassBlurBridge.java");

        assertTrue(pipeline.contains("HotSeatsListContentMiuiXBlurBackground"));
        assertTrue(pipeline.contains("HotSeatsListContentBlurBackground2"));
        assertTrue(glassHook.contains("suppressVendorGpuBlur"));
        assertTrue(glassHook.contains("setPassWindowBlurRadius(dockBg, 0)"));
        assertTrue(glassHook.contains("Miuix307ZeroCopyRenderer.install"));
        assertTrue(renderer.contains("new Miuix307PassBlurTextureView"));
        assertFalse(renderer.contains("new Miuix307PassBlurGpuView"));
        assertFalse(renderer.contains("LiquidBlurMode.ADVANCED_MATERIAL"));
        assertFalse(renderer.contains("Miuix307ZeroCopyToneView"));
        assertFalse("neutral renderer must not erase the safe replacement stroke",
                renderer.contains("materialHost.setForeground(null)"));
        assertTrue("the shell must still configure a replacement stroke with no vendor body",
                glassHook.contains("DockStrokeRenderer.configureReplacingForeground("));
        assertTrue(passBlur.contains("\"SetPassBlurSurface\""));
        assertTrue(passBlur.contains("DEMO_SCALE = 1.0f"));
        assertFalse(glassHook.contains("enforcePrismalOpticalOnly"));
        assertFalse(glassHook.contains("installNativeBackgroundPreserver"));
        assertFalse(glassHook.contains("nativeBackgroundHiddenByGlass"));
    }

    @Test
    public void thirdPartyBackground2PreservesVendorMiShadowLikeDefaultMaterial() throws Exception {
        String pipeline = read("Miuix307MaterialPipeline.java");
        String glassHook = read("MiuixGlassHook.java");
        assertFalse(pipeline.contains("installCompatMiShadowSuppression(classLoader)"));
        assertFalse(pipeline.contains("shouldSuppressCompatMiShadow"));
        assertFalse(glassHook.contains("shouldSuppressCompatMiShadow"));
        assertFalse(glassHook.contains("compat BlurBackground2 MiShadow suppressed"));
    }

    @Test
    public void thirdPartyBackground2DisablesVendorGpuBlurAtBlurUtilitiesBoundary() throws Exception {
        String pipeline = read("Miuix307MaterialPipeline.java");
        String glassHook = read("MiuixGlassHook.java");
        assertTrue(pipeline.contains("installCompatBackgroundBlurSuppression(classLoader)"));
        assertTrue(pipeline.contains("com.miui.home.launcher.common.BlurUtilities"));
        assertTrue(pipeline.contains("\"setBackgroundBlur\""));
        assertTrue(pipeline.contains("View.class, int.class, float[].class, int[][].class"));
        assertTrue(pipeline.contains("MiuixGlassHook.suppressCompatBackgroundBlurRadius"));
        assertTrue(glassHook.contains("suppressCompatBackgroundBlurRadius"));
        assertTrue(glassHook.contains("requestedRadius <= 0"));
        assertTrue(glassHook.contains("COMPAT_BACKGROUND_CLASS.equals(dockBg.getClass().getName())"));
        assertTrue(glassHook.contains("compat BlurBackground2 parent GPU blur suppressed"));
        assertFalse(glassHook.contains("targetRadius = Math.round"));
    }
}
