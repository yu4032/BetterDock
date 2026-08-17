package com.hellovoid.liquiddock;

import org.junit.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Regression coverage for the 10x6 profile fighting the historical 6->8 core rewrite. */
public class HomeGridCountOwnershipTest {
    private static Class<?> policyClass() {
        try {
            return Class.forName("com.hellovoid.liquiddock.HomeGridCountPolicy");
        } catch (ClassNotFoundException e) {
            fail("HomeGridCountPolicy must own profile count rewrites");
            throw new AssertionError(e);
        }
    }

    private static int coreRewrite(boolean tenBySixOwnsCounts, int value) throws Exception {
        Method method = policyClass().getDeclaredMethod(
                "coreRewrite", boolean.class, int.class);
        method.setAccessible(true);
        return (Integer) method.invoke(null, tenBySixOwnsCounts, value);
    }

    private static int profileRewrite(boolean portrait, boolean xAxis, int value) throws Exception {
        Method method = policyClass().getDeclaredMethod(
                "profileRewrite", HomeGridProfile.class, boolean.class, boolean.class, int.class);
        method.setAccessible(true);
        return (Integer) method.invoke(null, HomeGridProfile.GRID_10X6, portrait, xAxis, value);
    }

    @Test
    public void tenBySixCoreMustPreserveLiteralSixForOverlayOwnership() throws Exception {
        assertEquals(6, coreRewrite(true, 6));
        assertEquals(8, coreRewrite(false, 6));
    }

    @Test
    public void landscapeTenBySixResolvesExactlyTenColumnsBySixRows() throws Exception {
        int x = profileRewrite(false, true, coreRewrite(true, 6));
        int y = profileRewrite(false, false, coreRewrite(true, 6));
        assertEquals(10, x);
        assertEquals(6, y);
    }

    @Test
    public void portraitTenBySixResolvesExactlySixColumnsByTenRows() throws Exception {
        int x = profileRewrite(true, true, coreRewrite(true, 6));
        int y = profileRewrite(true, false, coreRewrite(true, 6));
        assertEquals(6, x);
        assertEquals(10, y);
    }

    @Test
    public void overlayRecoversStockLegacyAndPreviouslyCorruptedCounts() throws Exception {
        assertEquals(10, profileRewrite(false, true, 8));
        assertEquals(6, profileRewrite(false, false, 4));
        assertEquals(10, profileRewrite(false, true, 6));
        assertEquals(6, profileRewrite(false, false, 6));
        assertEquals(6, profileRewrite(false, false, 8));
        assertEquals(6, profileRewrite(true, true, 8));
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
