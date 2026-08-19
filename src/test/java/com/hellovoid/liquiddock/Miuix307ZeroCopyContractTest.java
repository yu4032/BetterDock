package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Source contracts for the HyperOS 307 PassBlur GPU calibration renderer. */
public class Miuix307ZeroCopyContractTest {
    private static String read(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/" + file));
    }

    @Test
    public void zeroCopyRendererUsesNeutralTextureViewPassBlurBackdropOnly() throws Exception {
        String renderer = read("Miuix307ZeroCopyRenderer.java");
        String hook = read("MiuixGlassHook.java");
        assertTrue(renderer.contains("new Miuix307PassBlurTextureView"));
        assertFalse(renderer.contains("new Miuix307PassBlurGpuView"));
        assertTrue(renderer.contains("host.addView(gpuBackdrop"));
        assertFalse("neutral renderer must not erase the configured replacement stroke",
                renderer.contains("materialHost.setForeground(null)"));
        assertTrue("shell owns the safe replacement stroke outside the GPU renderer",
                hook.contains("DockStrokeRenderer.configureReplacingForeground("));
        assertTrue(renderer.contains("Miuix307CompositorOpticsBridge.usesExactBackgroundBlur(materialHost)"));
        assertFalse(renderer.contains("LiquidBlurMode.ADVANCED_MATERIAL"));
        assertFalse(renderer.contains("host.reloadOpticsPreservingGeometry(glassConfig)"));
        assertFalse(renderer.contains("Miuix307ZeroCopyToneView"));
        assertFalse("calibration successful path must retire the fixed charge child",
                renderer.contains("new Miuix307RefractionSurfaceProbeView"));
        assertFalse("calibration successful path must not cover the OES output with framework blur",
                renderer.contains("new Miuix307ZeroCopyBackdropView")
                        || renderer.contains("applyVendorBlurConfig"));
        assertFalse(renderer.contains("captureScreenAsync"));
    }

    @Test
    public void historicalToneLayerRemainsAvailableButNeutralRendererDoesNotInstantiateIt()
            throws Exception {
        String renderer = read("Miuix307ZeroCopyRenderer.java");
        String tone = read("Miuix307ZeroCopyToneView.java");
        assertFalse(renderer.contains("new Miuix307ZeroCopyToneView"));
        assertFalse(renderer.contains("host.addView(tone"));
        assertTrue(tone.contains("glassConfig.tintAlpha"));
        assertTrue(tone.contains("glassConfig.tintR"));
        assertTrue(tone.contains("glassConfig.tintG"));
        assertTrue(tone.contains("glassConfig.tintB"));
        assertTrue(tone.contains("glassConfig.brightness"));
    }

    @Test
    public void rendererExposesGpuActivationGeometryAndInstallContracts() throws Exception {
        String renderer = read("Miuix307ZeroCopyRenderer.java");
        assertTrue(renderer.contains("static boolean isInstalled()"));
        assertTrue(renderer.contains("static boolean isActive()"));
        assertTrue(renderer.contains("gpuBackdrop.isGpuBackdropActive()"));
        assertTrue(renderer.contains("static boolean isActivationExhausted()"));
        assertTrue(renderer.contains("gpuBackdrop.isActivationExhausted()"));
        assertTrue(renderer.contains("static int activeWidth()"));
        assertTrue(renderer.contains("static int activeHeight()"));
        assertFalse("Stage A calibration must ignore glass radius/refraction state",
                renderer.contains("gpuBackdrop.setGlassRadius"));
        assertFalse(renderer.contains("tone.setTone(glassConfig)"));
        assertTrue("clear must stop the PassBlur producer before dropping refs",
                renderer.contains("gpuBackdrop.shutdown()"));
        assertFalse("foreground suppressor is retired so the safe replacement stroke can remain",
                renderer.contains("removeForegroundSuppressor()")
                        || renderer.contains("installForegroundSuppressor("));
    }

    @Test
    public void hookValidatesGpuFramesBeforeDeclaringZeroCopyActive() throws Exception {
        String hook = read("MiuixGlassHook.java");
        assertTrue("EGL/BufferQueue startup gets a longer validation window",
                hook.contains("ZERO_COPY_VALIDATION_FRAMES = 90"));
        assertTrue(hook.contains("Miuix307ZeroCopyRenderer.isActive()"));
        assertTrue(hook.contains("Miuix307ZeroCopyRenderer.isActivationExhausted()"));
        assertTrue(hook.contains("backend=passblur-gles")
                || hook.contains("backend=passblur-textureview-egl"));
        assertTrue(hook.contains("Miuix307ZeroCopyRenderer.activeWidth()"));
        assertTrue(hook.contains("Miuix307ZeroCopyRenderer.activeHeight()"));
        assertTrue("validation timeout must stay neutral instead of masking the GPU demo",
                hook.contains("zero-copy still pending; legacy capture disabled"));
    }

    @Test
    public void materialBindingTriesGpuDemoFirstAndKeepsInstallFallback() throws Exception {
        String hook = read("MiuixGlassHook.java");
        int zeroCopy = hook.indexOf("Miuix307ZeroCopyRenderer.install");
        int fallback = hook.indexOf("private static void installCaptureFallback");
        assertTrue(zeroCopy >= 0);
        assertTrue(fallback > zeroCopy);
        assertTrue(hook.contains("isZeroCopyActive()"));
        assertTrue(hook.contains("ZERO_COPY_TAG + \" zero-copy active"));
        assertTrue("unsupported install path may still use archived capture fallback",
                hook.contains("zero-copy unavailable; capture fallback reason=install"));
    }

    @Test
    public void successfulGpuDemoPathDoesNotBindCaptureOwnership() throws Exception {
        String hook = read("MiuixGlassHook.java");
        int zeroCopy = hook.indexOf("Miuix307ZeroCopyRenderer.install");
        int fallback = hook.indexOf("private static void installCaptureFallback");
        String successRegion = hook.substring(zeroCopy, fallback);
        assertFalse(successRegion.contains("LiquidGlassFactory.create"));
        assertFalse(successRegion.contains("HomeOwnershipRuntime.bind"));
        assertFalse(successRegion.contains("requestCapture"));
        assertFalse(successRegion.contains("captureScreenAsync"));
    }

    @Test
    public void historicalFrameworkBlurBridgeRemainsIndependentOfLauncherClassLoading() throws Exception {
        String bridge = read("Miuix307CompositorOpticsBridge.java");
        assertTrue(bridge.contains("View.class.getMethod(\"setBackgroundBlur\""));
        assertTrue(bridge.contains("View.class.getMethod(\"setBackgroundBlurAlpha\", Float.TYPE)"));
        assertFalse(bridge.contains("Class.forName(BLUR_UTILITIES"));
        assertFalse(bridge.contains("BLUR_UTILITIES"));
    }
}
