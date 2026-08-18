package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class Miuix307RecentsInputContractTest {
    private static String read(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/" + name));
    }

    @Test public void specialized307PipelineTracksFloatingDockGestureAcrossAllMoves() throws Exception {
        String entry = read("ModuleMain.java");
        String hook = read("Miuix307RecentsInputHook.java");

        assertTrue(entry.contains("Miuix307RecentsInputHook.install(classLoader)"));
        assertTrue(hook.contains("MiuixGlassHook.class, \"install\""));
        assertTrue(hook.contains("getRootView()"));
        assertTrue(hook.contains("setOnTouchListener"));
        assertTrue(hook.contains("return false"));
        assertTrue(hook.contains("\"dispatchTouchEvent\""));
        assertTrue(hook.contains("isTouchInDockArea"));
        assertTrue(hook.contains("gestureActive"));
        assertTrue(hook.contains("onDockTouchEvent()"));
        assertTrue(hook.contains("onDockGestureMotion"));
    }

    @Test public void specialized307PipelineRestoresExactOverviewBoundaries() throws Exception {
        String hook = read("Miuix307RecentsInputHook.java");
        assertTrue(hook.contains("com.miui.home.launcher.dock.v3.GestureToRecent"));
        assertTrue(hook.contains("com.miui.home.recents.event."));
        assertTrue(hook.contains("\"EnterOverviewStateEvent\""));
        assertTrue(hook.contains("\"ExitOverviewStateEvent\""));
        assertTrue(hook.contains("setOverviewActive"));
    }
}
