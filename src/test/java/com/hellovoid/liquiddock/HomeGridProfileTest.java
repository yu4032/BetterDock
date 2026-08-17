package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.ConfigMigration;

import org.junit.Test;

import java.lang.reflect.Method;

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
        Object stock = profile("stock");
        Object eight = profile("8x4");
        Object ten = profile("10x6");

        assertFalse((Boolean) call(stock, "isCustom", new Class<?>[0]));
        assertEquals(6, call(stock, "columns", new Class<?>[]{boolean.class}, false));
        assertEquals(4, call(stock, "rows", new Class<?>[]{boolean.class}, false));
        assertEquals(4, call(stock, "columns", new Class<?>[]{boolean.class}, true));
        assertEquals(6, call(stock, "rows", new Class<?>[]{boolean.class}, true));

        assertTrue((Boolean) call(eight, "isCustom", new Class<?>[0]));
        assertEquals(8, call(eight, "columns", new Class<?>[]{boolean.class}, false));
        assertEquals(4, call(eight, "rows", new Class<?>[]{boolean.class}, false));
        assertEquals(4, call(eight, "columns", new Class<?>[]{boolean.class}, true));
        assertEquals(8, call(eight, "rows", new Class<?>[]{boolean.class}, true));

        assertTrue((Boolean) call(ten, "isCustom", new Class<?>[0]));
        assertEquals(10, call(ten, "columns", new Class<?>[]{boolean.class}, false));
        assertEquals(6, call(ten, "rows", new Class<?>[]{boolean.class}, false));
        assertEquals(6, call(ten, "columns", new Class<?>[]{boolean.class}, true));
        assertEquals(10, call(ten, "rows", new Class<?>[]{boolean.class}, true));
    }

    @Test
    public void invalidPersistedProfileFallsBackToStock() throws Exception {
        Object profile = profile("definitely-not-a-grid");
        assertFalse((Boolean) call(profile, "isCustom", new Class<?>[0]));
        assertEquals("stock", call(profile, "persistedValue", new Class<?>[0]));
    }

    @Test
    public void legacyEightByFourMigratesOnlyWhenCanonicalProfileIsMissing() throws Exception {
        Method method;
        try {
            method = ConfigMigration.class.getDeclaredMethod(
                    "migrateGridProfile", android.content.SharedPreferences.class);
        } catch (NoSuchMethodException e) {
            fail("ConfigMigration.migrateGridProfile must exist");
            return;
        }
        method.setAccessible(true);

        TestSharedPreferences legacyOn = new TestSharedPreferences();
        legacyOn.edit().putBoolean("home_grid_8x4", true).commit();
        method.invoke(null, legacyOn);
        assertEquals("8x4", legacyOn.getString("grid_profile", null));

        TestSharedPreferences legacyOff = new TestSharedPreferences();
        legacyOff.edit().putBoolean("home_grid_8x4", false).commit();
        method.invoke(null, legacyOff);
        assertEquals("stock", legacyOff.getString("grid_profile", null));

        TestSharedPreferences absent = new TestSharedPreferences();
        method.invoke(null, absent);
        assertEquals("stock", absent.getString("grid_profile", null));

        TestSharedPreferences canonical = new TestSharedPreferences();
        canonical.edit().putString("grid_profile", "10x6")
                .putBoolean("home_grid_8x4", true).commit();
        method.invoke(null, canonical);
        assertEquals("10x6", canonical.getString("grid_profile", null));
    }
}
