package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class Miuix307RecentsInputContractTest {
    private static String read(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/" + name));
    }

    @Test public void specialized307PipelineTracksOneDockGestureAcrossAllMoves() throws Exception {
        String entry = read("ModuleMain.java");
        String hook = read("Miuix307RecentsInputHook.java");

        assertTrue(entry.contains("Miuix307RecentsInputHook.install(classLoader)"));
        assertTrue(hook.contains("\"dispatchTouchEvent\""));
        assertTrue(hook.contains("isTouchInDockArea"));
        assertTrue(hook.contains("gestureActive"));
        assertTrue(hook.contains("onDockTouchEvent()"));
        assertTrue(hook.contains("onDockGestureMotion"));
    }

    @Test public void specialized307PipelineRestoresExactOverviewBoundaries() throws Exception {
        String hook = read("Miuix307RecentsInputHook.java");
        assertTrue(hook.contains("\"GestureToRecent\""));
        assertTrue(hook.contains("\"EnterOverviewStateEvent\""));
        assertTrue(hook.contains("\"ExitOverviewStateEvent\""));
        assertTrue(hook.contains("setOverviewActive"));
    }
}
