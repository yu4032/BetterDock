package com.hellovoid.liquiddock;

import org.junit.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Regression coverage for stable 10x6/6x10 count ownership during rotation. */
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

    private static int rewriteNamed(String name, boolean xAxis, int value) throws Exception {
        Method method = policyClass().getDeclaredMethod(
                "profileRewriteForGridName", HomeGridProfile.class,
                String.class, boolean.class, int.class);
        method.setAccessible(true);
        return (Integer) method.invoke(null, HomeGridProfile.GRID_10X6, name, xAxis, value);
    }

    @Test
    public void landscapeEightByFourSurvivesStalePortraitConfiguration() throws Exception {
        assertEquals(10, profileRewrite(true, true, 8));
        assertEquals(6, profileRewrite(true, false, 4));
    }

    @Test
    public void portraitFourByEightSurvivesStaleLandscapeConfiguration() throws Exception {
        assertEquals(6, profileRewrite(false, true, 4));
        assertEquals(10, profileRewrite(false, false, 8));
    }

    @Test
    public void namedGridConfigIsTheFinalAxisOwner() throws Exception {
        // Even ambiguous/transient inputs must not create the observed 6x4 hybrid.
        assertEquals(10, rewriteNamed("land_grid", true, 6));
        assertEquals(6, rewriteNamed("land_grid", false, 4));
        assertEquals(6, rewriteNamed("vertical_grid", true, 8));
        assertEquals(10, rewriteNamed("vertical_grid", false, 6));
    }

    @Test
    public void alreadyResolvedProfileCountsRemainStableAcrossConfigurationChanges() throws Exception {
        assertEquals(10, profileRewrite(true, true, 10));
        assertEquals(6, profileRewrite(true, false, 6));
        assertEquals(6, profileRewrite(false, true, 6));
        assertEquals(10, profileRewrite(false, false, 10));
    }

    @Test
    public void unrelatedGridCountsRemainUntouchedWithoutNamedWorkspaceOwner() throws Exception {
        assertEquals(5, profileRewrite(false, true, 5));
        assertEquals(7, profileRewrite(false, false, 7));
        assertEquals(12, profileRewrite(true, false, 12));
    }

    @Test
    public void productionOverlayUsesGridNameInsteadOfGlobalOrientation() throws Exception {
        String core = Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/HomeGridHook.java"),
                StandardCharsets.UTF_8);
        String overlay = Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/HomeGridProfileOverlayHook.java"),
                StandardCharsets.UTF_8);
        assertTrue(core.contains("if ((Integer) args[0] == 6) args[0] = 8;"));
        assertTrue(core.contains("if ((Integer) result == 6) result = 8;"));
        assertTrue(overlay.contains("HomeGridCountPolicy.profileRewriteForGridName"));
        assertTrue(overlay.contains("gridName(chain.getThisObject())"));
        assertFalse(overlay.contains("Resources.getSystem().getConfiguration().orientation"));
        assertFalse(overlay.contains("context.getResources().getConfiguration().orientation"));
    }
}
