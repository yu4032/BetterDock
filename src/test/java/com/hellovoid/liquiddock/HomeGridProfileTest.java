package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HomeGridProfileTest {
    @Test public void tenBySixAndEightByFourExposeExactOrientationCounts() {
        assertEquals(8, HomeGridProfile.GRID_8X4.columns(false));
        assertEquals(4, HomeGridProfile.GRID_8X4.rows(false));
        assertEquals(4, HomeGridProfile.GRID_8X4.columns(true));
        assertEquals(8, HomeGridProfile.GRID_8X4.rows(true));
        assertEquals(10, HomeGridProfile.GRID_10X6.columns(false));
        assertEquals(6, HomeGridProfile.GRID_10X6.rows(false));
        assertEquals(6, HomeGridProfile.GRID_10X6.columns(true));
        assertEquals(10, HomeGridProfile.GRID_10X6.rows(true));
    }

    @Test public void persistedProfileFallsBackToEightByFour() {
        assertEquals(HomeGridProfile.GRID_10X6, HomeGridProfile.fromPersisted("10x6"));
        assertEquals(HomeGridProfile.GRID_8X4, HomeGridProfile.fromPersisted("unknown"));
        assertEquals(HomeGridProfile.GRID_8X4, HomeGridProfile.fromPersisted(null));
    }

    @Test public void profileMatchesOnlyOwnCountsAndTranspose() {
        assertTrue(HomeGridProfile.GRID_10X6.matchesCounts(10, 6));
        assertTrue(HomeGridProfile.GRID_10X6.matchesCounts(6, 10));
        assertFalse(HomeGridProfile.GRID_10X6.matchesCounts(8, 4));
    }
}
