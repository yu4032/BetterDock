package com.hellovoid.liquiddock;

import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;

public class FreeformCaptureExclusionTest {
    @Test public void visibleFreeformOrUnknownModeRequiresExclusion() throws Exception {
        Class<?> policy = load("com.hellovoid.liquiddock.FreeformCapturePolicy");
        Method method = policy.getDeclaredMethod("shouldExclude", int.class, boolean.class);
        method.setAccessible(true);
        assertEquals(Boolean.TRUE, method.invoke(null, 5, true));
        assertEquals(Boolean.TRUE, method.invoke(null, -1, true));
        assertEquals(Boolean.FALSE, method.invoke(null, 1, true));
        assertEquals(Boolean.FALSE, method.invoke(null, 5, false));
        assertEquals(Boolean.FALSE, method.invoke(null, -1, false));
    }

    @Test public void existingDockAndDragNamesRemainDeduplicatedAndOrdered() throws Exception {
        Class<?> helper = load("com.hellovoid.liquiddock.CaptureExclusionNames");
        Method merge = helper.getDeclaredMethod(
                "merge", String.class, String.class, Collection.class);
        merge.setAccessible(true);
        String[] names = (String[]) merge.invoke(null, "Floating Dock", "drag-layer",
                Arrays.asList("Floating Dock", null, ""));
        assertArrayEquals(new String[]{"Floating Dock", "drag-layer"}, names);
    }

    @Test public void homeUsesFullDisplayOnlyForLiveFreeformBackdrop() throws Exception {
        Method sourceFor = CaptureSourcePolicy.class.getDeclaredMethod("sourceFor",
                CaptureScene.class, boolean.class, boolean.class, boolean.class);
        sourceFor.setAccessible(true);
        assertEquals(CaptureSourcePolicy.Source.FULL_DISPLAY,
                sourceFor.invoke(null, CaptureScene.HOME, false, false, true));
        assertEquals(CaptureSourcePolicy.Source.WALLPAPER,
                sourceFor.invoke(null, CaptureScene.HOME, false, false, false));
    }

    @Test public void dockPreflightAndCaptureGateUseTaskLeashCapability() throws Exception {
        String dock = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java"));
        String resolver = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/FreeformLayerResolver.java"));
        String gate = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/FreeformCaptureLeashHook.java"));

        assertTrue("Dock keeps its existing fail-closed preflight boundary",
                dock.contains("freeformLayerResolver.resolveVisibleLayerNames()"));
        assertTrue("Preflight must depend on leash-provider readiness",
                resolver.contains("FreeformLeashRuntime.isProviderReady()"));
        assertTrue("Actual freeform exclusion must merge SurfaceControl task leashes",
                gate.contains("resolution.borrowedRemoteLeashes()"));
        assertTrue("Unsafe leash resolution must keep wallpaper fallback",
                gate.contains("args[5] = 2"));
        assertFalse("Freeform resolver must not invoke the retired SF debug lookup",
                resolver.contains("resolveAllByOwnerUids"));
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
