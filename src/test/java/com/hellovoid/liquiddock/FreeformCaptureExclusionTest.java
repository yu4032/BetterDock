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

    @Test public void appWithoutFreeformRemainsFullDisplay() {
        assertEquals(CaptureSourcePolicy.Source.FULL_DISPLAY,
                CaptureSourcePolicy.sourceFor(CaptureScene.APP, false, false));
    }

    @Test public void visibleFreeformForcesMode1CaptureToWallpaper() throws Exception {
        String gate = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/FreeformCaptureLeashHook.java"));
        int visibleStart = gate.indexOf("if (visibleFreeform)");
        assertTrue("missing visible-freeform capture branch", visibleStart >= 0);
        int logStart = gate.indexOf("logGateStateIfChanged", visibleStart);
        assertTrue("missing gate logging after visible-freeform branch", logStart > visibleStart);
        String visibleBranch = gate.substring(visibleStart, logStart);

        assertTrue("visible freeform must rewrite mode-1 to wallpaper mode",
                visibleBranch.contains("args[5] = 2"));
        assertTrue("visible freeform must clear handle exclusions before wallpaper capture",
                visibleBranch.contains("args[3] = null"));
        assertTrue("visible freeform must clear name exclusions before wallpaper capture",
                visibleBranch.contains("args[4] = null"));
        assertTrue("visible freeform action must explicitly record wallpaper ownership",
                visibleBranch.contains("WALLPAPER_VISIBLE_FREEFORM"));
        assertFalse("visible freeform must never pass unresolved APP capture through",
                visibleBranch.contains("PASS_THROUGH_UNRESOLVED_FREEFORM"));
        assertFalse("visible freeform must not remain mode-1 by excluding task leashes",
                visibleBranch.contains("EXCLUDE_TASK_LEASHES"));
    }

    @Test public void appVisibleFreeformIsWallpaperOwnedBeforeMode1Submission() throws Exception {
        String dock = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java"));
        int sourceStart = dock.indexOf("final boolean visibleFreeform");
        assertTrue("missing capture source selection", sourceStart >= 0);
        int capturingStart = dock.indexOf("capturing = true", sourceStart);
        assertTrue("missing capture submission boundary", capturingStart > sourceStart);
        String sourceSelection = dock.substring(sourceStart, capturingStart);

        assertTrue("APP source selection must inspect visible freeform state",
                sourceSelection.contains("requestScene == CaptureScene.APP"));
        assertTrue("APP source selection must query current visible freeform state",
                sourceSelection.contains("freeformLayerResolver.hasVisibleFreeformTasks()"));
        assertTrue("APP + visible freeform must select wallpaper before capture submission",
                sourceSelection.contains("selectedSource = CaptureSourcePolicy.Source.WALLPAPER"));
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
        assertTrue("Final freeform gate must still inspect the authoritative resolution",
                gate.contains("resolution.hasVisibleFreeformTasks()"));
        assertTrue("Unexpected gate exceptions must retain a wallpaper fallback",
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
