package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Source-level contract for hidden/vendor hooks that cannot be instantiated in a host JVM. */
public class StockLauncherCaptureContractTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/" + name));
    }

    @Test public void launcherOwnedLayerCapturePathIsGone() throws Exception {
        String live = source("LiveScreenCapture.java");
        String glass = source("DockLiquidGlassView.java");
        String hook = source("MainHook.java");
        String policy = source("CaptureSourcePolicy.java");

        for (String forbidden : new String[]{
                "LOCAL_LAYER", "LayerCaptureArgs", "captureLayers", "captureLayerAsync",
                "resolveLauncherOwnedCaptureSurface", "resolveViewRootSurfaceControl",
                "allAppsCaptureRoot", "resolveLaptopAllAppsCaptureRoot",
                "resolveNormalAllAppsCaptureRoot"}) {
            assertFalse("forbidden launcher-owned capture path remains: " + forbidden,
                    live.contains(forbidden) || glass.contains(forbidden)
                            || hook.contains(forbidden) || policy.contains(forbidden));
        }
    }

    @Test public void mainHookUsesStockDrawerAuthority() throws Exception {
        String hook = source("MainHook.java");
        assertTrue(hook.contains("com.miui.home.launcher.dock.v3.dependencies.DrawerStatusServiceImpl"));
        assertTrue(hook.contains("dispatchDrawerOpen"));
        assertTrue(hook.contains("dispatchDrawerClose"));
        assertTrue(hook.contains("dispatchDrawerProgress"));
    }

    @Test public void mainHookUsesStockRecentsAuthority() throws Exception {
        String hook = source("MainHook.java");
        assertTrue(hook.contains("com.miui.home.launcher.dock.v3.state.DockStateManager$mainStateObserver$1"));
        assertTrue(hook.contains("onEnterRecent"));
        assertTrue(hook.contains("onExitRecent"));
        assertTrue(hook.contains("com.miui.home.launcher.dock.v3.state.DockStateManager$recentsListener$1"));
        assertTrue(hook.contains("onRecentViewShow"));
        assertTrue(hook.contains("onRecentViewHide"));
        assertTrue(hook.contains("onRecentViewAnimationComplete"));
    }

    @Test public void recentsHapticPrearmIsPreserved() throws Exception {
        String hook = source("MainHook.java");
        String glass = source("DockLiquidGlassView.java");
        assertTrue(hook.contains("RecentsHapticHook.install"));
        assertTrue(hook.contains("onRecentsHapticTrigger"));
        assertTrue(glass.contains("prearmRecentsCapture(\"recents-prearm-haptic\")"));
        assertTrue(glass.contains("requestStateCapture(reason)"));
        assertTrue(glass.contains("dockDragging || recentsPrearmed || isRecentsVisible()"));
    }

    @Test public void launcherFocusDoesNotOverrideLauncherOwnedScenes() throws Exception {
        String hook = source("MainHook.java");
        String glass = source("DockLiquidGlassView.java");
        assertTrue(glass.contains("boolean isOverviewActive()"));
        assertTrue(hook.contains("glass.isAllAppsActive() || glass.isOverviewActive()"));
    }

    @Test public void launcherFocusGainCannotOverrideExternalAppTopTask() throws Exception {
        String hook = source("MainHook.java");
        assertTrue(hook.contains("resolveTopTaskPackage"));
        assertTrue(hook.contains("confirmLauncherHomeFocus"));
        assertTrue(hook.contains("external task still foreground"));
        assertFalse(hook.contains("launcherResumed = hasFocus;"));
    }

    @Test public void allAppsStateCarriesNoCaptureRoot() throws Exception {
        String glass = source("DockLiquidGlassView.java");
        String hook = source("MainHook.java");
        assertTrue(glass.contains("void setAllAppsActive(boolean active)"));
        assertFalse(glass.contains("setAllAppsActive(boolean active, View"));
        assertFalse(hook.contains("setAllAppsActive(\n                                true,"));
    }

    @Test public void mingouLegacyWorkstationFallbackIsGone() throws Exception {
        String hook = source("MainHook.java");
        assertFalse(hook.contains("isMingouLaptopPcModeEnabled"));
        assertFalse(hook.contains("setMingouLaptopPcModeEnabled"));
        assertFalse(hook.contains("Mingou workstation"));
    }
}
