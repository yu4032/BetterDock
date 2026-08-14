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
    public void spanSizeFillsTheWholeGridFootprint() {
        assertEquals(100, WidgetGridSizing.spanSize(1, 100, 12, 0, 0));
        assertEquals(212, WidgetGridSizing.spanSize(2, 100, 12, 0, 0));
        assertEquals(436, WidgetGridSizing.spanSize(4, 100, 12, 0, 0));
    }

    @Test
    public void marginsDoNotShrinkWidgetEdges() {
        assertEquals(212, WidgetGridSizing.spanSize(2, 100, 12, 3, 5));
        assertEquals(208, WidgetGridSizing.spanSize(2, 100, 8, 4, 4));
    }

    @Test
    public void twoRowWidgetsIncludeTheRowGap() {
        assertEquals(208, WidgetGridSizing.spanSize(2, 100, 8, 0, 0));
    }
}
