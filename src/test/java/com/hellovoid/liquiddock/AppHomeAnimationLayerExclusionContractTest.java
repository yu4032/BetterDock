package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class AppHomeAnimationLayerExclusionContractTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test public void vendorFastLaunchActivityLeashHasDedicatedAdapter() throws Exception {
        Path path = Path.of("src/main/java/com/hellovoid/liquiddock/AppHomeAnimationLayerExclusion.java");
        assertTrue(Files.exists(path));
        String source = Files.readString(path);
        assertTrue(source.contains("com.miui.home.recents.anim.FastLaunchWindowElement$getActivityOptions$1"));
        assertTrue(source.contains("startActivityFinished"));
        assertTrue(source.contains("mHomeActivityLeash"));
        assertTrue(source.contains("currentValidSurface"));
        assertTrue(source.contains("isValid()"));
        assertFalse(source.contains("private static final String BIND_METHOD"));
        assertFalse(source.contains("private static final String LEASH_FIELD"));
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

    @Test public void dockExcludesHomeActivityForAnyPendingFullDisplayCapture() throws Exception {
        String dock = read("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java");
        int start = dock.indexOf("boolean appHomeActivityLeashExcluded");
        assertTrue(start >= 0);
        int end = dock.indexOf("APP HOME capture Home-activity excluded=", start);
        assertTrue(end > start);
        String block = dock.substring(start, end);
        assertTrue(block.contains("sceneState.appHomeHandoffPending()"));
        assertTrue(block.contains("AppHomeAnimationLayerExclusion.currentValidSurface()"));
        assertTrue(block.contains("appendCaptureExcludeLayer"));
        assertFalse(block.contains("requestScene == CaptureScene.APP"));
    }
}
