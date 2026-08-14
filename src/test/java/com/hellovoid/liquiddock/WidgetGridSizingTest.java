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
    public void oneByOneUsesAlmostTheEntireCellSlot() {
        int[] xs = {0, 112, 224};
        int[] ys = {0, 108, 216};

        assertArrayEquals(new int[]{4, 4, 102, 100},
                WidgetGridSizing.slotRect(0, 0, 1, 1, xs, ys, 100, 100, 4));
        assertArrayEquals(new int[]{110, 4, 106, 100},
                WidgetGridSizing.slotRect(1, 0, 1, 1, xs, ys, 100, 100, 4));
    }

    @Test
    public void everyAdjacentWidgetPairUsesTwiceTheSamePadding() {
        int[] xs = {0, 112, 224, 336};
        int[] ys = {0, 108, 216};
        int padding = 4;

        int[] oneByOne = WidgetGridSizing.slotRect(
                0, 0, 1, 1, xs, ys, 100, 100, padding);
        int[] twoByOne = WidgetGridSizing.slotRect(
                1, 0, 2, 1, xs, ys, 100, 100, padding);
        int[] top = WidgetGridSizing.slotRect(
                0, 0, 2, 1, xs, ys, 100, 100, padding);
        int[] bottom = WidgetGridSizing.slotRect(
                0, 1, 2, 1, xs, ys, 100, 100, padding);

        assertEquals(padding * 2,
                twoByOne[0] - (oneByOne[0] + oneByOne[2]));
        assertEquals(padding * 2,
                bottom[1] - (top[1] + top[3]));
    }

    @Test
    public void twoStackedTwoByOneWidgetsShareTheTwoByTwoOuterFrame() {
        int[] xs = {0, 112, 224};
        int[] ys = {0, 108, 216};
        int padding = 4;

        int[] whole = WidgetGridSizing.slotRect(
                0, 0, 2, 2, xs, ys, 100, 100, padding);
        int[] top = WidgetGridSizing.slotRect(
                0, 0, 2, 1, xs, ys, 100, 100, padding);
        int[] bottom = WidgetGridSizing.slotRect(
                0, 1, 2, 1, xs, ys, 100, 100, padding);

        assertEquals(whole[0], top[0]);
        assertEquals(whole[0] + whole[2], top[0] + top[2]);
        assertEquals(whole[1], top[1]);
        assertEquals(whole[1] + whole[3], bottom[1] + bottom[3]);
        assertEquals(padding * 2,
                bottom[1] - (top[1] + top[3]));
    }

    @Test
    public void visualPaddingScalesWithCellSize() {
        assertEquals(4, WidgetGridSizing.visualPadding(100, 100));
        assertEquals(8, WidgetGridSizing.visualPadding(200, 200));
        assertEquals(1, WidgetGridSizing.visualPadding(1, 1));
    }

    @Test
    public void invalidSlotGeometryReturnsEmptyRect() {
        int[] xs = {0, 112};
        int[] ys = {0, 108};
        assertArrayEquals(new int[]{0, 0, 0, 0},
                WidgetGridSizing.slotRect(1, 0, 2, 1, xs, ys, 100, 100, 4));
    }
}
