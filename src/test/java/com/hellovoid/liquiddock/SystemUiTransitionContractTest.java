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
        assertFalse(runtime.contains("setLauncherState("));
        assertFalse(runtime.contains("onLauncherFocused("));
        assertFalse(runtime.contains("onLauncherFocusLost("));
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
