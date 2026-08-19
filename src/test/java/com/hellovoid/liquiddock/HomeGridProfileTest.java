package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Pure profile geometry contract shared by the 8x4 base and the 10x6 overlay. */
public class HomeGridProfileTest {

    @Test
    public void persistedProfilesResolveToExactOrientationCounts() {
        HomeGridProfile eight = HomeGridProfile.fromPersisted("8x4");
        HomeGridProfile ten = HomeGridProfile.fromPersisted("10x6");

        assertEquals(8, eight.columns(false));
        assertEquals(4, eight.rows(false));
        assertEquals(4, eight.columns(true));
        assertEquals(8, eight.rows(true));

        assertEquals(10, ten.columns(false));
        assertEquals(6, ten.rows(false));
        assertEquals(6, ten.columns(true));
        assertEquals(10, ten.rows(true));
    }

    @Test
    public void invalidPersistedProfileFallsBackToEightByFour() {
        assertEquals(HomeGridProfile.GRID_8X4, HomeGridProfile.fromPersisted(null));
        assertEquals(HomeGridProfile.GRID_8X4, HomeGridProfile.fromPersisted(""));
        assertEquals(HomeGridProfile.GRID_8X4,
                HomeGridProfile.fromPersisted("not-a-grid"));
        assertEquals("8x4", HomeGridProfile.fromPersisted("bad").persistedValue());
    }

    @Test
    public void profileCountMatchingAcceptsOnlyProfileAndTranspose() {
        assertTrue(HomeGridProfile.GRID_8X4.matchesCounts(8, 4));
        assertTrue(HomeGridProfile.GRID_8X4.matchesCounts(4, 8));
        assertFalse(HomeGridProfile.GRID_8X4.matchesCounts(10, 6));

        assertTrue(HomeGridProfile.GRID_10X6.matchesCounts(10, 6));
        assertTrue(HomeGridProfile.GRID_10X6.matchesCounts(6, 10));
        assertFalse(HomeGridProfile.GRID_10X6.matchesCounts(8, 4));
    }

    @Test
    public void generatedBlockOriginsAreUniqueEvenAndInBounds() {
        assertBlockGrid(HomeGridProfile.GRID_8X4, false, 8, 4, 8);
        assertBlockGrid(HomeGridProfile.GRID_8X4, true, 4, 8, 8);
        assertBlockGrid(HomeGridProfile.GRID_10X6, false, 10, 6, 15);
        assertBlockGrid(HomeGridProfile.GRID_10X6, true, 6, 10, 15);
    }

    private static void assertBlockGrid(HomeGridProfile profile, boolean portrait,
                                        int columns, int rows, int expectedCount) {
        int[][] origins = profile.blockOrigins(portrait);
        assertEquals(expectedCount, profile.totalBlocks());
        assertEquals(expectedCount, origins.length);
        Set<String> unique = new HashSet<>();
        for (int[] origin : origins) {
            assertEquals(2, origin.length);
            assertEquals(0, origin[0] % 2);
            assertEquals(0, origin[1] % 2);
            assertTrue(origin[0] >= 0 && origin[0] + 1 < columns);
            assertTrue(origin[1] >= 0 && origin[1] + 1 < rows);
            assertTrue(unique.add(origin[0] + ":" + origin[1]));
        }
    }
}
