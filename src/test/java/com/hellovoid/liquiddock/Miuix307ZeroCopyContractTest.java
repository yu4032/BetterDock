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
    public void zeroCopyRendererUsesDedicatedBackdropAndExistingSharpOpticsHost() throws Exception {
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
        assertTrue(renderer.contains("backdrop.setBlurRadius(effectiveBlurRadiusPx)"));
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
    public void compatBackgroundBlurDoesNotStackPassWindowBlurOnSameBackdrop() throws Exception {
        String renderer = read("Miuix307ZeroCopyRenderer.java");
        String backdrop = read("Miuix307ZeroCopyBackdropView.java");
        String bridge = read("Miuix307CompositorOpticsBridge.java");

        assertTrue("renderer must identify the exact Launcher Background2 path before attaching",
                renderer.contains("Miuix307CompositorOpticsBridge.usesExactBackgroundBlur(materialHost)"));
        assertTrue("backdrop needs an explicit pass-window enable/disable contract",
                backdrop.contains("passWindowBlurEnabled"));
        assertTrue("exact Launcher backgroundBlur must disable the independent pass-window path",
                renderer.contains("!exactBackgroundBlur"));
        assertTrue("compat detection belongs in the vendor bridge, not duplicated class-name logic",
                bridge.contains("static boolean usesExactBackgroundBlur(View vendorMaterial)"));
        assertTrue("successful exact backgroundBlur must still satisfy zero-copy validation",
                backdrop.contains("setExternalCompositorBlurActive")
                        && renderer.contains("backdrop.setExternalCompositorBlurActive(true)"));
    }

    @Test
    public void compatPathUsesFrameworkBackgroundBlurWithoutLauncherClassLoading() throws Exception {
        String bridge = read("Miuix307CompositorOpticsBridge.java");
        String renderer = read("Miuix307ZeroCopyRenderer.java");

        assertTrue(bridge.contains("HotSeatsListContentBlurBackground2"));
        assertTrue("framework View owns the exact public hidden API on HyperOS 307",
                bridge.contains("View.class.getMethod(\"setBackgroundBlur\""));
        assertTrue(bridge.contains("Integer.TYPE, float[].class, int[][].class"));
        assertTrue("alpha must use the same framework primitive",
                bridge.contains("View.class.getMethod(\"setBackgroundBlurAlpha\", Float.TYPE)"));
        assertFalse("do not cross the Launcher classloader just to reach a wrapper",
                bridge.contains("Class.forName(BLUR_UTILITIES"));
        assertFalse("the Launcher wrapper constant is no longer needed",
                bridge.contains("BLUR_UTILITIES"));
        assertTrue("all four corners must be sourced from the host radius",
                bridge.contains("float[] cornerRadii = new float[]")
                        && bridge.contains("cornerRadiusPx, cornerRadiusPx, cornerRadiusPx, cornerRadiusPx"));
        assertTrue("preserve the exact two vendor blend modes from addBlur()",
                bridge.contains("new int[]{106, darkColor}")
                        && bridge.contains("new int[]{100, lightColor}"));
        assertTrue(bridge.contains("hotseats_list_content_background_blur_color_dark"));
        assertTrue(bridge.contains("hotseats_list_content_background_blur_color_light"));
        assertTrue("the experiment radius must be passed into View.setBackgroundBlur itself",
                bridge.contains("Integer.valueOf(blurRadiusPx), cornerRadii, blendConfig"));
        assertTrue(bridge.contains("\"getParentAlpha\""));
        assertFalse("do not write vendor radius=100 then attempt to repair a different API",
                bridge.contains("addBlur.invoke"));
        assertFalse(bridge.contains("MiBlurBridge.setPassWindowBlurRadius(target, blurRadiusPx)"));

        assertTrue(renderer.contains("Miuix307CompositorOpticsBridge.applyVendorBlurConfig("));
        assertTrue(renderer.contains("EXPERIMENT_BLUR_RADIUS_PX = 5"));
        assertFalse(bridge.contains("captureScreenAsync"));
        assertFalse(bridge.contains("Bitmap"));
    }

    @Test
    public void completedBlurDiagnosticsAreRetiredFromProductionPath() throws Exception {
        String bridge = read("Miuix307CompositorOpticsBridge.java");
        assertFalse(bridge.contains("installCompatBlurProbe"));
        assertFalse(bridge.contains("installCompatViewSetterProbe"));
        assertFalse(bridge.contains("hookCompatViewSetter"));
        assertFalse(bridge.contains("compatProbeTarget"));
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
