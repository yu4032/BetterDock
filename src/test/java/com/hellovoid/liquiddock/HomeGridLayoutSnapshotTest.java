package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HomeGridLayoutSnapshotTest {

    @Test
    public void orientationOtherIsSymmetric() {
        assertEquals(HomeGridOrientation.PORTRAIT, HomeGridOrientation.LANDSCAPE.other());
        assertEquals(HomeGridOrientation.LANDSCAPE, HomeGridOrientation.PORTRAIT.other());
    }

    @Test
    public void itemBoundsAndOverlapUseWholeSpanRectangle() {
        HomeGridItemPosition widget = pos(1, 0, 4, 1, 4, 2);
        HomeGridItemPosition touching = pos(2, 0, 0, 3, 4, 2);
        HomeGridItemPosition overlapping = pos(3, 0, 7, 2, 1, 1);

        assertTrue(widget.fitsWithin(10, 6));
        assertFalse(widget.fitsWithin(6, 10));
        assertFalse(widget.overlaps(touching));
        assertTrue(widget.overlaps(overlapping));
    }

    @Test
    public void validMixedLayoutCreatesSnapshot() {
        HomeGridLayoutSnapshot snapshot = HomeGridLayoutSnapshot.create(
                HomeGridProfile.GRID_10X6,
                HomeGridOrientation.LANDSCAPE,
                Arrays.asList(
                        pos(10, 100, 0, 0, 4, 2),
                        pos(11, 100, 4, 0, 2, 2),
                        pos(12, 100, 9, 5, 1, 1)));

        assertNotNull(snapshot);
        assertEquals(3, snapshot.size());
        assertEquals(4, snapshot.get(10).spanX());
        assertEquals(5, snapshot.get(12).cellY());
    }

    @Test
    public void rejectsOutOfBoundsPlacement() {
        assertNull(HomeGridLayoutSnapshot.create(
                HomeGridProfile.GRID_8X4,
                HomeGridOrientation.LANDSCAPE,
                Arrays.asList(pos(1, 0, 6, 3, 2, 2))));
    }

    @Test
    public void rejectsOverlapOnSameScreenButAllowsSameCellsOnDifferentScreens() {
        assertNull(HomeGridLayoutSnapshot.create(
                HomeGridProfile.GRID_8X4,
                HomeGridOrientation.LANDSCAPE,
                Arrays.asList(
                        pos(1, 1, 0, 0, 4, 2),
                        pos(2, 1, 3, 1, 1, 1))));

        assertNotNull(HomeGridLayoutSnapshot.create(
                HomeGridProfile.GRID_8X4,
                HomeGridOrientation.LANDSCAPE,
                Arrays.asList(
                        pos(1, 1, 0, 0, 4, 2),
                        pos(2, 2, 0, 0, 4, 2))));
    }

    @Test
    public void rejectsDuplicateStableIds() {
        assertNull(HomeGridLayoutSnapshot.create(
                HomeGridProfile.GRID_8X4,
                HomeGridOrientation.PORTRAIT,
                Arrays.asList(
                        pos(7, 1, 0, 0, 1, 1),
                        pos(7, 2, 1, 1, 1, 1))));
    }

    private static HomeGridItemPosition pos(long id, long screenId,
                                            int x, int y, int spanX, int spanY) {
        return new HomeGridItemPosition(id, screenId, x, y, spanX, spanY);
    }
}
