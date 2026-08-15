package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Regression tests for the fast Recents -> Home source-domain handoff. */
public class RecentsHomeTransitionTest {
    @Test
    public void confirmedHomeGestureTemporarilyOverridesStaleRecentsVisibility() {
        CaptureSceneState state = new CaptureSceneState();
        long now = 10_000_000_000L;

        state.setForegroundOwnership(ForegroundOwnership.HOME);
        assertEquals(CaptureScene.RECENTS,
                state.resolve(now, true, true, true));

        state.setGestureTarget("HOME", now);
        assertEquals("HOME-targeted navigation must cross to wallpaper even if the official "
                        + "Recents-hide callback is one frame late",
                CaptureScene.HOME,
                state.resolve(now + 1L, true, true, true));
    }

    @Test
    public void homeOverrideIsBoundedAndFallsBackToRecentsWhenGestureDoesNotComplete() {
        CaptureSceneState state = new CaptureSceneState();
        long now = 20_000_000_000L;
        state.setForegroundOwnership(ForegroundOwnership.HOME);
        state.setGestureTarget("HOME", now);

        assertEquals(CaptureScene.HOME,
                state.resolve(now + 100_000_000L, true, true, true));
        assertEquals("stale/cancelled HOME prearm must not permanently suppress real Recents",
                CaptureScene.RECENTS,
                state.resolve(now + 1_600_000_000L, true, true, true));
    }

    @Test
    public void externalAppHomeHintCannotOverrideVisibleRecents() {
        CaptureSceneState state = new CaptureSceneState();
        long now = 30_000_000_000L;
        state.setForegroundOwnership(ForegroundOwnership.EXTERNAL);
        state.setGestureTarget("HOME", now);

        assertEquals("speculative HOME from an external app must keep the live Recents domain",
                CaptureScene.RECENTS,
                state.resolve(now + 1L, true, true, false));
    }
}
