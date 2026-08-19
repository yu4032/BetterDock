package com.hellovoid.liquiddock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

/** Integration contract between the existing geometry owner and the optional profile overlay. */
public class HomeGridHookProfileContractTest {

    @Before
    public void enableWidgetAdaptation() {
        WidgetGridSizing.setWidgetAdaptationEnabled(true);
    }

    @After
    public void resetWidgetAdaptation() {
        WidgetGridSizing.setWidgetAdaptationEnabled(false);
    }

    @Test
    public void mainInstallsBaseGeometryBeforeTypedProfileOverlay() throws Exception {
        String main = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/MainHook.java"));

        int base = main.indexOf("HomeGridHook.install(classLoader, customGridEnabled");
        int overlay = main.indexOf(
                "HomeGridProfileOverlayHook.install(classLoader, customGridEnabled, grid.profile)");
        assertTrue("base custom-grid geometry must be installed", base >= 0);
        assertTrue("typed 10x6 overlay must be installed", overlay >= 0);
        assertTrue("base geometry must exist before the profile-sized overlay", base < overlay);
    }

    @Test
    public void workspaceDropRuleReceivesTypedProfileAndAppliesRotationSafePolicy() throws Exception {
        String module = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/ModuleMain.java"));
        String drop = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/WorkspaceDropRuleHook.java"));

        assertTrue(module.contains("runtimeConfig.enabled && runtimeConfig.grid.enabled,"));
        assertTrue(module.contains("runtimeConfig.grid.profile"));
        assertTrue(drop.contains("boolean customGridEnabled, HomeGridProfile profile"));
        assertTrue(drop.contains("!customGridEnabled || installed"));
        assertTrue(drop.contains("int cellX = (Integer) chain.getArg(0)"));
        assertTrue(drop.contains("int cellY = (Integer) chain.getArg(1)"));
        assertTrue(drop.contains("int spanX = (Integer) chain.getArg(2)"));
        assertTrue(drop.contains("int spanY = (Integer) chain.getArg(3)"));
        assertTrue(drop.contains("WorkspaceDropPolicy.isPlacementAllowed("));
    }

    @Test
    public void landscapeTenBySixStillComputesFourByTwoFrameAtGeometricEdge() {
        int[] xs = axis(10, 100);
        int[] ys = axis(6, 120);
        assertArrayEquals(new int[]{600, 480, 400, 240},
                WidgetGridSizing.gridRect(6, 4, 4, 2, xs, ys, 100, 120, 0, 0));
        assertArrayEquals(new int[]{0, 0, 0, 0},
                WidgetGridSizing.gridRect(7, 4, 4, 2, xs, ys, 100, 120, 0, 0));
    }

    @Test
    public void portraitSixByTenStillComputesFourByTwoFrameAtGeometricEdge() {
        int[] xs = axis(6, 100);
        int[] ys = axis(10, 120);
        assertArrayEquals(new int[]{200, 960, 400, 240},
                WidgetGridSizing.gridRect(2, 8, 4, 2, xs, ys, 100, 120, 0, 0));
        assertArrayEquals(new int[]{0, 0, 0, 0},
                WidgetGridSizing.gridRect(3, 8, 4, 2, xs, ys, 100, 120, 0, 0));
    }

    private static int[] axis(int count, int pitch) {
        int[] values = new int[count];
        for (int i = 0; i < count; i++) values[i] = i * pitch;
        return values;
    }
}
