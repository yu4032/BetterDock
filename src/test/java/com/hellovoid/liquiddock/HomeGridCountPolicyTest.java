package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HomeGridCountPolicyTest {
    @Test public void tenBySixRewritesOnlyVerifiedEightByFourIntermediates() {
        assertEquals(10, HomeGridCountPolicy.profileRewrite(HomeGridProfile.GRID_10X6, 8));
        assertEquals(6, HomeGridCountPolicy.profileRewrite(HomeGridProfile.GRID_10X6, 4));
        assertEquals(7, HomeGridCountPolicy.profileRewrite(HomeGridProfile.GRID_10X6, 7));
    }

    @Test public void namedGridConfigOwnsOrientationCounts() {
        assertEquals(10, HomeGridCountPolicy.profileRewriteForGridName(
                HomeGridProfile.GRID_10X6, "land_grid", true, 8));
        assertEquals(6, HomeGridCountPolicy.profileRewriteForGridName(
                HomeGridProfile.GRID_10X6, "land_grid", false, 4));
        assertEquals(6, HomeGridCountPolicy.profileRewriteForGridName(
                HomeGridProfile.GRID_10X6, "vertical_grid", true, 8));
        assertEquals(10, HomeGridCountPolicy.profileRewriteForGridName(
                HomeGridProfile.GRID_10X6, "vertical_grid", false, 4));
    }
}
