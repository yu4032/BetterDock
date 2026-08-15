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
        assertTrue(controller.contains("foregroundTaskResolver.resolveTopPackage"));
        assertTrue(hook.contains("isExternalAppForeground(glass.getContext())"));
        assertTrue(glass.contains("externalAppForegroundConfirmed"));
        assertTrue(glass.contains("externalAppForegroundConfirmed ||"));
        assertTrue(glass.contains("setExternalAppForegroundConfirmed(externalAppForegroundConfirmed)"));
    }

    @Test public void confirmedExternalForegroundCannotBeOverriddenByStaleLauncherResumed() throws Exception {
        String state = source("CaptureSceneState.java");
        assertTrue(state.contains("externalAppForegroundConfirmed"));
        int start = state.indexOf("if (externalAppForegroundConfirmed)");
        int end = state.indexOf("if (externalAppDockInteraction)", start);
        assertTrue(start >= 0 && end > start);
        String authorityResolve = state.substring(start, end);
        assertFalse(authorityResolve.contains("launcherResumed"));
    }

    @Test public void externalAppInteractionLockCannotBeOverriddenByStaleLauncherResumed() throws Exception {
        String state = source("CaptureSceneState.java");
        int start = state.indexOf("if (externalAppDockInteraction)");
        int end = state.indexOf("if (gestureTarget != null", start);
        assertTrue(start >= 0 && end > start);
        String lockedResolve = state.substring(start, end);
        assertFalse(lockedResolve.contains("launcherResumed"));
    }

    @Test public void authoritativeHomeExplicitlyReleasesExternalForegroundAuthority() throws Exception {
        String controller = source("LauncherSceneController.java");
        String glass = source("DockLiquidGlassView.java");
        assertTrue(controller.contains("onAuthoritativeHomeConfirmed"));
        assertTrue(glass.contains("void onAuthoritativeHomeConfirmed()"));
        assertTrue(glass.contains("setExternalAppForegroundConfirmed(false)"));
        assertTrue(glass.contains("setExternalAppDockInteraction(false)"));
    }

    @Test public void appGesturePrearmDoesNotRejectOnlyBecauseLauncherResumeHintIsStale() throws Exception {
        String glass = source("DockLiquidGlassView.java");
        int start = glass.indexOf("private void armAppBackdropForGestureDown()");
        int end = glass.indexOf("/** Launcher genuinely lost window focus", start);
        assertTrue(start >= 0 && end > start);
        String method = glass.substring(start, end);
        assertFalse(method.contains("|| launcherResumed"));
    }
}
