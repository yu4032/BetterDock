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

    @Test public void workstationAlwaysUsesWallpaper() {
        CaptureSceneState state = new CaptureSceneState();
        state.setWorkstationWallpaperOnly(true, 1L, false, false, false);
        assertEquals(CaptureScene.HOME, state.desired());
        assertEquals(CaptureScene.HOME, state.resolve(2L, true, false, false));
        state.setWorkstationWallpaperOnly(false, 3L, false, false, false);
        assertEquals(CaptureScene.APP, state.desired());
    }

    @Test public void prearmRevisionRejectsOlderFrames() {
        CaptureSceneState state = new CaptureSceneState();
        long oldRevision = state.revision();
        state.prearmRecents(10L);
        assertEquals(CaptureScene.RECENTS, state.desired());
        assertFalse(state.matches(CaptureScene.APP, oldRevision));
    }
}
