package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression contracts for capture-scene races discovered in the full state-flow audit. */
public class CaptureRaceConvergenceContractTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/" + name));
    }

    @Test public void foregroundResolverIsTriStateAndControllerHasSinglePropagationPath()
            throws Exception {
        String resolver = source("ForegroundTaskResolver.java");
        String controller = source("LauncherSceneController.java");
        assertTrue(Files.exists(Path.of(
                "src/main/java/com/hellovoid/liquiddock/ForegroundOwnership.java")));
        assertTrue(resolver.contains("ForegroundOwnership.UNKNOWN"));
        assertTrue(resolver.contains("ForegroundOwnership.HOME"));
        assertTrue(resolver.contains("ForegroundOwnership.EXTERNAL"));
        assertTrue(controller.contains("applyForegroundObservation"));
        assertTrue(controller.contains("observeForegroundOwnership"));
        assertTrue(controller.contains("setForegroundOwnership"));
        assertFalse(controller.contains("foregroundTaskResolver.resolveTopPackage"));
    }

    @Test public void unknownForegroundObservationCannotClearExternalAuthority() throws Exception {
        String controller = source("LauncherSceneController.java");
        int start = controller.indexOf("applyForegroundObservation");
        int end = controller.indexOf("observeForegroundOwnership", start);
        assertTrue(start >= 0 && end > start);
        String method = controller.substring(start, end);
        assertTrue(method.contains("ForegroundOwnership.UNKNOWN"));
        assertTrue(method.contains("leave authority unchanged"));
        assertFalse(method.contains("setForegroundOwnership(ForegroundOwnership.HOME)"));
    }

    @Test public void unknownCannotReviveHomeAfterLauncherWasObservedAway() {
        CaptureSceneState state = new CaptureSceneState();
        state.setLauncherAwayHint(true);
        state.refresh(1L, false, true, true);
        assertEquals(CaptureScene.APP, state.desired());

        state.setForegroundOwnership(ForegroundOwnership.UNKNOWN);
        state.refresh(2L, false, true, true);
        assertEquals(CaptureScene.APP, state.desired());

        state.setForegroundOwnership(ForegroundOwnership.HOME);
        state.refresh(3L, false, true, true);
        assertEquals(CaptureScene.HOME, state.desired());
    }

    @Test public void interactionReleaseDoesNotInvalidateSameAppCaptureIdentity() {
        CaptureSceneState state = new CaptureSceneState();
        state.setExternalAppForegroundConfirmed(true);
        state.refresh(1L, false, true, false);
        assertEquals(CaptureScene.APP, state.desired());
        long before = state.revision();

        state.setExternalAppDockInteraction(true);
        state.refresh(2L, false, true, true);
        state.setExternalAppDockInteraction(false);
        state.refresh(3L, false, true, true);

        assertEquals(CaptureScene.APP, state.desired());
        assertEquals(before, state.revision());
    }

    @Test public void allAppsPrearmIsBoundedAndCannotOverrideExternalAuthority() {
        CaptureSceneState state = new CaptureSceneState();
        state.setExternalAppForegroundConfirmed(true);
        state.prearmAllApps(10L);
        assertEquals(CaptureScene.APP, state.resolve(11L, false, true, true));

        // UNKNOWN is not HOME. Only explicit HOME authority makes launcher-owned prearm valid.
        state.setExternalAppForegroundConfirmed(false);
        assertEquals(CaptureScene.APP, state.resolve(12L, false, true, true));
        state.setForegroundOwnership(ForegroundOwnership.HOME);
        state.prearmAllApps(20L);
        assertEquals(CaptureScene.ALL_APPS, state.resolve(21L, false, true, true));
        assertEquals(CaptureScene.HOME, state.resolve(2_000_000_000L, false, true, true));
    }

    @Test public void normalAllAppsTransitionUsesPrearmNotConfirmedDrawerState() throws Exception {
        String hooks = source("AllAppsStateHooks.java");
        assertTrue(hooks.contains("prearmAllAppsCapture"));
        int normal = hooks.indexOf("AllAppsTransitionController");
        int drawer = hooks.indexOf("dispatchDrawerOpen");
        assertTrue(normal >= 0 && drawer > normal);
        String normalBlock = hooks.substring(normal, drawer);
        assertFalse(normalBlock.contains("setAllAppsActive(true)"));
    }

    @Test public void partialHookGroupsCannotWritePersistentState() throws Exception {
        String allApps = source("AllAppsStateHooks.java");
        String recents = source("RecentsStateHooks.java");
        String main = source("MainHook.java");

        assertTrue(allApps.contains("laptopHooksInstalled"));
        assertTrue(allApps.contains("if (laptopHooksInstalled) glass.setAllAppsActive(true)"));
        assertTrue(allApps.contains("if (drawerStatusHooksInstalled) glass.setAllAppsActive(true)"));
        assertTrue(allApps.contains("drawer-partial-open"));

        assertTrue(recents.contains("mainObserverAuthoritative"));
        assertTrue(recents.contains("recentsListenerAuthoritative"));
        assertTrue(recents.contains("if (mainObserverAuthoritative && !recentsListenerAuthoritative)"));
        assertTrue(recents.contains("if (recentsListenerAuthoritative)"));
        assertTrue(recents.contains("return mainObserverHooked || recentsListenerHooked"));

        assertTrue(main.contains("dockDragHooksReady"));
        assertTrue(main.contains("dockDragHooksReady = startHooked && endHooked"));
        assertTrue(main.contains("drag-start-partial-hook"));
        assertTrue(main.contains("drag-end-partial-hook"));
    }

    @Test public void partialStockRecentsHooksNeverMixWithConstructorFallback() throws Exception {
        String hooks = source("RecentsStateHooks.java");
        assertTrue(hooks.contains("recentsListenerAuthoritative"));
        assertTrue(hooks.contains("return mainObserverHooked || recentsListenerHooked"));
        assertTrue(hooks.contains("prearmRecentsCaptureHint"));
        assertFalse(hooks.contains("boolean installed = mainObserverHooked && recentsListenerHooked"));
    }

    @Test public void recentsAndWorkstationDelayedStateUseSessionTokens() throws Exception {
        String glass = source("DockLiquidGlassView.java");
        assertTrue(glass.contains("recentsPrearmToken"));
        assertTrue(glass.contains("clearRecentsPrearmWindow"));
        assertTrue(glass.contains("workstationRecentsSessionToken"));
        assertTrue(glass.contains("session != workstationRecentsSessionToken"));
    }

    @Test public void asyncFailuresAlwaysRedirtyAndScheduleBoundedRetry() throws Exception {
        String glass = source("DockLiquidGlassView.java");
        assertTrue(glass.contains("scheduleCaptureFailureRetry"));
        assertTrue(glass.contains("captureFailureRetryToken"));
        assertTrue(glass.contains("CAPTURE_FAILURE_RETRY_DELAYS_MS"));
        assertFalse(glass.contains("async capture crop failed\", e);\n                if (sourceDirty) requestStateCapture()"));
    }

    @Test public void crossThreadCaptureReferencesHaveExplicitVisibility() throws Exception {
        String glass = source("DockLiquidGlassView.java");
        assertTrue(glass.contains("private volatile long activeCaptureAttempt"));
        assertTrue(glass.contains("private volatile LiveScreenCapture liveCapture"));
        assertTrue(glass.contains("private volatile boolean wallpaperCacheReady"));
        assertTrue(glass.contains("private volatile long wallpaperTransformRevision"));
    }

    @Test public void wallpaperCacheIsTransformScopedAndCommittedAfterValidation() throws Exception {
        String glass = source("DockLiquidGlassView.java");
        assertTrue(glass.contains("wallpaperCacheLock"));
        assertTrue(glass.contains("wallpaperTransformRevision"));
        assertTrue(glass.contains("cacheWallpaperTransformRevision"));
        assertTrue(glass.contains("requestWallpaperTransformRevision"));
        assertTrue(glass.contains("commitWallpaperCache"));
        assertFalse(glass.contains("cacheWallpaperStrip(strip, request);"));
    }

    @Test public void synchronousCompletionAlsoChecksSceneAndWallpaperTransformIdentity()
            throws Exception {
        String glass = source("DockLiquidGlassView.java");
        int start = glass.indexOf("final CroppedFrame frame = cropped;");
        int end = glass.indexOf("/** Shared completion path for async captures", start);
        assertTrue(start >= 0 && end > start);
        String syncCompletion = glass.substring(start, end);
        assertTrue(syncCompletion.contains("sceneState.matches(requestScene, requestSceneRevision)"));
        assertTrue(syncCompletion.contains("isWallpaperTransformCurrent"));
    }
}
