package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Regression contract: a symmetric horizontal-distance adjustment must not translate the grid. */
public class HomeGridHorizontalCenteringContractTest {
    @Test public void oversizedSourceCellShrinksAroundTheScreenCenter() {
        HomeGridHorizontalCenteringPolicy.Geometry geometry =
                HomeGridHorizontalCenteringPolicy.resolve(3008, 10, 320, 40);

        assertEquals(292, geometry.cellSize);
        assertEquals(0, geometry.gap);
        assertEquals(44, geometry.left);
        assertEquals(44, geometry.right(3008, 10));
    }

    @Test public void symmetricDistancePreservesCenterWithGapRemainder() {
        HomeGridHorizontalCenteringPolicy.Geometry geometry =
                HomeGridHorizontalCenteringPolicy.resolve(3008, 10, 250, 40);

        int right = geometry.right(3008, 10);
        assertTrue(Math.abs(geometry.left - right) <= 1);
        assertEquals(250, geometry.cellSize);
        assertEquals(47, geometry.gap);
    }
}
