package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Guards foreground-task authority at the APP Dock gesture boundary. */
public class ForegroundAuthorityContractTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/" + name));
    }

    @Test public void dockDownUsesTopTaskAuthorityInsteadOfLauncherResumeHint() throws Exception {
        String controller = source("LauncherSceneController.java");
        String hook = source("MainHook.java");
        String glass = source("DockLiquidGlassView.java");

        assertTrue(controller.contains("isExternalAppForeground"));
        assertTrue(controller.contains("observeForegroundOwnership"));
        assertTrue(controller.contains("foregroundTaskResolver.resolve("));
        assertFalse(controller.contains("foregroundTaskResolver.resolveTopPackage"));
        assertTrue(hook.contains("isExternalAppForeground(glass.getContext())"));
        assertTrue(glass.contains("externalAppForegroundConfirmed"));
        assertTrue(glass.contains("externalAppForegroundConfirmed ||"));
        assertTrue(glass.contains("setExternalAppForegroundConfirmed(externalAppForegroundConfirmed)"));
    }

    @Test public void confirmedExternalForegroundCannotBeOverriddenByStaleLauncherResumed() throws Exception {
        String state = source("CaptureSceneState.java");
        assertTrue(state.contains("externalAppForegroundConfirmed"));
        int resolve = state.indexOf("CaptureScene resolve(");
        int start = state.indexOf("foregroundOwnership == ForegroundOwnership.EXTERNAL", resolve);
        int end = state.indexOf("if (externalAppDockInteraction)", start);
        assertTrue(resolve >= 0 && start >= resolve && end > start);
        String authorityResolve = state.substring(start, end);
        assertFalse(authorityResolve.contains("launcherResumed"));
    }

    @Test public void externalAppInteractionLockCannotBeOverriddenByStaleLauncherResumed() throws Exception {
        String state = source("CaptureSceneState.java");
        int resolve = state.indexOf("CaptureScene resolve(");
        int start = state.indexOf("if (externalAppDockInteraction)", resolve);
        int end = state.indexOf("if (allAppsPrearmUntilNanos", start);
        assertTrue(resolve >= 0 && start >= resolve && end > start);
        String lockedResolve = state.substring(start, end);
        assertFalse(lockedResolve.contains("launcherResumed"));
    }

    @Test public void authoritativeHomeExplicitlyReleasesExternalForegroundAuthority() throws Exception {
        String controller = source("LauncherSceneController.java");
        String glass = source("DockLiquidGlassView.java");
        assertTrue(controller.contains("onAuthoritativeHomeConfirmed"));
        assertTrue(glass.contains("void onAuthoritativeHomeConfirmed()"));
        assertTrue(glass.contains("setForegroundOwnership(ForegroundOwnership.HOME)"));
        assertTrue(glass.contains("setExternalAppDockInteraction(false)"));
    }

    @Test public void appGesturePrearmDoesNotRejectOnlyBecauseLauncherResumeHintIsStale() throws Exception {
        String glass = source("DockLiquidGlassView.java");
        int start = glass.indexOf("private void armAppBackdropForGestureDown()");
        int end = glass.indexOf("void setForegroundOwnership(ForegroundOwnership ownership)", start);
        assertTrue(start >= 0 && end > start);
        String method = glass.substring(start, end);
        assertFalse(method.contains("|| launcherResumed"));
    }

    @Test public void oneEventConsumesOnlyOneForegroundSnapshot() throws Exception {
        String controller = source("LauncherSceneController.java");
        assertTrue(controller.contains("applyForegroundObservation"));
        int focus = controller.indexOf("private boolean confirmLauncherHomeFocus");
        int recheck = controller.indexOf("private void scheduleLauncherHomeFocusRecheck", focus);
        assertTrue(focus >= 0 && recheck > focus);
        String focusMethod = controller.substring(focus, recheck);
        assertTrue(focusMethod.contains("ForegroundTaskResolver.Observation observation"));
        assertTrue(focusMethod.contains("applyForegroundObservation("));
        assertFalse(focusMethod.contains("observeForegroundOwnership(context"));

        int gesture = controller.indexOf("private void prearmGestureCaptureTarget");
        int hook = controller.indexOf("private void hookDockGestureTarget", gesture);
        assertTrue(gesture >= 0 && hook > gesture);
        String gestureMethod = controller.substring(gesture, hook);
        assertTrue(gestureMethod.contains("ForegroundTaskResolver.Observation observation"));
        assertTrue(gestureMethod.contains("applyForegroundObservation("));
        assertFalse(gestureMethod.contains("observeForegroundOwnership(glass.getContext()"));
    }
}
