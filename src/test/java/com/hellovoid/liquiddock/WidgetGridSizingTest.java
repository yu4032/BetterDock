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
    public void twoStackedTwoByOneWidgetsShareTheTwoByTwoOuterFrame() {
        int[] xs = {0, 112, 224};
        int[] ys = {0, 108, 216};

        int[] whole = WidgetGridSizing.gridRect(
                0, 0, 2, 2, xs, ys, 100, 100, 12, 8);
        int[] top = WidgetGridSizing.gridRect(
                0, 0, 2, 1, xs, ys, 100, 100, 12, 8);
        int[] bottom = WidgetGridSizing.gridRect(
                0, 1, 2, 1, xs, ys, 100, 100, 12, 8);

        assertArrayEquals(new int[]{0, 2, 212, 204}, whole);
        assertEquals(whole[0], top[0]);
        assertEquals(whole[0] + whole[2], top[0] + top[2]);
        assertEquals(whole[1], top[1]);
        assertEquals(whole[1] + whole[3], bottom[1] + bottom[3]);
        assertEquals(12, bottom[1] - (top[1] + top[3]));
    }

    @Test
    public void mixedOneByOneAndTwoByOneWidgetsKeepTheSameGutter() {
        int[] xs = {0, 112, 224, 336};
        int[] ys = {0, 108, 216};

        int[] oneByOne = WidgetGridSizing.gridRect(
                0, 0, 1, 1, xs, ys, 100, 100, 12, 8);
        int[] twoByOne = WidgetGridSizing.gridRect(
                1, 0, 2, 1, xs, ys, 100, 100, 12, 8);

        assertEquals(12,
                twoByOne[0] - (oneByOne[0] + oneByOne[2]));
    }

    @Test
    public void smallerAxisReceivesOnlyTheMissingGutterInset() {
        int[] xs = {0, 108, 216};
        int[] ys = {0, 114, 228};

        int[] left = WidgetGridSizing.gridRect(
                0, 0, 1, 1, xs, ys, 100, 100, 8, 14);
        int[] right = WidgetGridSizing.gridRect(
                1, 0, 1, 1, xs, ys, 100, 100, 8, 14);

        assertArrayEquals(new int[]{3, 0, 94, 100}, left);
        assertEquals(14, right[0] - (left[0] + left[2]));
    }

    @Test
    public void uniformGutterUsesTheLargerGridGap() {
        assertEquals(14, WidgetGridSizing.uniformGutter(14, 8));
        assertEquals(14, WidgetGridSizing.uniformGutter(8, 14));
        assertEquals(0, WidgetGridSizing.uniformGutter(-2, -4));
    }
}
