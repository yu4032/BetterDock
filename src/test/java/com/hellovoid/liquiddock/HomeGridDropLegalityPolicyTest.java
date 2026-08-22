package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HomeGridDropLegalityPolicyTest {

    @Test
    public void exactTwoByTwoMustUseMacroblockOriginInEightByFour() {
        assertTrue(legal(HomeGridProfile.GRID_8X4, 8, 4, 0, 0, 2, 2));
        assertTrue(legal(HomeGridProfile.GRID_8X4, 8, 4, 6, 2, 2, 2));
        assertFalse(legal(HomeGridProfile.GRID_8X4, 8, 4, 1, 0, 2, 2));
        assertFalse(legal(HomeGridProfile.GRID_8X4, 8, 4, 2, 1, 2, 2));
        assertFalse(legal(HomeGridProfile.GRID_8X4, 8, 4, 7, 2, 2, 2));
    }

    @Test
    public void exactTwoByTwoMustUseMacroblockOriginInFourByEight() {
        assertTrue(legal(HomeGridProfile.GRID_8X4, 4, 8, 2, 6, 2, 2));
        assertFalse(legal(HomeGridProfile.GRID_8X4, 4, 8, 1, 6, 2, 2));
        assertFalse(legal(HomeGridProfile.GRID_8X4, 4, 8, 2, 7, 2, 2));
    }

    @Test
    public void exactTwoByTwoUsesSameRuleForTenBySixAndSixByTen() {
        assertTrue(legal(HomeGridProfile.GRID_10X6, 10, 6, 8, 4, 2, 2));
        assertFalse(legal(HomeGridProfile.GRID_10X6, 10, 6, 7, 4, 2, 2));
        assertTrue(legal(HomeGridProfile.GRID_10X6, 6, 10, 4, 8, 2, 2));
        assertFalse(legal(HomeGridProfile.GRID_10X6, 6, 10, 4, 9, 2, 2));
    }

    @Test
    public void otherSpansRemainFreeWithinGridBounds() {
        assertTrue(legal(HomeGridProfile.GRID_8X4, 8, 4, 1, 1, 4, 2));
        assertTrue(legal(HomeGridProfile.GRID_8X4, 8, 4, 1, 1, 1, 2));
        assertTrue(legal(HomeGridProfile.GRID_8X4, 8, 4, 7, 3, 1, 1));
        assertFalse(legal(HomeGridProfile.GRID_8X4, 8, 4, 7, 3, 2, 1));
    }

    @Test
    public void negativeCoordinatesAreNeverLegal() {
        assertFalse(legal(HomeGridProfile.GRID_8X4, 8, 4, -1, 0, 2, 2));
        assertFalse(legal(HomeGridProfile.GRID_8X4, 8, 4, 0, -1, 1, 1));
    }

    private static boolean legal(HomeGridProfile profile, int columns, int rows,
                                 int x, int y, int spanX, int spanY) {
        return HomeGridDropLegalityPolicy.isLegal(
                profile, columns, rows, x, y, spanX, spanY);
    }
}
