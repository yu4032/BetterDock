package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WidgetGridSizingTest {
    @Test
    public void supportedWidgetSpecsAreLimitedToLauncherSizes() {
        assertTrue(WidgetGridSizing.isSupportedSpec(1, 1));
        assertTrue(WidgetGridSizing.isSupportedSpec(2, 1));
        assertTrue(WidgetGridSizing.isSupportedSpec(2, 2));
        assertTrue(WidgetGridSizing.isSupportedSpec(4, 2));

        assertFalse(WidgetGridSizing.isSupportedSpec(3, 1));
        assertFalse(WidgetGridSizing.isSupportedSpec(4, 1));
    }

    @Test
    public void oneByOneUsesTheCompleteGridPitch() {
        int[] xs = {0, 112, 224};
        int[] ys = {0, 108, 216};

        assertArrayEquals(new int[]{0, 0, 112, 108},
                WidgetGridSizing.gridRect(
                        0, 0, 1, 1, xs, ys, 100, 100, 12, 8));
        assertArrayEquals(new int[]{112, 0, 112, 108},
                WidgetGridSizing.gridRect(
                        1, 0, 1, 1, xs, ys, 100, 100, 12, 8));
    }

    @Test
    public void adjacentWidgetAllocationsShareTheSameBoundary() {
        int[] xs = {0, 112, 224, 336};
        int[] ys = {0, 108, 216};

        int[] oneByOne = WidgetGridSizing.gridRect(
                0, 0, 1, 1, xs, ys, 100, 100, 12, 8);
        int[] twoByOne = WidgetGridSizing.gridRect(
                1, 0, 2, 1, xs, ys, 100, 100, 12, 8);
        int[] top = WidgetGridSizing.gridRect(
                0, 0, 2, 1, xs, ys, 100, 100, 12, 8);
        int[] bottom = WidgetGridSizing.gridRect(
                0, 1, 2, 1, xs, ys, 100, 100, 12, 8);

        assertEquals(oneByOne[0] + oneByOne[2], twoByOne[0]);
        assertEquals(top[1] + top[3], bottom[1]);
    }

    @Test
    public void twoByTwoFollowsIndependentHorizontalAndVerticalPitch() {
        int[] xs = {10, 120, 230, 340};
        int[] ys = {20, 150, 280};

        assertArrayEquals(new int[]{10, 20, 220, 260},
                WidgetGridSizing.gridRect(
                        0, 0, 2, 2, xs, ys, 100, 100, 10, 30));
    }

    @Test
    public void twoStackedTwoByOneAllocationsExactlyCoverOneTwoByTwo() {
        int[] xs = {0, 112, 224};
        int[] ys = {0, 108, 216};

        int[] whole = WidgetGridSizing.gridRect(
                0, 0, 2, 2, xs, ys, 100, 100, 12, 8);
        int[] top = WidgetGridSizing.gridRect(
                0, 0, 2, 1, xs, ys, 100, 100, 12, 8);
        int[] bottom = WidgetGridSizing.gridRect(
                0, 1, 2, 1, xs, ys, 100, 100, 12, 8);

        assertEquals(whole[0], top[0]);
        assertEquals(whole[0] + whole[2], top[0] + top[2]);
        assertEquals(whole[1], top[1]);
        assertEquals(top[1] + top[3], bottom[1]);
        assertEquals(whole[1] + whole[3], bottom[1] + bottom[3]);
    }

    @Test
    public void fourOneByOneAllocationsExactlyTileOneTwoByTwo() {
        int[] xs = {0, 112, 224};
        int[] ys = {0, 108, 216};

        int[] whole = WidgetGridSizing.gridRect(
                0, 0, 2, 2, xs, ys, 100, 100, 12, 8);
        int[] topLeft = WidgetGridSizing.gridRect(
                0, 0, 1, 1, xs, ys, 100, 100, 12, 8);
        int[] topRight = WidgetGridSizing.gridRect(
                1, 0, 1, 1, xs, ys, 100, 100, 12, 8);
        int[] bottomLeft = WidgetGridSizing.gridRect(
                0, 1, 1, 1, xs, ys, 100, 100, 12, 8);
        int[] bottomRight = WidgetGridSizing.gridRect(
                1, 1, 1, 1, xs, ys, 100, 100, 12, 8);

        assertEquals(whole[0], topLeft[0]);
        assertEquals(whole[1], topLeft[1]);
        assertEquals(topLeft[0] + topLeft[2], topRight[0]);
        assertEquals(topLeft[1] + topLeft[3], bottomLeft[1]);
        assertEquals(whole[0] + whole[2], topRight[0] + topRight[2]);
        assertEquals(whole[0] + whole[2], bottomRight[0] + bottomRight[2]);
        assertEquals(whole[1] + whole[3], bottomLeft[1] + bottomLeft[3]);
        assertEquals(whole[1] + whole[3], bottomRight[1] + bottomRight[3]);
    }

    @Test
    public void finalRowAndColumnExtrapolateTheMeasuredPitch() {
        int[] xs = {10, 120, 230};
        int[] ys = {20, 150, 280};

        assertArrayEquals(new int[]{230, 280, 110, 130},
                WidgetGridSizing.gridRect(
                        2, 2, 1, 1, xs, ys, 100, 100, 10, 30));
    }

    @Test
    public void aSingleCellAxisFallsBackToCellSizePlusGap() {
        int[] xs = {15};
        int[] ys = {25};

        assertArrayEquals(new int[]{15, 25, 112, 108},
                WidgetGridSizing.gridRect(
                        0, 0, 1, 1, xs, ys, 100, 100, 12, 8));
    }

    @Test
    public void invalidGridGeometryReturnsEmptyRect() {
        int[] xs = {0, 112};
        int[] ys = {0, 108};
        assertArrayEquals(new int[]{0, 0, 0, 0},
                WidgetGridSizing.gridRect(
                        1, 0, 2, 1, xs, ys, 100, 100, 12, 8));
    }
}
