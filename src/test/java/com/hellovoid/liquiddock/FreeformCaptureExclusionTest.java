package com.hellovoid.liquiddock;

import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;

public class FreeformCaptureExclusionTest {
    @Test public void existingDockAndDragNamesRemainDeduplicatedAndOrdered() throws Exception {
        Class<?> helper = load("com.hellovoid.liquiddock.CaptureExclusionNames");
        Method merge = helper.getDeclaredMethod(
                "merge", String.class, String.class, Collection.class);
        merge.setAccessible(true);
        String[] names = (String[]) merge.invoke(null, "Floating Dock", "drag-layer",
                Arrays.asList("Floating Dock", null, ""));
        assertArrayEquals(new String[]{"Floating Dock", "drag-layer"}, names);
    }

    @Test public void homeAlwaysReturnsToWallpaperEvenWithVisibleFreeform() throws Exception {
        Method sourceFor = CaptureSourcePolicy.class.getDeclaredMethod("sourceFor",
                CaptureScene.class, boolean.class, boolean.class, boolean.class);
        sourceFor.setAccessible(true);
        assertEquals(CaptureSourcePolicy.Source.WALLPAPER,
                sourceFor.invoke(null, CaptureScene.HOME, false, false, true));
        assertEquals(CaptureSourcePolicy.Source.WALLPAPER,
                sourceFor.invoke(null, CaptureScene.HOME, false, false, false));
    }

    @Test public void appRemainsFullDisplaySubjectToFinalLeashGate() {
        assertEquals(CaptureSourcePolicy.Source.FULL_DISPLAY,
                CaptureSourcePolicy.sourceFor(CaptureScene.APP, false, false));
    }

    @Test public void temporaryDockPreflightHasNoTaskStateAuthority() throws Exception {
        String dock = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java"));
        String resolver = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/FreeformLayerResolver.java"));
        String gate = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/FreeformCaptureLeashHook.java"));

        assertTrue("Dock still has a temporary compatibility preflight until Task 4 cleanup",
                dock.contains("freeformLayerResolver.resolveVisibleLayerNames()"));
        assertFalse(resolver.contains("ActivityManager"));
        assertFalse(resolver.contains("RunningTaskInfo"));
        assertFalse(resolver.contains("getRunningTasks"));
        assertFalse(resolver.contains("getWindowingMode"));
        assertFalse(resolver.contains("displayId(task)"));
        assertTrue("Actual freeform exclusion must merge SurfaceControl task leashes",
                gate.contains("resolution.borrowedRemoteLeashes()"));
        assertTrue("Unsafe snapshot resolution must keep wallpaper fallback",
                gate.contains("args[5] = 2"));
    }

    private static Class<?> load(String name) throws Exception {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException missing) {
            fail("missing production class: " + name);
            throw missing;
        }
    }
}
