package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Regression contract: DragController bounds must follow the live Launcher root after rotation. */
public class HomeGridDragBoundsContractTest {
    @Test public void tenBySixOwnsLauncherDragControllerBoundsFromLiveRoot() throws Exception {
        String entry = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/ModuleMain.java"));
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/HomeGridDragBoundsHook.java"));

        assertTrue(entry.contains("HomeGridDragBoundsHook.install(classLoader"));
        assertTrue(hook.contains("com.miui.home.launcher.Launcher"));
        assertTrue(hook.contains("getScreenHeightForDragController"));
        assertTrue(hook.contains("getScreenWidthForDragController"));
        assertTrue(hook.contains("getRootView"));
        assertTrue(hook.contains("root.getHeight()"));
        assertTrue(hook.contains("root.getWidth()"));
        assertTrue(hook.contains("HomeGridProfile.GRID_10X6"));
    }
}
