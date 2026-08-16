package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class WallpaperZoomHookContractTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test public void localWallpaperPerFrameScaleHasDedicatedHook() throws Exception {
        Path path = Path.of("src/main/java/com/hellovoid/liquiddock/WallpaperZoomHook.java");
        assertTrue(Files.exists(path));
        String source = Files.readString(path);
        assertTrue(source.contains("com.miui.home.recents.anim.LocalWallpaperElement"));
        assertTrue(source.contains("updateTargetParams"));
        assertTrue(source.contains("float.class"));
        assertTrue(source.contains("WallpaperZoomRuntime.onScale"));
        assertTrue(source.contains("chain.proceed"));
    }

    @Test public void systemWallpaperPathIsDiagnosticOnly() throws Exception {
        String source = read("src/main/java/com/hellovoid/liquiddock/WallpaperZoomHook.java");
        assertTrue(source.contains("com.miui.home.recents.anim.SystemWallpaperElement"));
        assertTrue(source.contains("animTo"));
        assertTrue(source.contains("setTo"));
        assertFalse(source.contains("sendWallpaperCommand("));
        assertFalse(source.contains("setWallpaperZoomOut("));
    }

    @Test public void zoomRuntimeOnlyForwardsToCurrentGlass() throws Exception {
        Path path = Path.of("src/main/java/com/hellovoid/liquiddock/WallpaperZoomRuntime.java");
        assertTrue(Files.exists(path));
        String source = Files.readString(path);
        assertTrue(source.contains("WeakReference<DockLiquidGlassView>"));
        assertTrue(source.contains("setLauncherWallpaperVisualScale"));
        assertFalse(source.contains("HomeOwnershipPolicy"));
        assertFalse(source.contains("setLauncherState"));
    }

    @Test public void hookInstallsAlongsideValidatedRecentsLifecycle() throws Exception {
        String source = read("src/main/java/com/hellovoid/liquiddock/RecentsHapticHook.java");
        assertTrue(source.contains("RecentsExitAnimationHook.install(classLoader)"));
        assertTrue(source.contains("WallpaperZoomHook.install(classLoader)"));
    }

    @Test public void currentGlassBindingAlsoBindsZoomRuntime() throws Exception {
        String runtime = read("src/main/java/com/hellovoid/liquiddock/HomeOwnershipRuntime.java");
        assertTrue(runtime.contains("WallpaperZoomRuntime.bind(glass)"));
    }
}
