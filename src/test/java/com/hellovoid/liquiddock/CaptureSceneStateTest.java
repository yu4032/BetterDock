package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

public class CaptureSceneStateTest {
    @Test public void lifecycleAndRecentsResolveDeterministically() {
        CaptureSceneState state = new CaptureSceneState();
        assertEquals(CaptureScene.APP, state.resolve(1L, false, false, false));
        assertEquals(CaptureScene.HOME, state.resolve(1L, false, true, true));
        assertEquals(CaptureScene.RECENTS, state.resolve(1L, true, true, true));
    }

    @Test public void allAppsOwnsSceneEvenWhenLauncherOverlayStealsFocus() {
        CaptureSceneState state = new CaptureSceneState();
        state.setAllAppsActive(true, false);
        assertEquals(CaptureScene.ALL_APPS, state.desired());
        // Official Laptop overlay enables focus when All Apps opens, so Launcher main focus
        // may be false. Normal mode must still keep All Apps as the owning scene.
        assertEquals(CaptureScene.ALL_APPS, state.resolve(1L, false, true, false));
        state.setAllAppsActive(false, false);
        assertEquals(CaptureScene.APP, state.resolve(2L, false, true, false));
    }

    @Test public void workstationAllAppsAliasesHomeWithoutBackdropRevision() {
        CaptureSceneState state = new CaptureSceneState();
        assertTrue(state.refresh(1L, false, true, true));
        assertEquals(CaptureScene.HOME, state.desired());
        long homeRevision = state.revision();

        state.setAllAppsActive(true, true);
        assertTrue(state.allAppsActive());
        assertEquals(CaptureScene.HOME, state.desired());
        assertEquals(CaptureScene.HOME, state.resolve(2L, false, true, false));
        assertEquals(homeRevision, state.revision());

        state.setAllAppsActive(false, true);
        assertFalse(state.allAppsActive());
        assertEquals(CaptureScene.HOME, state.desired());
        assertEquals(homeRevision, state.revision());
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

    @Test public void focusLossClearInvalidatesOnlyHomeGestureTarget() {
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
        assertEquals(CaptureScene.APP, state.resolve(2L, false, false, false));
        state.setWorkstationSuspended(false, 3L, false, false, false);
        assertFalse(state.workstationSuspended());
    }

    @Test public void prearmRevisionRejectsOlderFrames() {
        CaptureSceneState state = new CaptureSceneState();
        long oldRevision = state.revision();
        state.prearmRecents(10L);
        assertEquals(CaptureScene.RECENTS, state.desired());
        assertFalse(state.matches(CaptureScene.APP, oldRevision));
    }
}
