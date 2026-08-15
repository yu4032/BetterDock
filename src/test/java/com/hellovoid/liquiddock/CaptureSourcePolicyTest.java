package com.hellovoid.liquiddock;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

/** Launcher-owned scenes must use the stock wallpaper backdrop rather than capture a local root. */
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

    @Test public void externalAppStillUsesFullDisplay() throws Exception {
        assertEquals("FULL_DISPLAY", sourceFor("APP", false));
    }

    @Test public void homeUsesWallpaper() throws Exception {
        assertEquals("WALLPAPER", sourceFor("HOME", false));
    }

    @Test public void recentsUsesWallpaperEvenWhenLocalLauncherLayerExists() throws Exception {
        assertEquals("WALLPAPER", sourceFor("RECENTS", true));
        assertEquals("WALLPAPER", sourceFor("RECENTS", false));
    }

    @Test public void allAppsUsesWallpaperEvenWhenOverlayLayerExists() throws Exception {
        assertEquals("WALLPAPER", sourceFor("ALL_APPS", true));
        assertEquals("WALLPAPER", sourceFor("ALL_APPS", false));
    }
}
