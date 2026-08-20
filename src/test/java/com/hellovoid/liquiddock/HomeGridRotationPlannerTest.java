package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HomeGridRotationPlannerTest {
    @Test public void arbitraryFourByTwoLandscapeAnchorGetsValidPortraitPosition() {
        HomeGridRotationPlanner.Item widget = new HomeGridRotationPlanner.Item(
                10L, 0, 3, 2, 4, 2, 4, 2);
        HomeGridRotationPlanner.Plan plan = HomeGridRotationPlanner.plan(
                10, 6, 6, 10, Arrays.asList(widget), new HashMap<>());
        HomeGridRotationPlanner.Position p = plan.position(10L);
        assertTrue(p != null);
        assertTrue(p.x >= 0 && p.y >= 0);
        assertTrue(p.x + p.spanX <= 6);
        assertTrue(p.y + p.spanY <= 10);
        assertFalse(plan.isUnresolved(10L));
    }

    @Test public void rememberedOrientationPositionWinsAndEnablesRoundTrip() {
        HomeGridRotationPlanner.Item widget = new HomeGridRotationPlanner.Item(
                11L, 0, 1, 5, 4, 2, 4, 2);
        Map<Long, HomeGridRotationPlanner.Position> rememberedPortrait = new HashMap<>();
        rememberedPortrait.put(11L,
                new HomeGridRotationPlanner.Position(0, 2, 6, 4, 2));
        HomeGridRotationPlanner.Plan portrait = HomeGridRotationPlanner.plan(
                10, 6, 6, 10, Arrays.asList(widget), rememberedPortrait);
        assertEquals(2, portrait.position(11L).x);
        assertEquals(6, portrait.position(11L).y);

        HomeGridRotationPlanner.Item portraitItem = new HomeGridRotationPlanner.Item(
                11L, 0, 2, 6, 4, 2, 4, 2);
        Map<Long, HomeGridRotationPlanner.Position> rememberedLandscape = new HashMap<>();
        rememberedLandscape.put(11L,
                new HomeGridRotationPlanner.Position(0, 1, 4, 4, 2));
        HomeGridRotationPlanner.Plan landscape = HomeGridRotationPlanner.plan(
                6, 10, 10, 6, Arrays.asList(portraitItem), rememberedLandscape);
        assertEquals(1, landscape.position(11L).x);
        assertEquals(4, landscape.position(11L).y);
    }

    @Test public void conflictingRememberedPositionsFallBackWithoutOverlapDeterministically() {
        HomeGridRotationPlanner.Item first = new HomeGridRotationPlanner.Item(
                1L, 0, 0, 0, 1, 1, 1, 1);
        HomeGridRotationPlanner.Item second = new HomeGridRotationPlanner.Item(
                2L, 0, 1, 0, 1, 1, 1, 1);
        Map<Long, HomeGridRotationPlanner.Position> remembered = new HashMap<>();
        remembered.put(1L, new HomeGridRotationPlanner.Position(0, 2, 2, 1, 1));
        remembered.put(2L, new HomeGridRotationPlanner.Position(0, 2, 2, 1, 1));

        HomeGridRotationPlanner.Plan a = HomeGridRotationPlanner.plan(
                10, 6, 6, 10, Arrays.asList(first, second), remembered);
        HomeGridRotationPlanner.Plan b = HomeGridRotationPlanner.plan(
                10, 6, 6, 10, Arrays.asList(first, second), remembered);

        assertEquals(a.position(1L), b.position(1L));
        assertEquals(a.position(2L), b.position(2L));
        assertFalse(a.position(1L).overlaps(a.position(2L)));
        assertEquals(2, a.position(1L).x);
        assertEquals(2, a.position(1L).y);
    }

    @Test public void iconAndWidgetUseTheSameOccupancyMap() {
        HomeGridRotationPlanner.Item widget = new HomeGridRotationPlanner.Item(
                20L, 0, 0, 0, 4, 2, 4, 2);
        HomeGridRotationPlanner.Item icon = new HomeGridRotationPlanner.Item(
                21L, 0, 0, 1, 1, 1, 1, 1);
        Map<Long, HomeGridRotationPlanner.Position> remembered = new HashMap<>();
        remembered.put(20L, new HomeGridRotationPlanner.Position(0, 0, 0, 4, 2));
        remembered.put(21L, new HomeGridRotationPlanner.Position(0, 1, 1, 1, 1));

        HomeGridRotationPlanner.Plan plan = HomeGridRotationPlanner.plan(
                10, 6, 6, 10, Arrays.asList(widget, icon), remembered);
        assertFalse(plan.position(20L).overlaps(plan.position(21L)));
    }

    @Test public void screenIdsRemainLongAndNeverAliasAcrossPages() {
        long largeScreen = ((long) Integer.MAX_VALUE) + 42L;
        HomeGridRotationPlanner.Item first = new HomeGridRotationPlanner.Item(
                30L, largeScreen, 0, 0, 1, 1, 1, 1);
        HomeGridRotationPlanner.Item second = new HomeGridRotationPlanner.Item(
                31L, 0L, 0, 0, 1, 1, 1, 1);
        HomeGridRotationPlanner.Plan plan = HomeGridRotationPlanner.plan(
                10, 6, 6, 10, Arrays.asList(first, second), new HashMap<>());
        assertEquals(largeScreen, plan.position(30L).screenId);
        assertEquals(0L, plan.position(31L).screenId);
        assertFalse(plan.position(30L).overlaps(plan.position(31L)));
    }
}
