package com.hellovoid.liquiddock;

import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;

public class FreeformCaptureExclusionTest {
    @Test public void onlyVisibleFreeformTasksAreExcluded() throws Exception {
        Class<?> policy = load("com.hellovoid.liquiddock.FreeformCapturePolicy");
        Method method = policy.getDeclaredMethod("shouldExclude", int.class, boolean.class);
        method.setAccessible(true);
        assertEquals(Boolean.TRUE, method.invoke(null, 5, true));
        assertEquals(Boolean.FALSE, method.invoke(null, 1, true));
        assertEquals(Boolean.FALSE, method.invoke(null, 5, false));
    }

    @Test public void fullDisplayExclusionNamesAreDeduplicatedAndOrdered() throws Exception {
        Class<?> helper = load("com.hellovoid.liquiddock.CaptureExclusionNames");
        Method merge = helper.getDeclaredMethod(
                "merge", String.class, String.class, Collection.class);
        merge.setAccessible(true);
        String[] names = (String[]) merge.invoke(null, "Floating Dock", "drag-layer",
                Arrays.asList("freeform-a", "Floating Dock", null, "", "freeform-b"));
        assertArrayEquals(new String[]{"Floating Dock", "drag-layer", "freeform-a", "freeform-b"},
                names);
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

    @Test public void fullDisplayCaptureConsumesVisibleFreeformLayerNames() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java"));
        assertTrue("FULL_DISPLAY exclusion must resolve visible freeform layers",
                source.contains("freeformLayerResolver.resolveVisibleLayerNames()"));
        assertTrue("Dock, drag and freeform exclusions must share one composer",
                source.contains("CaptureExclusionNames.merge("));
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
