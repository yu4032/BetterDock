package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class SystemUiTransitionContractTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/" + name), StandardCharsets.UTF_8);
    }

    @Test public void systemUiInstallsTransitionSource() throws Exception {
        String entry = source("ModuleMain.java");
        assertTrue(entry.contains("SystemUiTransitionSource.install"));
    }

    @Test public void transitionSourceObservesReadyMergeFinishAndPushes() throws Exception {
        String source = source("SystemUiTransitionSource.java");
        assertTrue(source.contains("com.android.wm.shell.transition.Transitions"));
        assertTrue(source.contains("onTransitionReady"));
        assertTrue(source.contains("onTransitionMerged"));
        assertTrue(source.contains("onTransitionFinished"));
        assertTrue(source.contains("APP_TO_LAUNCHER_START"));
        assertTrue(source.contains("FLAG_ONEWAY") || source.contains("pushTransitionEvent"));
        assertFalse(source.contains("Thread.sleep"));
        assertFalse(source.contains("CountDownLatch"));
        assertFalse(source.contains("await("));
    }

    @Test public void launcherRuntimeDoesNotMutateOwnershipDuringTransition() throws Exception {
        String runtime = source("SystemUiTransitionRuntime.java");
        assertTrue(runtime.contains("beginAppToLauncherVisualHold"));
        assertTrue(runtime.contains("finishAppToLauncherVisualHold"));
        assertTrue(runtime.contains("resolveVisualHoldToOverview"));
        assertFalse(runtime.contains("HomeOwnershipRuntime.request"));
        assertFalse(runtime.contains("glass.setLauncherState("));
        assertFalse(runtime.contains("glass.onLauncherFocused("));
        assertFalse(runtime.contains("glass.onLauncherFocusLost("));
    }

    @Test public void staleSystemUiGenerationCannotReplaceNewerTransitionState() throws Exception {
        String runtime = source("SystemUiTransitionRuntime.java");
        assertTrue(runtime.contains("generation < sourceGeneration"));
        assertTrue(runtime.contains("generation != sourceGeneration"));
    }

    @Test public void homeFinishKeepsHoldAcrossARealDisplayFrameBeforeCapture() throws Exception {
        String runtime = source("SystemUiTransitionRuntime.java");

        int finish = runtime.indexOf(
                "static void finishAppToLauncherVisualHold(long generation, long tokenId, boolean aborted)");
        int next = runtime.indexOf("\n    static void ", finish + 1);
        String body = next > finish ? runtime.substring(finish, next) : runtime.substring(finish);

        assertTrue("non-aborted Shell finish must arm a dedicated HOME compositor release",
                body.contains("scheduleHomeVisualRelease"));
        assertFalse("Shell finish must not immediately request HOME capture",
                body.contains("requestCapture(\"systemui-transition-home-finished\")"));

        int helper = runtime.indexOf("private static void scheduleHomeVisualRelease(");
        int nextHelper = runtime.indexOf("\n    private static void ", helper + 1);
        String helperBody = nextHelper > helper
                ? runtime.substring(helper, nextHelper) : runtime.substring(helper);
        assertTrue("HOME release must cross VSYNC rather than a same-loop post",
                helperBody.contains("postOnAnimation"));
        assertTrue("the hold must remain active until the frame barrier callback",
                helperBody.indexOf("postOnAnimation") < helperBody.indexOf("visualHold = false;"));
        assertTrue("stale in-flight full-display candidates must be invalidated before release",
                helperBody.contains("cancelPendingCaptureWork"));
        assertTrue("HOME capture belongs after the compositor barrier releases the hold",
                helperBody.indexOf("visualHold = false;")
                        < helperBody.indexOf("requestCapture(\"systemui-transition-home-finished\")"));
        assertFalse("HOME settling must never use a guessed millisecond delay",
                helperBody.contains("postDelayed("));
    }

    @Test public void appSupersedeInvalidatesPendingHomeFrameRelease() throws Exception {
        String runtime = source("SystemUiTransitionRuntime.java");
        assertTrue(runtime.contains("homeReleaseSequence"));

        int app = runtime.indexOf(
                "static void resolveLauncherToApp(long generation, long tokenId, int displayId)");
        int next = runtime.indexOf("\n    private static void ", app + 1);
        if (next < 0) next = runtime.indexOf("\n    static void ", app + 1);
        String body = next > app ? runtime.substring(app, next) : runtime.substring(app);
        assertTrue("reverse transition must invalidate any pending HOME frame release",
                body.contains("homeReleaseSequence++"));
    }

    @Test public void migrated307HasNoLauncherHomePrearm() throws Exception {
        String pipeline = source("Miuix307MaterialPipeline.java");
        String glassHook = source("MiuixGlassHook.java");
        assertFalse(pipeline.contains("installHomeGesturePrearm"));
        assertFalse(pipeline.contains("com.miui.home.launcher.dock.v3.GestureToHome"));
        assertFalse(pipeline.contains("com.miui.home.recents.util.StateNotifyUtils"));
        assertFalse(glassHook.contains("onHomeTransitionStart"));
        assertFalse(glassHook.contains("setGestureCaptureTarget(\"HOME\")"));
    }
}
