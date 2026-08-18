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

    @Test public void appToLauncherNeverFreezesRequestOrInstallPath() throws Exception {
        String runtime = source("SystemUiTransitionRuntime.java");

        assertFalse("SystemUI migration must not install requestStateCapture freeze gates",
                runtime.contains("installCaptureRequestGate"));
        assertFalse("SystemUI migration must not install installCapture/recycle freeze gates",
                runtime.contains("installCaptureInstallGate"));
        assertFalse("live transition start/finish must not invalidate animation readbacks",
                runtime.contains("cancelPendingCaptureWork"));
        assertTrue("APP_TO_LAUNCHER must activate the shared continuous capture burst",
                runtime.contains("setSystemUiTransitionActive(")
                        && runtime.contains("true, \"app-to-launcher-token-\""));
        assertTrue("transition start must request a normal composed frame",
                runtime.contains("requestCapture(\"systemui-transition-start\")"));
    }

    @Test public void homeFinishSwitchesSceneWithoutClosingCapturePath() throws Exception {
        String runtime = source("SystemUiTransitionRuntime.java");

        int finish = runtime.indexOf(
                "static void finishAppToLauncherVisualHold(long generation, long tokenId, boolean aborted)");
        int next = runtime.indexOf("\n    static void ", finish + 1);
        String body = next > finish ? runtime.substring(finish, next) : runtime.substring(finish);

        assertTrue("HOME must become authoritative only at real Shell finish",
                body.contains("applyStableScene(glass, true)"));
        assertTrue("HOME finish must stop only the SystemUI burst lease",
                body.contains("setSystemUiTransitionActive(")
                        && body.contains("false, \"home-finish-token-\""));
        assertTrue("HOME finish must immediately request a normal final capture",
                body.contains("requestCapture(\"systemui-transition-home-finished\")"));
        assertTrue("one additional post-VSYNC sample may settle the final compositor transaction",
                body.contains("postOnAnimation")
                        && body.contains("systemui-transition-home-post-vsync"));
        assertFalse("the post-VSYNC sample must not be a gate or delayed unlock",
                body.contains("postDelayed(") || body.contains("visualHold = false;\n                glass.requestCapture"));
    }

    @Test public void reverseTransitionStopsBurstAndReturnsToAppDirectly() throws Exception {
        String runtime = source("SystemUiTransitionRuntime.java");
        int app = runtime.indexOf(
                "static void resolveLauncherToApp(long generation, long tokenId, int displayId)");
        int next = runtime.indexOf("\n    private static void ", app + 1);
        if (next < 0) next = runtime.indexOf("\n    static void ", app + 1);
        String body = next > app ? runtime.substring(app, next) : runtime.substring(app);
        assertTrue(body.contains("setSystemUiTransitionActive(")
                && body.contains("false, \"launcher-to-app-token-\""));
        assertTrue(body.contains("applyStableScene(glass, false)"));
        assertTrue(body.contains("requestCapture(\"systemui-transition-app\")"));
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
