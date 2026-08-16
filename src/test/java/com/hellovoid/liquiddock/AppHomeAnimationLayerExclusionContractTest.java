package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class AppHomeAnimationLayerExclusionContractTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test public void vendorWindowElementLeashHasDedicatedAdapter() throws Exception {
        Path path = Path.of("src/main/java/com/hellovoid/liquiddock/AppHomeAnimationLayerExclusion.java");
        assertTrue(Files.exists(path));
        String source = Files.readString(path);
        assertTrue(source.contains("com.miui.home.recents.anim.WindowElement"));
        assertTrue(source.contains("bindIconLayerLeashIfNeeded"));
        assertTrue(source.contains("mFloatingIconLayerLeash"));
        assertTrue(source.contains("currentValidSurface"));
        assertTrue(source.contains("isValid()"));
    }

    @Test public void adapterIsInstalledWithAppHomeLifecycleHook() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquiddock/AppHomeAnimationHook.java");
        assertTrue(hook.contains("AppHomeAnimationLayerExclusion.install(classLoader)"));
    }

    @Test public void captureSceneExposesReadOnlyPendingState() throws Exception {
        String state = read("src/main/java/com/hellovoid/liquiddock/CaptureSceneState.java");
        assertTrue(state.contains("boolean appHomeHandoffPending()"));
        assertTrue(state.contains("return appHomeHandoffPending;"));
    }

    @Test public void dockExcludesLauncherRootOnlyDuringPendingAppHomeCapture() throws Exception {
        String dock = read("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java");
        assertTrue(dock.contains("requestScene == CaptureScene.APP"));
        assertTrue(dock.contains("sceneState.appHomeHandoffPending()"));
        assertTrue(dock.contains("AppHomeAnimationLayerExclusion.currentValidSurface()"));
        assertTrue(dock.contains("appendCaptureExcludeLayer"));
        assertTrue(dock.contains("appHomeLauncherRootExcluded"));
    }
}
