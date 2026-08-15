package com.hellovoid.liquiddock;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/** Launcher-owned scenes stay wallpaper-backed until Recents is explicitly confirmed live. */
public class CaptureSourcePolicyTest {
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String sourceFor(String sceneName, boolean localLayerAvailable) throws Exception {
        Class<?> policy = Class.forName("com.hellovoid.liquiddock.CaptureSourcePolicy");
        Class<?> scene = Class.forName("com.hellovoid.liquiddock.CaptureScene");
        Method sourceFor = policy.getDeclaredMethod("sourceFor", scene, boolean.class);
        sourceFor.setAccessible(true);
        Object sceneValue = Enum.valueOf((Class<? extends Enum>) scene, sceneName);
        return ((Enum<?>) sourceFor.invoke(null, sceneValue, localLayerAvailable)).name();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String sourceFor(String sceneName, boolean localLayerAvailable,
                                    boolean recentsLiveConfirmed) throws Exception {
        Class<?> policy = Class.forName("com.hellovoid.liquiddock.CaptureSourcePolicy");
        Class<?> scene = Class.forName("com.hellovoid.liquiddock.CaptureScene");
        final Method sourceFor;
        try {
            sourceFor = policy.getDeclaredMethod(
                    "sourceFor", scene, boolean.class, boolean.class);
        } catch (NoSuchMethodException e) {
            fail("CaptureSourcePolicy must expose confirmed-Recents source selection");
            return null;
        }
        sourceFor.setAccessible(true);
        Object sceneValue = Enum.valueOf((Class<? extends Enum>) scene, sceneName);
        return ((Enum<?>) sourceFor.invoke(
                null, sceneValue, localLayerAvailable, recentsLiveConfirmed)).name();
    }

    @Test public void externalAppStillUsesFullDisplay() throws Exception {
        assertEquals("FULL_DISPLAY", sourceFor("APP", false));
        assertEquals("FULL_DISPLAY", sourceFor("APP", false, false));
    }

    @Test public void homeUsesWallpaper() throws Exception {
        assertEquals("WALLPAPER", sourceFor("HOME", false));
        assertEquals("WALLPAPER", sourceFor("HOME", false, true));
    }

    @Test public void unconfirmedRecentsUsesWallpaper() throws Exception {
        assertEquals("WALLPAPER", sourceFor("RECENTS", true));
        assertEquals("WALLPAPER", sourceFor("RECENTS", false));
        assertEquals("WALLPAPER", sourceFor("RECENTS", true, false));
        assertEquals("WALLPAPER", sourceFor("RECENTS", false, false));
    }

    @Test public void confirmedRecentsUsesFullDisplay() throws Exception {
        assertEquals("FULL_DISPLAY", sourceFor("RECENTS", true, true));
        assertEquals("FULL_DISPLAY", sourceFor("RECENTS", false, true));
    }

    @Test public void allAppsUsesWallpaperEvenWhenOverlayLayerExists() throws Exception {
        assertEquals("WALLPAPER", sourceFor("ALL_APPS", true));
        assertEquals("WALLPAPER", sourceFor("ALL_APPS", false));
        assertEquals("WALLPAPER", sourceFor("ALL_APPS", true, true));
    }
}
