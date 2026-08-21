package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HomeGridVerticalBoundsPolicyTest {
    @Test public void oversizedTenRowGridFitsAboveDockBar() {
        HomeGridVerticalBoundsPolicy.Geometry geometry =
                HomeGridVerticalBoundsPolicy.resolve(3008, 10, 320,
                        3, 0, 400, 0, 0);

        assertEquals(400, geometry.dockBarHeight);
        assertTrue(geometry.cellSize < 320);
        assertTrue(geometry.bottom >= 400);
        assertTrue(geometry.lastRowBottom(10) <= 3008 - geometry.bottom);
    }

    @Test public void positiveTopAndBottomAdjustmentsStillStayTouchable() {
        HomeGridVerticalBoundsPolicy.Geometry geometry =
                HomeGridVerticalBoundsPolicy.resolve(3008, 10, 320,
                        3, 0, 400, 20, 30);

        assertTrue(geometry.top >= 20);
        assertTrue(geometry.bottom >= 430);
        assertTrue(geometry.lastRowBottom(10) <= 3008 - geometry.bottom);
    }

    @Test public void rowGapAdjustmentDoesNotChangeTheVerticalSourceCellLimit() {
        HomeGridVerticalBoundsPolicy.Geometry compact =
                HomeGridVerticalBoundsPolicy.resolve(3008, 6, 240,
                        3, -2, 300, 0, 0);
        HomeGridVerticalBoundsPolicy.Geometry loose =
                HomeGridVerticalBoundsPolicy.resolve(3008, 6, 240,
                        3, 20, 300, 0, 0);

        assertTrue(compact.cellSize <= 240);
        assertTrue(loose.cellSize <= 240);
        assertEquals(1, compact.gap);
        assertEquals(23, loose.gap);
    }
}
