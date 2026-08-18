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
    public void compositorBlurRegionUsesTheSameRoundedGeometryAsTheHost() throws Exception {
        String backdrop = read("Miuix307ZeroCopyBackdropView.java");
        String renderer = read("Miuix307ZeroCopyRenderer.java");

        assertTrue(backdrop.contains("ViewOutlineProvider"));
        assertTrue(backdrop.contains("outline.setRoundRect"));
        assertTrue(backdrop.contains("setClipToOutline(true)"));
        assertTrue(backdrop.contains("setGlassRadius"));
        assertTrue(backdrop.contains("invalidateOutline()"));
        assertTrue(renderer.contains("WeakReference<DockLiquidGlassHostView> hostRef"));
        assertTrue(renderer.contains("backdrop.setGlassRadius(readHostRadius(host))"));
        assertTrue(renderer.contains("getDeclaredField(\"radius\")"));
    }

    @Test
    public void compositorOpticsUseBlurBackground2NativeAddBlurWithoutReadback() throws Exception {
        String bridge = read("Miuix307CompositorOpticsBridge.java");
        String renderer = read("Miuix307ZeroCopyRenderer.java");

        assertTrue(bridge.contains("HotSeatsListContentBlurBackground2"));
        assertTrue(bridge.contains("getDeclaredMethod(\"addBlur\", View.class, float.class)"));
        assertTrue(bridge.contains("addBlur.invoke(vendorMaterial, target, cornerRadiusPx)"));
        assertTrue(bridge.contains("MiBlurBridge.setPassWindowBlurRadius(target, blurRadiusPx)"));
        assertTrue(bridge.contains("compat compositor optics active"));
        assertTrue(renderer.contains("Miuix307CompositorOpticsBridge.applyVendorBlurConfig("));
        assertTrue(renderer.contains("materialHost, backdrop, readHostRadius(host), blurRadiusPx"));
        assertFalse(bridge.contains("new float[]"));
        assertFalse(bridge.contains("new int[][]"));
        assertFalse(bridge.contains("captureScreenAsync"));
        assertFalse(bridge.contains("Bitmap"));
    }

    @Test
    public void compatBlurProbeObservesVendorArgumentsWithoutMutation() throws Exception {
        String bridge = read("Miuix307CompositorOpticsBridge.java");
        String hook = read("MiuixGlassHook.java");

        assertTrue(bridge.contains("installCompatBlurProbe(ClassLoader classLoader)"));
        assertTrue(bridge.contains("com.miui.home.launcher.common.BlurUtilities"));
        assertTrue(bridge.contains("\"setBackgroundBlur\""));
        assertTrue(bridge.contains("View.class, int.class, float[].class, int[][].class"));
        assertTrue(bridge.contains("compat blur args target="));
        assertTrue(bridge.contains("chain.proceed(chain.getArgs().toArray(new Object[0]))"));
        assertTrue(hook.contains("Miuix307CompositorOpticsBridge.installCompatBlurProbe(cl)"));
        assertFalse(bridge.contains("chain.getArgs().set("));
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
