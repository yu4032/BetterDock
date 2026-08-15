package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

public class CaptureSceneStateTest {
    private static void setExternalAppDockInteraction(CaptureSceneState state, boolean active) {
        try {
            state.getClass().getDeclaredMethod("setExternalAppDockInteraction", boolean.class)
                    .invoke(state, active);
        } catch (NoSuchMethodException e) {
            fail("CaptureSceneState must expose setExternalAppDockInteraction(boolean)");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void setExternalAppForegroundConfirmed(CaptureSceneState state, boolean active) {
        try {
            state.getClass().getDeclaredMethod("setExternalAppForegroundConfirmed", boolean.class)
                    .invoke(state, active);
        } catch (NoSuchMethodException e) {
            fail("CaptureSceneState must expose setExternalAppForegroundConfirmed(boolean)");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test public void lifecycleAndRecentsResolveDeterministically() {
        CaptureSceneState state = new CaptureSceneState();
        assertEquals(CaptureScene.APP, state.resolve(1L, false, false, false));
        assertEquals(CaptureScene.HOME, state.resolve(1L, false, true, true));
        assertEquals(CaptureScene.RECENTS, state.resolve(1L, true, true, true));
    }

    @Test public void allAppsOwnsSceneEvenWhenLauncherOverlayStealsFocus() throws Exception {
        CaptureSceneState state = new CaptureSceneState();
        state.getClass().getDeclaredMethod("setAllAppsActive", boolean.class)
                .invoke(state, true);
        assertEquals("ALL_APPS", state.desired().name());
        assertEquals("ALL_APPS", state.resolve(1L, false, true, false).name());
        state.getClass().getDeclaredMethod("setAllAppsActive", boolean.class)
                .invoke(state, false);
        assertEquals(CaptureScene.APP, state.resolve(2L, false, true, false));
    }

    @Test public void confirmedLauncherStateOutranksAndClearsStaleGesturePrearm() {
        CaptureSceneState state = new CaptureSceneState();

        state.setGestureTarget("APP", 1_000L);
        state.setAllAppsActive(true);
        assertEquals(CaptureScene.ALL_APPS, state.resolve(2_000L, false, true, false));
        state.setAllAppsActive(false);
        assertEquals(CaptureScene.HOME, state.resolve(2_500L, false, true, true));

        state.setGestureTarget("HOME", 3_000L);
        assertEquals(CaptureScene.RECENTS, state.resolve(4_000L, true, true, true));
    }

    @Test public void externalAppDockInteractionRejectsStaleHomeGestureBeforeHaptic() {
        CaptureSceneState state = new CaptureSceneState();
        state.setGestureTarget("HOME", 1_000L);
        setExternalAppDockInteraction(state, true);

        assertEquals(CaptureScene.APP, state.resolve(2_000L, false, true, false));
        assertEquals(CaptureSourcePolicy.Source.FULL_DISPLAY,
                CaptureSourcePolicy.sourceFor(state.resolve(2_000L, false, true, false)));
    }

    @Test public void externalAppDockInteractionStillAllowsRecentsLiveDomain() {
        CaptureSceneState state = new CaptureSceneState();
        setExternalAppDockInteraction(state, true);
        state.setGestureTarget("RECENTS", 1_000L);

        assertEquals(CaptureScene.RECENTS, state.resolve(2_000L, false, true, false));
        assertEquals(CaptureSourcePolicy.Source.FULL_DISPLAY,
                CaptureSourcePolicy.sourceFor(state.resolve(2_000L, false, true, false)));
    }

    @Test public void externalAppDockInteractionIgnoresStaleLauncherResumedUntilExplicitRelease() {
        CaptureSceneState state = new CaptureSceneState();
        setExternalAppDockInteraction(state, true);

        assertEquals(CaptureScene.APP, state.resolve(2_000L, false, true, true));
        assertEquals(CaptureSourcePolicy.Source.FULL_DISPLAY,
                CaptureSourcePolicy.sourceFor(state.resolve(2_000L, false, true, true)));

        setExternalAppDockInteraction(state, false);
        assertEquals(CaptureScene.HOME, state.resolve(3_000L, false, true, true));
    }

    @Test public void confirmedExternalForegroundSurvivesUntilExplicitHomeAuthority() {
        CaptureSceneState state = new CaptureSceneState();
        setExternalAppForegroundConfirmed(state, true);
        setExternalAppDockInteraction(state, true);

        assertEquals(CaptureScene.APP, state.resolve(2_000L, false, true, true));
        setExternalAppDockInteraction(state, false);
        assertEquals(CaptureScene.APP, state.resolve(3_000L, false, true, true));
        assertEquals(CaptureSourcePolicy.Source.FULL_DISPLAY,
                CaptureSourcePolicy.sourceFor(state.resolve(3_000L, false, true, true)));

        // Losing EXTERNAL proof is UNKNOWN, not proof of HOME.
        setExternalAppForegroundConfirmed(state, false);
        assertEquals(CaptureScene.APP, state.resolve(4_000L, false, true, true));

        state.setForegroundOwnership(ForegroundOwnership.HOME);
        assertEquals(CaptureScene.HOME, state.resolve(5_000L, false, true, true));
    }

    @Test public void releasingExternalAppDockInteractionDoesNotReviveUnconfirmedHomeHint() {
        CaptureSceneState state = new CaptureSceneState();
        setExternalAppDockInteraction(state, true);
        state.setGestureTarget("HOME", 1_000L);
        assertEquals(CaptureScene.APP, state.resolve(2_000L, false, true, false));

        setExternalAppDockInteraction(state, false);
        assertEquals(CaptureScene.APP, state.resolve(3_000L, false, true, false));
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
