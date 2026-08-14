package com.hellovoid.liquiddock;

import org.junit.Test;

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
    public void spanSizeMatchesTheFullGridFootprint() {
        assertEquals(100, WidgetGridSizing.gridSpanSize(1, 100, 12));
        assertEquals(212, WidgetGridSizing.gridSpanSize(2, 100, 12));
        assertEquals(436, WidgetGridSizing.gridSpanSize(4, 100, 12));
    }

    @Test
    public void twoRowWidgetsIncludeTheInternalRowGap() {
        assertEquals(208, WidgetGridSizing.gridSpanSize(2, 100, 8));
    }

    @Test
    public void negativeGapCannotShrinkTheGridFootprint() {
        assertEquals(200, WidgetGridSizing.gridSpanSize(2, 100, -12));
    }

    @Test
    public void invalidGeometryProducesNoSize() {
        assertEquals(0, WidgetGridSizing.gridSpanSize(0, 100, 8));
        assertEquals(0, WidgetGridSizing.gridSpanSize(2, 0, 8));
    }
}
