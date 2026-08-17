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

        // MainHook exits immediately when the specialized 307 pipeline is installed, before
        // reaching installLiquidGlassCaptureHooks in either legacy path.
        assertTrue(mainHook.contains("MiuiX 307 material active; legacy liquid capture bypassed"));
        assertTrue(mainHook.indexOf("Miuix307MaterialPipeline.install")
                < mainHook.indexOf("installLiquidGlassCaptureHooks(classLoader)"));
        // RecentsHapticHook is installed earlier, so its callback must gate itself at runtime.
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

        // APP/HOME ownership is required so DockLiquidGlassView selects FULL_DISPLAY for APP
        // instead of remaining UNKNOWN -> WALLPAPER forever on the specialized 307 path.
        assertTrue(source.contains("HomeOwnershipRuntime.bind(glass, glass.getContext())"));
        // 307 glass must always be allowed to sample the composed display over apps, even if a
        // stale historical preference disabled the legacy fullscreen-capture toggle.
        assertTrue(source.contains("glass.setFullscreenCapture(true)"));
        // DockLiquidGlassView normally hides geometrySource after its first frame. On 307 that
        // source is the native MiuiX pass-window blur background and must remain visible.
        assertTrue(source.contains("installNativeBackgroundPreserver"));
        assertTrue(source.contains("nativeBackgroundHiddenByGlass"));
        assertTrue(source.contains("dockBg.setAlpha(1f)"));
    }

    @Test
    public void recreatedMiuixBackgroundRebindsGlassWithoutLauncherSetupViews() throws IOException {
        String pipeline = read("Miuix307MaterialPipeline.java");
        String hook = read("MiuixGlassHook.java");

        // HyperOS may replace the MiuiX background during APP -> HOME without rebuilding the
        // whole Launcher. Every authoritative geometry callback must therefore ensure that the
        // callback instance owns a Prismal host before merely synchronizing its geometry.
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
    public void detachedThemeHierarchySchedulesOneShotGlassRebind() throws IOException {
        String pipeline = read("Miuix307MaterialPipeline.java");

        // Theme/icon-pack changes can mutate only the HotSeats hierarchy. setupViews and geometry
        // callbacks are not guaranteed after the injected host is detached, so retain the owner
        // weakly and repair from the actual View detach boundary instead of polling theme state.
        assertTrue("HotSeats owner must not be retained strongly",
                pipeline.contains("WeakReference<Object> hotSeatsRef"));
        assertTrue("setupViews must refresh the weak HotSeats owner",
                pipeline.contains("new WeakReference<>(hotSeats)"));
        assertTrue("bound hierarchy must observe real attach/detach lifecycle",
                pipeline.contains("View.OnAttachStateChangeListener"));
        assertTrue("theme recovery must have a coalescing latch",
                pipeline.contains("hierarchyRebindPosted"));
        assertTrue("detach must schedule a one-shot hierarchy repair",
                pipeline.contains("scheduleHierarchyRebind"));
        assertTrue("repair must be queued on the main thread without a delay",
                pipeline.contains("MAIN_HANDLER.post("));
        assertFalse("theme recovery must not poll with delayed retries",
                pipeline.contains("MAIN_HANDLER.postDelayed("));

        // Re-resolve the current vendor background from HotSeats rather than reinstalling into
        // a stale detached View, then reuse the one authoritative installer/binding path.
        assertTrue(pipeline.contains("resolveBackground(hotSeats)"));
        assertTrue(pipeline.contains("ensureGlassBound(currentBackground, config, classLoader)"));
        assertTrue(pipeline.contains("Miuix307DragCaptureHook.bind(background)"));

        // The host itself can be removed while the vendor background survives. Resolve the
        // injected DockLiquidGlassHostView from the bound background parent and observe it too;
        // no extra mutable glass API is needed in MiuixGlassHook.
        assertTrue(pipeline.contains("resolveBoundHost(background)"));
        assertTrue(pipeline.contains("instanceof DockLiquidGlassHostView"));

        // Existing binding identity remains the no-op guard, preventing duplicate Prismal hosts.
        assertTrue(pipeline.contains("MiuixGlassHook.isBoundTo(background)"));
    }

    @Test
    public void miuixUsesGuiCaptureTuningAndOnlyNativeBackdropBlur() throws IOException {
        String source = read("MiuixGlassHook.java");

        // 307 refraction sampling follows the existing GUI values rather than a demo constant.
        assertTrue(source.contains("glass.setCaptureScale(config.glass.captureScale)"));
        assertTrue(source.contains("glass.setCapturePowerLimitFps(config.glass.captureFps)"));
        assertFalse(source.contains("glass.setCaptureScale(0.5f)"));
        assertFalse(source.contains("glass.setCapturePowerLimitFps(30)"));

        // MiuiX owns blur. Prismal must remain a sharp optical/refraction/highlight overlay;
        // otherwise HOME/RECENTS can acquire a second heavy shader/self-blur after a rebind or
        // config reload.
        assertTrue(source.contains("enforcePrismalOpticalOnly"));
        assertTrue(source.contains("glass.setBlurMode(LiquidBlurMode.SHADER)"));
        assertTrue(source.contains("glass.setBlurRadiusPx(0)"));
    }

    @Test
    public void miuixNativeBlurRadiusIsReassertedAfterVendorStateTransitions() throws IOException {
        String bridge = read("MiBlurBridge.java");
        String hook = read("MiuixGlassHook.java");

        // HyperOS 307 rewrites the same HotSeats background from radius 14 to 201 during
        // GestureToHome. The 307 layer must be able to restore only the configured radius without
        // replaying setPassWindowBlurEnabled/setMiViewBlurMode and disturbing the vendor material.
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
