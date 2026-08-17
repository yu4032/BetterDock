package com.hellovoid.liquiddock;

import org.junit.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Regression coverage for normal Workspace geometry. X and Y axes must be independent. */
public class HomeGridGeometryPolicyTest {
    private static final int LEFT = 0;
    private static final int RIGHT = 1;
    private static final int TOP = 2;
    private static final int BOTTOM = 3;
    private static final int CELL_WIDTH = 4;
    private static final int CELL_HEIGHT = 5;
    private static final int WIDTH_GAP = 6;
    private static final int HEIGHT_GAP = 7;

    private static int[] geometry(int width, int height, int dockBarHeight,
            int countX, int countY, int baseCell, int baseHeightGap,
            int leftOffset, int rightOffset, int topOffset, int bottomOffset,
            int rowGapOffset) throws Exception {
        Class<?> policy;
        try {
            policy = Class.forName("com.hellovoid.liquiddock.HomeGridGeometryPolicy");
        } catch (ClassNotFoundException e) {
            fail("HomeGridGeometryPolicy must exist");
            throw new AssertionError(e);
        }
        Method method = policy.getDeclaredMethod("normalWorkspace",
                int.class, int.class, int.class, int.class, int.class, int.class, int.class,
                int.class, int.class, int.class, int.class, int.class);
        method.setAccessible(true);
        return (int[]) method.invoke(null, width, height, dockBarHeight,
                countX, countY, baseCell, baseHeightGap,
                leftOffset, rightOffset, topOffset, bottomOffset, rowGapOffset);
    }

    @Test
    public void horizontalOffsetsDoNotChangeVerticalGeometry() throws Exception {
        int[] baseline = geometry(2560, 1600, 220, 10, 6, 280, 3, 0, 0, 0, 0, 0);
        int[] narrowed = geometry(2560, 1600, 220, 10, 6, 280, 3, 300, 300, 0, 0, 0);
        assertTrue(narrowed[CELL_WIDTH] < baseline[CELL_WIDTH]);
        assertEquals(baseline[TOP], narrowed[TOP]);
        assertEquals(baseline[BOTTOM], narrowed[BOTTOM]);
        assertEquals(baseline[CELL_HEIGHT], narrowed[CELL_HEIGHT]);
        assertEquals(baseline[HEIGHT_GAP], narrowed[HEIGHT_GAP]);
    }

    @Test
    public void verticalOffsetsDoNotChangeHorizontalGeometry() throws Exception {
        int[] baseline = geometry(2560, 1600, 220, 10, 6, 280, 3, 0, 0, 0, 0, 0);
        int[] shortened = geometry(2560, 1600, 220, 10, 6, 280, 3, 0, 0, 100, 120, 0);
        assertTrue(shortened[CELL_HEIGHT] < baseline[CELL_HEIGHT]);
        assertEquals(baseline[LEFT], shortened[LEFT]);
        assertEquals(baseline[RIGHT], shortened[RIGHT]);
        assertEquals(baseline[CELL_WIDTH], shortened[CELL_WIDTH]);
        assertEquals(baseline[WIDTH_GAP], shortened[WIDTH_GAP]);
    }

    @Test
    public void tenBySixLandscapeFitsAndCentersBeforeOffsets() throws Exception {
        int width = 2560, height = 1600, dock = 220;
        int[] g = geometry(width, height, dock, 10, 6, 280, 3, 0, 0, 0, 0, 0);
        int gridRight = g[LEFT] + g[CELL_WIDTH] * 10 + g[WIDTH_GAP] * 9;
        int gridBottom = g[TOP] + g[CELL_HEIGHT] * 6 + g[HEIGHT_GAP] * 5;
        assertTrue(g[LEFT] >= 0 && g[RIGHT] >= 0 && g[TOP] >= 0 && g[BOTTOM] >= dock);
        assertTrue(gridRight <= width);
        assertTrue(gridBottom <= height - dock);
        assertTrue(Math.abs(g[LEFT] - (width - gridRight)) <= 1);
        assertTrue(Math.abs(g[TOP] - ((height - dock) - gridBottom)) <= 1);
    }

    @Test
    public void sixByTenPortraitFitsAndCentersBeforeOffsets() throws Exception {
        int width = 1600, height = 2560, dock = 220;
        int[] g = geometry(width, height, dock, 6, 10, 280, 3, 0, 0, 0, 0, 0);
        int gridRight = g[LEFT] + g[CELL_WIDTH] * 6 + g[WIDTH_GAP] * 5;
        int gridBottom = g[TOP] + g[CELL_HEIGHT] * 10 + g[HEIGHT_GAP] * 9;
        assertTrue(g[LEFT] >= 0 && g[RIGHT] >= 0 && g[TOP] >= 0 && g[BOTTOM] >= dock);
        assertTrue(gridRight <= width);
        assertTrue(gridBottom <= height - dock);
        assertTrue(Math.abs(g[LEFT] - (width - gridRight)) <= 1);
        assertTrue(Math.abs(g[TOP] - ((height - dock) - gridBottom)) <= 1);
    }

    @Test
    public void eightByFourKeepsPreferredCellWhenItAlreadyFits() throws Exception {
        int[] g = geometry(2560, 1600, 220, 8, 4, 240, 3, 0, 0, 0, 0, 0);
        assertEquals(240, g[CELL_WIDTH]);
        assertEquals(240, g[CELL_HEIGHT]);
        assertEquals(0, g[WIDTH_GAP]);
        assertEquals(3, g[HEIGHT_GAP]);
    }

    @Test
    public void normalWorkspaceHookUsesAxisIndependentPolicy() throws Exception {
        String source = Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/HomeGridHook.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("HomeGridGeometryPolicy.normalWorkspace"));
        assertTrue(source.contains("mCellWidth\", geometry[4]"));
        assertTrue(source.contains("mCellHeight\", geometry[5]"));
    }
}
