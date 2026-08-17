package com.hellovoid.liquiddock;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Source contract for the device-validated MiuiX 307 glass architecture. */
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
    public void materialPipelineUsesExistingPrismalGlassStack() throws IOException {
        Path hookPath = MAIN.resolve("MiuixGlassHook.java");
        assertTrue("MiuiX glass hook must exist", Files.exists(hookPath));
        String hook = read("MiuixGlassHook.java");
        String pipeline = read("Miuix307MaterialPipeline.java");

        assertTrue(hook.contains("LiquidGlassFactory.create"));
        assertTrue(hook.contains("DockLiquidGlassHostView"));
        assertTrue(hook.contains("applyPassWindowBlur"));
        assertTrue(pipeline.contains("setBackgroundWidth"));
        assertTrue(pipeline.contains("setBackgroundHeight"));
        assertFalse(pipeline.contains("new Miuix307RefractionView"));
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
    public void nativeMiuixDrawableIsPreserved() throws IOException {
        Path hookPath = MAIN.resolve("MiuixGlassHook.java");
        assertTrue("MiuiX glass hook must exist", Files.exists(hookPath));
        String source = read("MiuixGlassHook.java");
        assertFalse(source.contains("setBackground(null)"));
        assertTrue(source.contains("mBackground"));
    }

    @Test
    public void appBackdropUsesOwnershipAndKeepsNativeMiuixVisible() throws IOException {
        String source = read("MiuixGlassHook.java");

        assertTrue(source.contains("HomeOwnershipRuntime.bind(glass, glass.getContext())"));
        assertTrue(source.contains("glass.setFullscreenCapture(true)"));
        assertTrue(source.contains("installNativeBackgroundPreserver"));
        assertTrue(source.contains("nativeBackgroundHiddenByGlass"));
        assertTrue(source.contains("dockBg.setAlpha(1f)"));
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

        // Icon/theme changes can replace the background and, on some builds, the HotSeats owner.
        // Recovery therefore keeps the Launcher weakly and re-resolves mHotSeats instead of
        // trusting a detached owner captured during setupViews.
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

        // If the first main-turn repair runs before the replacement hierarchy is attached, wait
        // on a stable Launcher/workspace root. Listening only to the detached old HotSeats tree
        // can never observe the replacement hierarchy's first layout.
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
    public void miuixUsesGuiCaptureTuningAndOnlyNativeBackdropBlur() throws IOException {
        String source = read("MiuixGlassHook.java");

        assertTrue(source.contains("glass.setCaptureScale(config.glass.captureScale)"));
        assertTrue(source.contains("glass.setCapturePowerLimitFps(config.glass.captureFps)"));
        assertFalse(source.contains("glass.setCaptureScale(0.5f)"));
        assertFalse(source.contains("glass.setCapturePowerLimitFps(30)"));

        assertTrue(source.contains("enforcePrismalOpticalOnly"));
        assertTrue(source.contains("glass.setBlurMode(LiquidBlurMode.SHADER)"));
        assertTrue(source.contains("glass.setBlurRadiusPx(0)"));
    }

    @Test
    public void miuixNativeBlurRadiusIsReassertedAfterVendorStateTransitions() throws IOException {
        String bridge = read("MiBlurBridge.java");
        String hook = read("MiuixGlassHook.java");

        assertTrue(bridge.contains("setPassWindowBlurRadius"));
        assertTrue(hook.contains("enforceNativeBlurRadius"));
        assertTrue(hook.contains("config.glass.blur"));
        assertTrue(hook.contains("MiBlurBridge.setPassWindowBlurRadius"));

        int helper = bridge.indexOf("setPassWindowBlurRadius");
        int apply = bridge.indexOf("applyPassWindowBlur");
        assertTrue("radius-only helper must be independent of full pass-blur setup",
                helper >= 0 && apply >= 0 && helper != apply);
    }
}
