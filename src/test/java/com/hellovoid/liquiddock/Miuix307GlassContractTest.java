package com.hellovoid.liquiddock;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Source contract for the device-validated MiuiX 307 shell and current GPU demo backend. */
public class Miuix307GlassContractTest {
    private static final Path MAIN = Paths.get("src/main/java/com/hellovoid/liquiddock");

    private static String read(String file) throws IOException {
        return Files.readString(MAIN.resolve(file), StandardCharsets.UTF_8);
    }

    @Test
    public void blurBridgeProvidesRealtimePassWindowBlur() throws IOException {
        String source = read("MiBlurBridge.java");
        assertTrue(source.contains("setPassWindowBlurEnabled"));
        assertTrue(source.contains("setMiViewBlurMode"));
        assertTrue(source.contains("setMiBackgroundBlurRadius"));
        assertTrue(source.contains("applyPassWindowBlur"));
        assertTrue(source.contains("clearPassWindowBlur"));
    }

    @Test
    public void materialPipelineUsesNeutralPassBlurGpuPrimaryWithCaptureFallback()
            throws IOException {
        Path hookPath = MAIN.resolve("MiuixGlassHook.java");
        assertTrue("MiuiX glass hook must exist", Files.exists(hookPath));
        String hook = read("MiuixGlassHook.java");
        String renderer = read("Miuix307ZeroCopyRenderer.java");
        String pipeline = read("Miuix307MaterialPipeline.java");

        assertTrue(hook.contains("Miuix307ZeroCopyRenderer.install"));
        assertTrue(hook.contains("DockLiquidGlassHostView"));
        assertTrue(renderer.contains("new Miuix307PassBlurGpuView"));
        assertTrue(renderer.contains("host.addView(gpuBackdrop"));
        assertFalse(renderer.contains("Miuix307ZeroCopyToneView"));
        assertFalse(renderer.contains("LiquidBlurMode.ADVANCED_MATERIAL"));
        assertFalse("neutral renderer must not erase the safe replacement stroke",
                renderer.contains("materialHost.setForeground(null)"));
        assertTrue("shell must configure the replacement foreground stroke separately",
                hook.contains("DockStrokeRenderer.configureReplacingForeground("));
        assertTrue(hook.contains("suppressVendorGpuBlur"));
        assertTrue(pipeline.contains("setBackgroundWidth"));
        assertTrue(pipeline.contains("setBackgroundHeight"));
        assertFalse(pipeline.contains("new Miuix307RefractionView"));

        int zeroCopy = hook.indexOf("Miuix307ZeroCopyRenderer.install");
        int fallback = hook.indexOf("private static void installCaptureFallback");
        assertTrue(zeroCopy >= 0 && fallback > zeroCopy);
        assertFalse(hook.substring(zeroCopy, fallback).contains("LiquidGlassFactory.create"));
        assertTrue(hook.substring(fallback).contains("LiquidGlassFactory.create"));
    }

    @Test
    public void miuixModeIsolatedFromLegacyCaptureLifecycle() throws IOException {
        String mainHook = read("MainHook.java");
        String haptic = read("RecentsHapticHook.java");
        String pipeline = read("Miuix307MaterialPipeline.java");

        assertTrue(mainHook.contains("MiuiX 307 material active; legacy liquid capture bypassed"));
        assertTrue(mainHook.indexOf("Miuix307MaterialPipeline.install")
                < mainHook.indexOf("installLiquidGlassCaptureHooks(classLoader)"));
        assertTrue(haptic.contains("!Miuix307MaterialPipeline.isInstalled()"));
        assertFalse(pipeline.contains("installLiquidGlassCaptureHooks"));
    }

    @Test
    public void nativeMiuixDrawableIsPreservedAsGeometrySource() throws IOException {
        Path hookPath = MAIN.resolve("MiuixGlassHook.java");
        assertTrue("MiuiX glass hook must exist", Files.exists(hookPath));
        String source = read("MiuixGlassHook.java");
        assertFalse(source.contains("setBackground(null)"));
        assertTrue(source.contains("mBackground"));
        assertTrue(source.contains("geometry source"));
    }

    @Test
    public void captureOwnershipIsFallbackOnly() throws IOException {
        String source = read("MiuixGlassHook.java");
        int zeroCopy = source.indexOf("Miuix307ZeroCopyRenderer.install");
        int fallback = source.indexOf("private static void installCaptureFallback");
        assertTrue(zeroCopy >= 0 && fallback > zeroCopy);

        String primaryRegion = source.substring(zeroCopy, fallback);
        String fallbackRegion = source.substring(fallback);
        assertFalse(primaryRegion.contains("HomeOwnershipRuntime.bind"));
        assertFalse(primaryRegion.contains("glass.setFullscreenCapture(true)"));
        assertTrue(fallbackRegion.contains("HomeOwnershipRuntime.bind(glass, glass.getContext())"));
        assertTrue(fallbackRegion.contains("glass.setFullscreenCapture(true)"));

        assertTrue(source.contains("installVendorGpuBlurSuppressor"));
        assertTrue(source.contains("suppressVendorGpuBlur"));
        assertFalse(source.contains("installNativeBackgroundPreserver"));
        assertFalse(source.contains("nativeBackgroundHiddenByGlass"));
        assertFalse(source.contains("dockBg.setAlpha(1f)"));
    }

    @Test
    public void recreatedMiuixBackgroundRebindsGlassWithoutLauncherSetupViews() throws IOException {
        String pipeline = read("Miuix307MaterialPipeline.java");
        String hook = read("MiuixGlassHook.java");

        assertTrue(hook.contains("isBoundTo"));
        assertTrue(pipeline.contains("ensureGlassBound"));
        assertTrue(pipeline.contains("MiuiX 307 background instance changed"));

        int width = pipeline.indexOf("setBackgroundWidth");
        int height = pipeline.indexOf("setBackgroundHeight");
        int radius = pipeline.indexOf("setBackgroundRadius");
        assertTrue(width >= 0 && pipeline.indexOf("ensureGlassBound", width) > width);
        assertTrue(height >= 0 && pipeline.indexOf("ensureGlassBound", height) > height);
        assertTrue(radius >= 0 && pipeline.indexOf("ensureGlassBound", radius) > radius);
    }

    @Test
    public void detachedThemeHierarchyWaitsForRealReattachBeforeGlassRebind() throws IOException {
        String pipeline = read("Miuix307MaterialPipeline.java");

        assertTrue("Launcher owner must not be retained strongly",
                pipeline.contains("WeakReference<Object> launcherRef"));
        assertTrue("setupViews must refresh the weak Launcher owner",
                pipeline.contains("new WeakReference<>(launcher)"));
        assertTrue("theme recovery must re-resolve the current HotSeats owner",
                pipeline.contains("resolveCurrentHotSeats"));
        assertTrue("HotSeats owner must not be retained strongly",
                pipeline.contains("WeakReference<Object> hotSeatsRef"));
        assertTrue("setupViews must refresh the weak HotSeats owner",
                pipeline.contains("new WeakReference<>(hotSeats)"));
        assertTrue("bound hierarchy must observe real attach/detach lifecycle",
                pipeline.contains("View.OnAttachStateChangeListener"));
        assertTrue("theme recovery must have a coalescing latch",
                pipeline.contains("hierarchyRebindPosted"));
        assertTrue("detach must schedule hierarchy repair",
                pipeline.contains("scheduleHierarchyRebind"));
        assertTrue("repair must reject a stale detached background",
                pipeline.contains("currentBackground.isAttachedToWindow()"));

        assertTrue(pipeline.contains("ViewTreeObserver.OnGlobalLayoutListener"));
        assertTrue(pipeline.contains("armHierarchyLayoutRecovery"));
        assertTrue(pipeline.contains("workspaceRef != null && workspaceRef.isAttachedToWindow()"));
        assertTrue(pipeline.contains("removeOnGlobalLayoutListener"));
        assertFalse("theme recovery must not poll with delayed retries",
                pipeline.contains("MAIN_HANDLER.postDelayed("));

        assertTrue(pipeline.contains("resolveBackground(hotSeats)"));
        assertTrue(pipeline.contains("ensureGlassBound(currentBackground, config, classLoader)"));
        assertTrue(pipeline.contains("resolveBoundHost(background)"));
        assertTrue(pipeline.contains("instanceof DockLiquidGlassHostView"));
        assertTrue(pipeline.contains("MiuixGlassHook.isBoundTo(background)"));
    }

    @Test
    public void captureFallbackUsesGuiTuningAndConfiguredBlur() throws IOException {
        String source = read("MiuixGlassHook.java");
        String factory = read("LiquidGlassFactory.java");
        int fallback = source.indexOf("private static void installCaptureFallback");
        assertTrue(fallback >= 0);
        String fallbackRegion = source.substring(fallback);

        assertTrue(fallbackRegion.contains("glass.setCaptureScale(config.glass.captureScale)"));
        assertTrue(fallbackRegion.contains("glass.setCapturePowerLimitFps(config.glass.captureFps)"));
        assertFalse(source.contains("glass.setCaptureScale(0.5f)"));
        assertFalse(source.contains("glass.setCapturePowerLimitFps(30)"));

        assertTrue(factory.contains("Math.round(config.blur * scale)"));
        assertTrue(factory.contains("view.setBlurMode(config.blurMode)"));
        assertFalse(source.contains("enforcePrismalOpticalOnly"));
        assertFalse(source.contains("glass.setBlurRadiusPx(0)"));
    }

    @Test
    public void vendorGpuBlurIsReassertivelyDisabledAfterStateTransitions() throws IOException {
        String bridge = read("MiBlurBridge.java");
        String hook = read("MiuixGlassHook.java");
        String renderer = read("Miuix307ZeroCopyRenderer.java");
        String passBlur = read("Miuix307PassBlurBridge.java");

        assertTrue(bridge.contains("setPassWindowBlurRadius"));
        assertTrue(bridge.contains("clearPassWindowBlur"));
        assertTrue(hook.contains("suppressVendorGpuBlur"));
        assertTrue(hook.contains("MiBlurBridge.setPassWindowBlurRadius(dockBg, 0)"));
        assertTrue(hook.contains("MiBlurBridge.clearPassWindowBlur(dockBg)"));
        assertTrue(hook.contains("ViewTreeObserver.OnPreDrawListener"));
        assertTrue(hook.contains("vendor parent GPU blur disabled"));
        assertTrue("the current primary backdrop must be an independent GPU child",
                renderer.contains("new Miuix307PassBlurGpuView"));
        assertTrue("SurfaceFlinger producer must remain full-resolution in the first demo",
                passBlur.contains("DEMO_SCALE = 1.0f"));

        int helper = bridge.indexOf("setPassWindowBlurRadius");
        int apply = bridge.indexOf("applyPassWindowBlur");
        assertTrue("radius-only helper must remain independent of full pass-blur setup",
                helper >= 0 && apply >= 0 && helper != apply);
    }
}
