package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HomeGridPlacementPlannerTest {

    @Test
    public void completeRememberedTargetLayoutWinsOverRepacking() {
        HomeGridItemPosition sourceWidget = pos(1, 10, 2, 1, 4, 2);
        HomeGridItemPosition sourceIcon = pos(2, 10, 7, 3, 1, 1);
        HomeGridLayoutSnapshot remembered = HomeGridLayoutSnapshot.create(
                HomeGridProfile.GRID_8X4,
                HomeGridOrientation.PORTRAIT,
                Arrays.asList(
                        pos(1, 10, 0, 5, 4, 2),
                        pos(2, 10, 3, 0, 1, 1)));

        HomeGridPlacementPlanner.PlanResult result = HomeGridPlacementPlanner.plan(
                HomeGridProfile.GRID_8X4,
                HomeGridOrientation.PORTRAIT,
                Arrays.asList(sourceWidget, sourceIcon),
                remembered);

        assertTrue(result.success());
        assertNotNull(result.snapshot());
        assertEquals(0, result.snapshot().get(1).cellX());
        assertEquals(5, result.snapshot().get(1).cellY());
        assertEquals(3, result.snapshot().get(2).cellX());
        assertEquals(0, result.snapshot().get(2).cellY());
    }

    @Test
    public void largeItemClaimsItsNearestRegionBeforeOneByOneIcon() {
        HomeGridItemPosition large = pos(100, 7, 2, 1, 4, 2);
        HomeGridItemPosition icon = pos(101, 7, 2, 2, 1, 1);

        HomeGridPlacementPlanner.PlanResult result = HomeGridPlacementPlanner.plan(
                HomeGridProfile.GRID_8X4,
                HomeGridOrientation.PORTRAIT,
                Arrays.asList(icon, large),
                null);

        assertTrue(result.success());
        HomeGridItemPosition placedLarge = result.snapshot().get(100);
        HomeGridItemPosition placedIcon = result.snapshot().get(101);
        assertEquals(0, placedLarge.cellX());
        assertEquals(3, placedLarge.cellY());
        assertFalse(placedLarge.overlaps(placedIcon));
    }

    @Test
    public void nearestNormalizedCenterUsesRowMajorTieBreak() {
        HomeGridPlacementPlanner.PlanResult result = HomeGridPlacementPlanner.plan(
                HomeGridProfile.GRID_8X4,
                HomeGridOrientation.PORTRAIT,
                Collections.singletonList(pos(9, 3, 7, 3, 1, 1)),
                null);

        assertTrue(result.success());
        assertEquals(3, result.snapshot().get(9).cellX());
        assertEquals(6, result.snapshot().get(9).cellY());
        assertEquals(3, result.snapshot().get(9).screenId());
    }

    @Test
    public void impossibleSpanFailsWithoutPartialSnapshot() {
        HomeGridPlacementPlanner.PlanResult result = HomeGridPlacementPlanner.plan(
                HomeGridProfile.GRID_8X4,
                HomeGridOrientation.PORTRAIT,
                Arrays.asList(
                        pos(1, 0, 0, 0, 1, 1),
                        pos(2, 0, 0, 0, 5, 1)),
                null);

        assertFalse(result.success());
        assertNull(result.snapshot());
    }

    @Test
    public void staleRememberedSnapshotPreservesCompatibleItemsAndPlansNewOnes() {
        HomeGridLayoutSnapshot remembered = HomeGridLayoutSnapshot.create(
                HomeGridProfile.GRID_8X4,
                HomeGridOrientation.PORTRAIT,
                Collections.singletonList(pos(1, 4, 3, 7, 1, 1)));

        HomeGridPlacementPlanner.PlanResult result = HomeGridPlacementPlanner.plan(
                HomeGridProfile.GRID_8X4,
                HomeGridOrientation.PORTRAIT,
                Arrays.asList(
                        pos(1, 4, 7, 3, 1, 1),
                        pos(2, 4, 0, 0, 1, 1)),
                remembered);

        assertTrue(result.success());
        assertEquals(3, result.snapshot().get(1).cellX());
        assertEquals(7, result.snapshot().get(1).cellY());
        assertNotNull(result.snapshot().get(2));
        assertFalse(result.snapshot().get(1).overlaps(result.snapshot().get(2)));
    }

    private static HomeGridItemPosition pos(long id, long screenId,
                                            int x, int y, int spanX, int spanY) {
        return new HomeGridItemPosition(id, screenId, x, y, spanX, spanY);
    }
}
