package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Pure count-ownership rules; no Android Configuration timing is allowed here. */
public class HomeGridCountPolicyTest {

    @Test
    public void tenBySixOnlyRewritesVerifiedEightByFourIntermediates() {
        assertEquals(10, HomeGridCountPolicy.profileRewrite(HomeGridProfile.GRID_10X6, 8));
        assertEquals(6, HomeGridCountPolicy.profileRewrite(HomeGridProfile.GRID_10X6, 4));
        assertEquals(10, HomeGridCountPolicy.profileRewrite(HomeGridProfile.GRID_10X6, 10));
        assertEquals(6, HomeGridCountPolicy.profileRewrite(HomeGridProfile.GRID_10X6, 6));
        assertEquals(5, HomeGridCountPolicy.profileRewrite(HomeGridProfile.GRID_10X6, 5));
        assertEquals(7, HomeGridCountPolicy.profileRewrite(HomeGridProfile.GRID_10X6, 7));
        assertEquals(12, HomeGridCountPolicy.profileRewrite(HomeGridProfile.GRID_10X6, 12));
    }

    @Test
    public void eightByFourProfileNeverRewritesCounts() {
        for (int value : new int[]{4, 6, 8, 10, 12}) {
            assertEquals(value,
                    HomeGridCountPolicy.profileRewrite(HomeGridProfile.GRID_8X4, value));
        }
    }

    @Test
    public void namedLandscapeGridOwnsTenBySixAxes() {
        assertEquals(10, HomeGridCountPolicy.profileRewriteForGridName(
                HomeGridProfile.GRID_10X6, "land_grid", true, 8));
        assertEquals(6, HomeGridCountPolicy.profileRewriteForGridName(
                HomeGridProfile.GRID_10X6, "land_grid", false, 4));
        // Named ownership is authoritative even if a transient input is ambiguous.
        assertEquals(10, HomeGridCountPolicy.profileRewriteForGridName(
                HomeGridProfile.GRID_10X6, "landscape_workspace", true, 6));
        assertEquals(6, HomeGridCountPolicy.profileRewriteForGridName(
                HomeGridProfile.GRID_10X6, "landscape_workspace", false, 8));
    }

    @Test
    public void namedPortraitGridOwnsSixByTenAxes() {
        assertEquals(6, HomeGridCountPolicy.profileRewriteForGridName(
                HomeGridProfile.GRID_10X6, "vertical_grid", true, 8));
        assertEquals(10, HomeGridCountPolicy.profileRewriteForGridName(
                HomeGridProfile.GRID_10X6, "vertical_grid", false, 6));
        assertEquals(6, HomeGridCountPolicy.profileRewriteForGridName(
                HomeGridProfile.GRID_10X6, "portrait_workspace", true, 10));
        assertEquals(10, HomeGridCountPolicy.profileRewriteForGridName(
                HomeGridProfile.GRID_10X6, "portrait_workspace", false, 4));
    }

    @Test
    public void unknownGridNameFallsBackConservatively() {
        assertEquals(10, HomeGridCountPolicy.profileRewriteForGridName(
                HomeGridProfile.GRID_10X6, null, true, 8));
        assertEquals(6, HomeGridCountPolicy.profileRewriteForGridName(
                HomeGridProfile.GRID_10X6, "other_grid", false, 4));
        assertEquals(7, HomeGridCountPolicy.profileRewriteForGridName(
                HomeGridProfile.GRID_10X6, "other_grid", true, 7));
    }
}
