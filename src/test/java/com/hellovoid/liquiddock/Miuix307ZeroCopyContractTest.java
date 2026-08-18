package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Source contracts for the experimental MiuiX 307 zero-readback renderer. */
public class Miuix307ZeroCopyContractTest {
    private static String read(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/" + file));
    }

    @Test
    public void bridgeExposesPassWindowCapabilityAndBackdropOwnsBlur() throws Exception {
        String bridge = read("MiBlurBridge.java");
        String backdrop = read("Miuix307ZeroCopyBackdropView.java");

        assertTrue(bridge.contains("static boolean isPassWindowBlurAvailable()"));
        assertTrue(backdrop.contains("extends View"));
        assertTrue(backdrop.contains("MiBlurBridge.applyPassWindowBlur(this, blurRadiusPx)"));
        assertTrue(backdrop.contains("MiBlurBridge.clearPassWindowBlur(this)"));
        assertTrue(backdrop.contains("postOnAnimation"));
        assertTrue(backdrop.contains("isBlurActive()"));
        assertTrue(backdrop.contains("isActivationExhausted()"));
    }

    @Test
    public void zeroCopyRendererUsesDedicatedChildAndExistingSharpOpticsHost() throws Exception {
        String renderer = read("Miuix307ZeroCopyRenderer.java");

        assertTrue(renderer.contains("new Miuix307ZeroCopyBackdropView"));
        assertTrue(renderer.contains("host.addView(backdrop"));
        assertTrue(renderer.contains("LiquidBlurMode.ADVANCED_MATERIAL"));
        assertTrue(renderer.contains("host.reloadOpticsPreservingGeometry(glassConfig)"));
        assertFalse(renderer.contains("LiquidGlassFactory.create"));
        assertFalse(renderer.contains("DockLiquidGlassView"));
        assertFalse(renderer.contains("captureScreenAsync"));
    }

    @Test
    public void zeroCopyToneLayerCarriesGuiTintAndBrightness() throws Exception {
        String renderer = read("Miuix307ZeroCopyRenderer.java");
        String tone = read("Miuix307ZeroCopyToneView.java");

        assertTrue(renderer.contains("new Miuix307ZeroCopyToneView"));
        assertTrue(renderer.contains("host.addView(tone"));
        assertTrue(tone.contains("glassConfig.tintAlpha"));
        assertTrue(tone.contains("glassConfig.tintR"));
        assertTrue(tone.contains("glassConfig.tintG"));
        assertTrue(tone.contains("glassConfig.tintB"));
        assertTrue(tone.contains("glassConfig.brightness"));
        assertTrue(tone.contains("BlendMode.SCREEN"));
    }

    @Test
    public void geometryRefreshSynchronizesZeroCopyBlurAndTone() throws Exception {
        String hook = read("MiuixGlassHook.java");
        String renderer = read("Miuix307ZeroCopyRenderer.java");

        assertTrue(renderer.contains("static void sync(LiquidDockConfig.Glass glassConfig"));
        assertTrue(renderer.contains("backdrop.setBlurRadius(blurRadiusPx)"));
        assertTrue(renderer.contains("tone.setTone(glassConfig)"));
        assertTrue(hook.contains("Miuix307ZeroCopyRenderer.sync(\n                config.glass, Math.round(config.glass.blur))"));
    }

    @Test
    public void materialBindingTriesZeroCopyFirstAndKeepsCaptureFallback() throws Exception {
        String hook = read("MiuixGlassHook.java");

        int zeroCopy = hook.indexOf("Miuix307ZeroCopyRenderer.install");
        int fallback = hook.indexOf("installCaptureFallback");
        assertTrue(zeroCopy >= 0);
        assertTrue(fallback > zeroCopy);
        assertTrue(hook.contains("zeroCopyRef"));
        assertTrue(hook.contains("isZeroCopyActive()"));
        assertTrue(hook.contains("ZERO_COPY_TAG + \" zero-copy active"));
        assertTrue(hook.contains("ZERO_COPY_TAG + \" zero-copy unavailable; capture fallback"));
    }

    @Test
    public void successfulZeroCopyPathDoesNotBindCaptureOwnership() throws Exception {
        String hook = read("MiuixGlassHook.java");
        int zeroCopy = hook.indexOf("Miuix307ZeroCopyRenderer.install");
        int fallback = hook.indexOf("installCaptureFallback");
        String successRegion = hook.substring(zeroCopy, fallback);

        assertFalse(successRegion.contains("LiquidGlassFactory.create"));
        assertFalse(successRegion.contains("HomeOwnershipRuntime.bind"));
        assertFalse(successRegion.contains("requestCapture"));
        assertFalse(successRegion.contains("captureScreenAsync"));
    }
}
