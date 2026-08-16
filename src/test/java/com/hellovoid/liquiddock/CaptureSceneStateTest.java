package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

public class CaptureSceneStateTest {
    @Test public void unknownBaselineFailsClosedUntilSystemUiResolvesOwnership() {
        CaptureSceneState state = new CaptureSceneState();
        assertEquals(CaptureScene.UNKNOWN, state.desired());
        assertEquals(CaptureScene.UNKNOWN, state.resolve(1L, false, false, false));
        assertEquals(CaptureScene.APP, state.resolve(1L, false, true, false));
        assertEquals(CaptureScene.HOME, state.resolve(1L, false, true, true));
        assertEquals(CaptureScene.RECENTS, state.resolve(1L, true, false, false));
    }

    @Test public void allAppsOwnsSceneEvenWhenLauncherOverlayStealsFocus() throws Exception {
        CaptureSceneState state = new CaptureSceneState();
        state.getClass().getDeclaredMethod("setAllAppsActive", boolean.class)
                .invoke(state, true);
        assertEquals("ALL_APPS", state.desired().name());
        assertEquals("ALL_APPS", state.resolve(1L, false, false, false).name());
        state.getClass().getDeclaredMethod("setAllAppsActive", boolean.class)
                .invoke(state, false);
        assertEquals(CaptureScene.APP, state.resolve(2L, false, true, false));
    }

    @Test public void gestureTargetExpiresAndCanBeInterrupted() {
        CaptureSceneState state = new CaptureSceneState();
        state.setGestureTarget("RECENTS", 1_000L);
        long revision = state.revision();
        assertEquals(CaptureScene.RECENTS, state.desired());
        assertTrue(state.matches(CaptureScene.RECENTS, revision));
        state.setGestureTarget("HOME", 2_000L);
        assertEquals(CaptureScene.HOME, state.desired());
        assertFalse(state.matches(CaptureScene.RECENTS, revision));
        assertFalse(state.gestureTargetExpired(1_000_000L));
        assertTrue(state.gestureTargetExpired(1_500_003_000L));
    }

    @Test public void ownershipBoundaryClearInvalidatesOnlyHomeGestureTarget() {
        CaptureSceneState state = new CaptureSceneState();

        state.setGestureTarget("HOME", 1_000L);
        assertTrue(state.clearGestureTargetIfHome());
        assertTrue(state.refresh(2_000L, false, true, false));
        assertEquals(CaptureScene.APP, state.desired());

        state.setGestureTarget("RECENTS", 3_000L);
        assertFalse(state.clearGestureTargetIfHome());
        assertEquals(CaptureScene.RECENTS, state.resolve(4_000L, false, true, false));

        state.setGestureTarget("APP", 5_000L);
        assertFalse(state.clearGestureTargetIfHome());
        assertEquals(CaptureScene.APP, state.resolve(6_000L, false, true, false));
    }

    @Test public void workstationSuspendedTracksFlagOnly() {
        CaptureSceneState state = new CaptureSceneState();
        assertFalse(state.workstationSuspended());
        state.setWorkstationSuspended(true, 1L, false, false, false);
        assertTrue(state.workstationSuspended());
        assertEquals(CaptureScene.UNKNOWN, state.resolve(2L, false, false, false));
        state.setWorkstationSuspended(false, 3L, false, false, false);
        assertFalse(state.workstationSuspended());
    }

    @Test public void prearmRevisionRejectsOlderFrames() {
        CaptureSceneState state = new CaptureSceneState();
        long oldRevision = state.revision();
        state.prearmRecents(10L);
        assertEquals(CaptureScene.RECENTS, state.desired());
        assertFalse(state.matches(CaptureScene.UNKNOWN, oldRevision));
    }

    @Test public void appToHomeGestureKeepsLiveAppUntilAnimationEnd() {
        CaptureSceneState state = new CaptureSceneState();
        state.setGestureTarget("APP", 1_000L);
        state.setGestureTarget("HOME", 2_000L);

        // SystemUI may already report HOME while Launcher is still rendering CLOSE_TO_HOME.
        assertEquals(CaptureScene.APP, state.desired());
        assertEquals(CaptureScene.APP, state.resolve(500_000_000L, false, true, true));

        state.setGestureTarget("HOME_ANIMATION_END", 600_000_000L);
        assertEquals(CaptureScene.HOME, state.desired());
        assertEquals(CaptureScene.HOME, state.resolve(600_000_001L, false, true, true));
    }

    @Test public void appToHomeHoldCanBeInterruptedAndHasFailureWatchdog() {
        CaptureSceneState state = new CaptureSceneState();
        state.setGestureTarget("APP", 1_000L);
        state.setGestureTarget("HOME", 2_000L);
        state.setGestureTarget("RECENTS", 3_000L);
        assertEquals(CaptureScene.RECENTS, state.resolve(4_000L, false, true, true));

        state.setGestureTarget("APP", 10_000L);
        state.setGestureTarget("HOME", 11_000L);
        assertEquals(CaptureScene.APP, state.resolve(1_900_000_000L, false, true, true));
        assertEquals(CaptureScene.HOME, state.resolve(2_100_000_000L, false, true, true));
    }
}
