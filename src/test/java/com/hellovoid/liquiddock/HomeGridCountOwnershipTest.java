package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Regression coverage for the 10x6 profile fighting the historical 6->8 core rewrite. */
public class HomeGridCountOwnershipTest {

    @Test
    public void tenBySixCoreMustPreserveLiteralSixForOverlayOwnership() {
        assertEquals(6, HomeGridCountPolicy.coreRewrite(true, 6));
        assertEquals(8, HomeGridCountPolicy.coreRewrite(false, 6));
    }

    @Test
    public void landscapeTenBySixResolvesExactlyTenColumnsBySixRows() {
        int x = HomeGridCountPolicy.profileRewrite(
                HomeGridProfile.GRID_10X6, false, true,
                HomeGridCountPolicy.coreRewrite(true, 6));
        int y = HomeGridCountPolicy.profileRewrite(
                HomeGridProfile.GRID_10X6, false, false,
                HomeGridCountPolicy.coreRewrite(true, 6));
        assertEquals(10, x);
        assertEquals(6, y);
    }

    @Test
    public void portraitTenBySixResolvesExactlySixColumnsByTenRows() {
        int x = HomeGridCountPolicy.profileRewrite(
                HomeGridProfile.GRID_10X6, true, true,
                HomeGridCountPolicy.coreRewrite(true, 6));
        int y = HomeGridCountPolicy.profileRewrite(
                HomeGridProfile.GRID_10X6, true, false,
                HomeGridCountPolicy.coreRewrite(true, 6));
        assertEquals(6, x);
        assertEquals(10, y);
    }

    @Test
    public void overlayCanRecoverBothHistoricalAndStockWorkspaceCounts() {
        assertEquals(10, HomeGridCountPolicy.profileRewrite(
                HomeGridProfile.GRID_10X6, false, true, 8));
        assertEquals(6, HomeGridCountPolicy.profileRewrite(
                HomeGridProfile.GRID_10X6, false, false, 4));
        assertEquals(10, HomeGridCountPolicy.profileRewrite(
                HomeGridProfile.GRID_10X6, false, true, 6));
        assertEquals(6, HomeGridCountPolicy.profileRewrite(
                HomeGridProfile.GRID_10X6, false, false, 6));
    }

    @Test
    public void productionHooksShareTheCountOwnershipPolicy() throws Exception {
        String core = Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/HomeGridHook.java"),
                StandardCharsets.UTF_8);
        String overlay = Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/HomeGridProfileOverlayHook.java"),
                StandardCharsets.UTF_8);
        assertTrue(core.contains("HomeGridCountPolicy.coreRewrite"));
        assertTrue(core.contains("HomeGridProfileOverlayHook.ownsTenBySixCounts()"));
        assertTrue(overlay.contains("HomeGridCountPolicy.profileRewrite"));
    }
}
