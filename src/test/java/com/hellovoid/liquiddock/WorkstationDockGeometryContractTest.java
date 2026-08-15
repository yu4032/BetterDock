package com.hellovoid.liquiddock;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression contracts for the visible workstation Dock geometry. */
public class WorkstationDockGeometryContractTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/" + name));
    }

    @Test public void workstationWidthOffsetDoesNotAccumulateAndRebasesOnNativeWidth() throws Exception {
        Class<?> type = Class.forName("com.hellovoid.liquiddock.WorkstationDockWidthState");
        Constructor<?> ctor = type.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object state = ctor.newInstance();
        Method targetWidth = type.getDeclaredMethod("targetWidth", int.class, int.class);
        targetWidth.setAccessible(true);

        assertEquals(1100, ((Number) targetWidth.invoke(state, 1000, 100)).intValue());
        // The next layout reports our already-adjusted width: do not add the offset again.
        assertEquals(1100, ((Number) targetWidth.invoke(state, 1100, 100)).intValue());
        // A genuinely new native measurement becomes the new baseline.
        assertEquals(1300, ((Number) targetWidth.invoke(state, 1200, 100)).intValue());
        assertEquals(1, ((Number) targetWidth.invoke(state, 1200, -5000)).intValue());
    }

    @Test public void workstationWidthTargetsVisibleDockContainerInsteadOfHiddenNormalBackground()
            throws Exception {
        String main = source("MainHook.java");
        int start = main.indexOf("private static void installWorkstationDockHooks");
        int end = main.indexOf("private static void installWorkstationModeGuard", start);
        assertTrue(start >= 0 && end > start);
        String workstationHooks = main.substring(start, end);
        assertFalse("workstation width must not mutate the hidden normal HotSeats background",
                workstationHooks.contains("widthOffset != 0) args[0]"));
        assertTrue("MainHook must install the visible workstation Dock geometry hook",
                main.contains("WorkstationDockGeometryHook.install(classLoader, config.workstation);"));

        String hook = source("WorkstationDockGeometryHook.java");
        assertTrue("the actual workstation Dock is discovered from its bound divider/view chain",
                hook.contains("HotSeatsListContentAdapter$LineViewHolder"));
        assertTrue("runtime ancestor resolution must select the workstation Dock container",
                hook.contains("DockContainer"));
    }

    @Test public void settledShadowPositionResyncIsNotWidthOnly() throws Exception {
        String main = source("MainHook.java");
        assertFalse("a moved Dock with unchanged width must still move its independent shadow",
                main.contains("if (!anim && bgW != lastShadowW)"));
        assertTrue("settled Dock frames must resync shadow geometry",
                main.contains("syncShadowGeometry();"));
    }
}
