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

    @Test public void stockDrawerAuthorityLivesOutsideMainHook() throws Exception {
        String hook = source("MainHook.java");
        String allApps = source("AllAppsStateHooks.java");
        assertTrue(allApps.contains("com.miui.home.launcher.dock.v3.dependencies.DrawerStatusServiceImpl"));
        assertTrue(allApps.contains("dispatchDrawerOpen"));
        assertTrue(allApps.contains("dispatchDrawerClose"));
        assertTrue(allApps.contains("dispatchDrawerProgress"));
        assertFalse(hook.contains("installDrawerStatusHooks"));
    }

    @Test public void stockRecentsAuthorityLivesOutsideMainHook() throws Exception {
        String hook = source("MainHook.java");
        String recents = source("RecentsStateHooks.java");
        assertTrue(recents.contains("com.miui.home.launcher.dock.v3.state.DockStateManager$mainStateObserver$1"));
        assertTrue(recents.contains("onEnterRecent"));
        assertTrue(recents.contains("onExitRecent"));
        assertTrue(recents.contains("com.miui.home.launcher.dock.v3.state.DockStateManager$recentsListener$1"));
        assertTrue(recents.contains("onRecentViewShow"));
        assertTrue(recents.contains("onRecentViewHide"));
        assertTrue(recents.contains("onRecentViewAnimationComplete"));
        assertFalse(hook.contains("installStockRecentsStateHooks"));
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

    @Test public void launcherSceneArbitrationLivesOutsideMainHook() throws Exception {
        String hook = source("MainHook.java");
        String controller = source("LauncherSceneController.java");
        String resolver = source("ForegroundTaskResolver.java");
        String glass = source("DockLiquidGlassView.java");

        assertTrue(glass.contains("boolean isOverviewActive()"));
        assertTrue(controller.contains("glass.isAllAppsActive() || glass.isOverviewActive()"));
        assertTrue(resolver.contains("resolveTopPackage"));
        assertTrue(controller.contains("confirmLauncherHomeFocus"));
        assertTrue(controller.contains("external task still foreground"));
        assertTrue(controller.contains("prearmGestureCaptureTarget"));
        assertTrue(controller.contains("gesture HOME kept live while external task foreground"));
        assertTrue(controller.contains("prearmAppBackdrop(\"gesture-home-unconfirmed\")"));

        for (String forbidden : new String[]{
                "resolveTopTaskPackage", "confirmLauncherHomeFocus",
                "scheduleLauncherHomeFocusRecheck", "prearmGestureCaptureTarget",
                "hookDockGestureTarget", "hookOverviewStateEvent"}) {
            assertFalse("scene implementation remains in MainHook: " + forbidden,
                    hook.contains(forbidden));
        }
        assertFalse(hook.contains("launcherResumed = hasFocus;"));
    }

    @Test public void appDockPullInteractionLatchIsIndependentOfDynamicCapture() throws Exception {
        String glass = source("DockLiquidGlassView.java");
        int start = glass.indexOf("void onDockGestureMotion(int action, float rawY)");
        int end = glass.indexOf("/** Called from the launcher's dedicated performEnterRecent haptic event. */", start);
        assertTrue(start >= 0 && end > start);
        String gestureMethod = glass.substring(start, end);
        assertTrue(gestureMethod.contains("setExternalAppDockInteraction(externalAppInteraction)"));
        assertTrue(gestureMethod.contains("setExternalAppDockInteraction(false)"));
        assertFalse(gestureMethod.contains("dynamicAppCapture"));
        assertTrue(gestureMethod.indexOf("setExternalAppDockInteraction(externalAppInteraction)")
                < gestureMethod.indexOf("armAppBackdropForGestureDown()"));
    }

    @Test public void allAppsStateCarriesNoCaptureRoot() throws Exception {
        String glass = source("DockLiquidGlassView.java");
        String allApps = source("AllAppsStateHooks.java");
        assertTrue(glass.contains("void setAllAppsActive(boolean active)"));
        assertFalse(glass.contains("setAllAppsActive(boolean active, View"));
        assertFalse(allApps.contains("captureRoot"));
    }

    @Test public void mainHookIsOnlyCompositionRootForLauncherSceneModules() throws Exception {
        String hook = source("MainHook.java");
        source("ForegroundTaskResolver.java");
        source("LauncherSceneController.java");
        source("AllAppsStateHooks.java");
        source("RecentsStateHooks.java");
        assertTrue(hook.contains("LauncherSceneController"));
        assertFalse(hook.contains("DrawerStatusServiceImpl"));
        assertFalse(hook.contains("DockStateManager$mainStateObserver$1"));
        assertFalse(hook.contains("DockStateManager$recentsListener$1"));
    }

    @Test public void mingouLegacyWorkstationFallbackIsGone() throws Exception {
        String hook = source("MainHook.java");
        assertFalse(hook.contains("isMingouLaptopPcModeEnabled"));
        assertFalse(hook.contains("setMingouLaptopPcModeEnabled"));
        assertFalse(hook.contains("Mingou workstation"));
    }
}
