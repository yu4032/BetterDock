package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HomeGridVerticalBoundsPolicyTest {
    @Test public void oversizedTenRowGridFitsAboveDockBar() {
        HomeGridVerticalBoundsPolicy.Geometry geometry =
                HomeGridVerticalBoundsPolicy.resolve(3008, 10, 320, 3, 400, 0, 0);

        assertEquals(400, geometry.dockBarHeight);
        assertTrue(geometry.cellSize < 320);
        assertTrue(geometry.bottom >= 400);
        assertTrue(geometry.lastRowBottom(10) <= 3008 - geometry.bottom);
    }

    @Test public void positiveTopAndBottomAdjustmentsStillStayTouchable() {
        HomeGridVerticalBoundsPolicy.Geometry geometry =
                HomeGridVerticalBoundsPolicy.resolve(3008, 10, 320, 3, 400, 20, 30);

        assertTrue(geometry.top >= 20);
        assertTrue(geometry.bottom >= 430);
        assertTrue(geometry.lastRowBottom(10) <= 3008 - geometry.bottom);
    }
}
