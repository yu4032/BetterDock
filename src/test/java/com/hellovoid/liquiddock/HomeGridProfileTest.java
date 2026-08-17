package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.ConfigMigration;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HomeGridProfileTest {
    private static Class<?> profileClass() {
        try {
            return Class.forName("com.hellovoid.liquiddock.HomeGridProfile");
        } catch (ClassNotFoundException e) {
            fail("HomeGridProfile must exist");
            throw new AssertionError(e);
        }
    }

    private static Object profile(String persisted) throws Exception {
        Method method = profileClass().getDeclaredMethod("fromPersisted", String.class);
        method.setAccessible(true);
        return method.invoke(null, persisted);
    }

    private static Object call(Object profile, String method, Class<?>[] types, Object... args)
            throws Exception {
        Method m = profileClass().getDeclaredMethod(method, types);
        m.setAccessible(true);
        return m.invoke(profile, args);
    }

    @Test
    public void profilesExposeExactOrientationCounts() throws Exception {
        Object eight = profile("8x4");
        Object ten = profile("10x6");

        assertEquals(8, call(eight, "columns", new Class<?>[]{boolean.class}, false));
        assertEquals(4, call(eight, "rows", new Class<?>[]{boolean.class}, false));
        assertEquals(4, call(eight, "columns", new Class<?>[]{boolean.class}, true));
        assertEquals(8, call(eight, "rows", new Class<?>[]{boolean.class}, true));

        assertEquals(10, call(ten, "columns", new Class<?>[]{boolean.class}, false));
        assertEquals(6, call(ten, "rows", new Class<?>[]{boolean.class}, false));
        assertEquals(6, call(ten, "columns", new Class<?>[]{boolean.class}, true));
        assertEquals(10, call(ten, "rows", new Class<?>[]{boolean.class}, true));
    }

    @Test
    public void invalidPersistedProfileFallsBackToEightByFour() throws Exception {
        Object profile = profile("definitely-not-a-grid");
        assertEquals("8x4", call(profile, "persistedValue", new Class<?>[0]));
    }

    @Test
    public void generatedTwoByTwoBlocksCoverEightByFourWithoutDuplicates() throws Exception {
        assertBlockGrid(profile("8x4"), false, 8, 4, 8);
        assertBlockGrid(profile("8x4"), true, 4, 8, 8);
    }

    @Test
    public void generatedTwoByTwoBlocksCoverTenBySixWithoutDuplicates() throws Exception {
        assertBlockGrid(profile("10x6"), false, 10, 6, 15);
        assertBlockGrid(profile("10x6"), true, 6, 10, 15);
    }

    @Test
    public void countMatchingAcceptsOnlySelectedProfileAndTranspose() throws Exception {
        Object eight = profile("8x4");
        Object ten = profile("10x6");
        assertTrue((Boolean) call(eight, "matchesCounts",
                new Class<?>[]{int.class, int.class}, 8, 4));
        assertTrue((Boolean) call(eight, "matchesCounts",
                new Class<?>[]{int.class, int.class}, 4, 8));
        assertFalse((Boolean) call(eight, "matchesCounts",
                new Class<?>[]{int.class, int.class}, 10, 6));
        assertTrue((Boolean) call(ten, "matchesCounts",
                new Class<?>[]{int.class, int.class}, 10, 6));
        assertTrue((Boolean) call(ten, "matchesCounts",
                new Class<?>[]{int.class, int.class}, 6, 10));
        assertFalse((Boolean) call(ten, "matchesCounts",
                new Class<?>[]{int.class, int.class}, 8, 4));
    }

    private static void assertBlockGrid(Object profile, boolean portrait,
                                        int columns, int rows, int expectedCount) throws Exception {
        int[][] origins = (int[][]) call(profile, "blockOrigins",
                new Class<?>[]{boolean.class}, portrait);
        assertEquals(expectedCount, origins.length);
        assertEquals(expectedCount, call(profile, "totalBlocks", new Class<?>[0]));
        Set<String> unique = new HashSet<>();
        for (int[] origin : origins) {
            assertEquals(2, origin.length);
            assertTrue(origin[0] >= 0 && origin[0] + 1 < columns);
            assertTrue(origin[1] >= 0 && origin[1] + 1 < rows);
            assertEquals(0, origin[0] % 2);
            assertEquals(0, origin[1] % 2);
            assertTrue(unique.add(origin[0] + ":" + origin[1]));
        }
    }

    @Test
    public void legacyEightByFourMigratesToMasterSwitchAndProfile() throws Exception {
        Method method;
        try {
            method = ConfigMigration.class.getDeclaredMethod(
                    "migrateGridProfile", android.content.SharedPreferences.class);
        } catch (NoSuchMethodException e) {
            fail("ConfigMigration.migrateGridProfile must exist");
            return;
        }
        method.setAccessible(true);

        TestSharedPreferences legacyOn = new TestSharedPreferences(Collections.emptyMap());
        legacyOn.edit().putBoolean("home_grid_8x4", true).commit();
        method.invoke(null, legacyOn);
        assertTrue(legacyOn.getBoolean("home_grid_extended", false));
        assertEquals("8x4", legacyOn.getString("grid_profile", null));

        TestSharedPreferences legacyOff = new TestSharedPreferences(Collections.emptyMap());
        legacyOff.edit().putBoolean("home_grid_8x4", false).commit();
        method.invoke(null, legacyOff);
        assertFalse(legacyOff.getBoolean("home_grid_extended", true));
        assertEquals("8x4", legacyOff.getString("grid_profile", null));

        TestSharedPreferences absent = new TestSharedPreferences(Collections.emptyMap());
        method.invoke(null, absent);
        assertFalse(absent.getBoolean("home_grid_extended", true));
        assertEquals("8x4", absent.getString("grid_profile", null));
    }

    @Test
    public void canonicalMasterAndProfileAreNeverOverwrittenByLegacyState() throws Exception {
        Method method = ConfigMigration.class.getDeclaredMethod(
                "migrateGridProfile", android.content.SharedPreferences.class);
        method.setAccessible(true);

        TestSharedPreferences canonical = new TestSharedPreferences(Collections.emptyMap());
        canonical.edit()
                .putBoolean("home_grid_extended", false)
                .putString("grid_profile", "10x6")
                .putBoolean("home_grid_8x4", true)
                .commit();
        method.invoke(null, canonical);

        assertFalse(canonical.getBoolean("home_grid_extended", true));
        assertEquals("10x6", canonical.getString("grid_profile", null));
    }
}
