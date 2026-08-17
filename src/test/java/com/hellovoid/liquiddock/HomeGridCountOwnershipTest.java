package com.hellovoid.liquiddock;

import org.junit.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Regression coverage for the 10x6 overlay recovering the historical core's 6->8 rewrite. */
public class HomeGridCountOwnershipTest {
    private static Class<?> policyClass() {
        try {
            return Class.forName("com.hellovoid.liquiddock.HomeGridCountPolicy");
        } catch (ClassNotFoundException e) {
            fail("HomeGridCountPolicy must own profile count rewrites");
            throw new AssertionError(e);
        }
    }

    private static int profileRewrite(boolean portrait, boolean xAxis, int value) throws Exception {
        Method method = policyClass().getDeclaredMethod(
                "profileRewrite", HomeGridProfile.class, boolean.class, boolean.class, int.class);
        method.setAccessible(true);
        return (Integer) method.invoke(null, HomeGridProfile.GRID_10X6, portrait, xAxis, value);
    }

    @Test
    public void landscapeCoreEightResolvesExactlyTenColumnsBySixRows() throws Exception {
        // Stable HomeGridHook rewrites stock 6 to 8 before the lowest-priority overlay setter.
        assertEquals(10, profileRewrite(false, true, 8));
        assertEquals(6, profileRewrite(false, false, 8));
    }

    @Test
    public void portraitCoreEightResolvesExactlySixColumnsByTenRows() throws Exception {
        assertEquals(6, profileRewrite(true, true, 8));
        assertEquals(10, profileRewrite(true, false, 8));
    }

    @Test
    public void overlayAlsoAcceptsStockAndLegacyAxisCounts() throws Exception {
        assertEquals(10, profileRewrite(false, true, 6));
        assertEquals(6, profileRewrite(false, false, 6));
        assertEquals(10, profileRewrite(false, true, 8));
        assertEquals(6, profileRewrite(false, false, 4));
        assertEquals(6, profileRewrite(true, true, 4));
        assertEquals(10, profileRewrite(true, false, 8));
    }

    @Test
    public void unrelatedGridCountsRemainUntouched() throws Exception {
        assertEquals(5, profileRewrite(false, true, 5));
        assertEquals(7, profileRewrite(false, false, 7));
        assertEquals(12, profileRewrite(true, false, 12));
    }

    @Test
    public void productionOverlayUsesSharedCountRecoveryPolicy() throws Exception {
        String core = Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/HomeGridHook.java"),
                StandardCharsets.UTF_8);
        String overlay = Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/HomeGridProfileOverlayHook.java"),
                StandardCharsets.UTF_8);
        // Preserve the device-verified 8x4 core instead of rewriting its count ownership.
        assertTrue(core.contains("if ((Integer) args[0] == 6) args[0] = 8;"));
        assertTrue(core.contains("if ((Integer) result == 6) result = 8;"));
        assertTrue(overlay.contains("HomeGridCountPolicy.profileRewrite"));
    }
}
