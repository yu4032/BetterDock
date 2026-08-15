package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

/** Regression coverage for APP -> HOME capture settle and foreground-authority oscillation. */
public class HomeTransitionStabilityTest {
    private static final long MS = 1_000_000L;

    private static Object newAuthorityGate() throws Exception {
        Class<?> type = Class.forName("com.hellovoid.liquiddock.ForegroundAuthorityGate");
        Constructor<?> ctor = type.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static ForegroundOwnership filter(Object gate,
                                              ForegroundOwnership current,
                                              ForegroundOwnership observed,
                                              boolean allowHomeCommit,
                                              boolean allowExternalCommit,
                                              boolean returningFromExternal,
                                              long nowNanos) throws Exception {
        Method m = gate.getClass().getDeclaredMethod("filter",
                ForegroundOwnership.class, ForegroundOwnership.class,
                boolean.class, boolean.class, boolean.class, long.class);
        m.setAccessible(true);
        return (ForegroundOwnership) m.invoke(gate, current, observed,
                allowHomeCommit, allowExternalCommit, returningFromExternal, nowNanos);
    }

    @Test public void staleHomeObservationCannotFlipExternalWithoutHomeBoundary() throws Exception {
        Object gate = newAuthorityGate();
        assertEquals(ForegroundOwnership.EXTERNAL,
                filter(gate, ForegroundOwnership.EXTERNAL, ForegroundOwnership.HOME,
                        false, false, true, 10L));
    }

    @Test public void externalToHomeNeedsStableRepeatedFocusEvidence() throws Exception {
        Object gate = newAuthorityGate();
        assertEquals(ForegroundOwnership.EXTERNAL,
                filter(gate, ForegroundOwnership.EXTERNAL, ForegroundOwnership.HOME,
                        true, false, true, 0L));
        assertEquals(ForegroundOwnership.EXTERNAL,
                filter(gate, ForegroundOwnership.EXTERNAL, ForegroundOwnership.HOME,
                        true, false, true, 80L * MS));
        assertEquals(ForegroundOwnership.HOME,
                filter(gate, ForegroundOwnership.EXTERNAL, ForegroundOwnership.HOME,
                        true, false, true, 140L * MS));
    }

    @Test public void contradictoryObservationResetsHomeCandidate() throws Exception {
        Object gate = newAuthorityGate();
        assertEquals(ForegroundOwnership.EXTERNAL,
                filter(gate, ForegroundOwnership.EXTERNAL, ForegroundOwnership.HOME,
                        true, false, true, 0L));
        assertEquals(ForegroundOwnership.EXTERNAL,
                filter(gate, ForegroundOwnership.EXTERNAL, ForegroundOwnership.EXTERNAL,
                        true, true, true, 70L * MS));
        assertEquals(ForegroundOwnership.EXTERNAL,
                filter(gate, ForegroundOwnership.EXTERNAL, ForegroundOwnership.HOME,
                        true, false, true, 200L * MS));
    }

    @Test public void staleExternalObservationCannotFlipHomeWithoutExternalBoundary() throws Exception {
        Object gate = newAuthorityGate();
        assertEquals(ForegroundOwnership.HOME,
                filter(gate, ForegroundOwnership.HOME, ForegroundOwnership.EXTERNAL,
                        false, false, false, 10L));
        assertEquals(ForegroundOwnership.EXTERNAL,
                filter(gate, ForegroundOwnership.HOME, ForegroundOwnership.EXTERNAL,
                        false, true, false, 20L));
    }

    @Test public void homeSettleBlocksMode2ButStillRunsDomainBarrier() throws Exception {
        String glass = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java"));
        assertTrue(glass.contains("homeWallpaperSettleUntilNanos"));
        assertTrue(glass.contains("beginHomeWallpaperSettle"));
        assertTrue(glass.contains("isHomeWallpaperSettleActive"));
        assertTrue(glass.contains("homeWallpaperSettleToken"));
        assertTrue(glass.contains("homeWallpaperCaptureEpoch"));
        assertTrue(glass.contains("isHomeWallpaperResultCurrent"));

        int request = glass.indexOf("private void requestStateCapture(String reason)");
        int barrier = glass.indexOf("applyBackdropTransitionBarrier", request);
        int settleGate = glass.indexOf("isHomeWallpaperSettleActive", barrier);
        int allowed = glass.indexOf("isCaptureAllowed()", settleGate);
        assertTrue(request >= 0 && barrier > request && settleGate > barrier && allowed > settleGate);

        int kick = glass.indexOf("private final Runnable captureKick");
        int kickSettle = glass.indexOf("isHomeWallpaperSettleActive", kick);
        int clearDirty = glass.indexOf("sourceDirty = false", kick);
        assertTrue(kick >= 0 && kickSettle > kick && clearDirty > kickSettle);
    }

    @Test public void authoritativeHomeArmsSettleBeforeChangingScene() throws Exception {
        String glass = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java"));
        int start = glass.indexOf("void onAuthoritativeHomeConfirmed()");
        int end = glass.indexOf("void onLauncherFocusLost()", start);
        assertTrue(start >= 0 && end > start);
        String method = glass.substring(start, end);
        int settle = method.indexOf("beginHomeWallpaperSettle");
        int authority = method.indexOf("setForegroundOwnership(ForegroundOwnership.HOME)");
        assertTrue(settle >= 0 && authority > settle);
    }

    @Test public void transitionalHomeFramesCannotPolluteWallpaperCache() throws Exception {
        String glass = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java"));
        assertTrue(glass.contains("requestHomeWallpaperCaptureEpoch"));
        assertTrue(glass.contains("isHomeWallpaperResultCurrent(requestScene"));
        int commit = glass.indexOf("commitWallpaperCache(wallpaperCandidate");
        int resultGuard = glass.lastIndexOf("isHomeWallpaperResultCurrent", commit);
        assertTrue(commit > 0 && resultGuard > 0 && resultGuard < commit);
    }

    @Test public void brokenSurfaceComposerAppLayerProbeIsGone() throws Exception {
        String glass = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java"));
        assertFalse(glass.contains("android.view.ISurfaceComposer$Stub"));
        assertFalse(glass.contains("resolveAppLayerByUid"));
    }

    @Test public void controllerUsesGateAndOnlyFocusCanCommitHomeFromExternal() throws Exception {
        String controller = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/LauncherSceneController.java"));
        assertTrue(controller.contains("ForegroundAuthorityGate"));
        assertTrue(controller.contains("allowHomeCommit"));
        assertTrue(controller.contains("allowExternalCommit"));
        assertTrue(controller.contains("focus-home-confirm"));
        assertFalse(controller.contains("applyForegroundObservation(observation, glass, \"gesture-\" + target)"));
    }
}
