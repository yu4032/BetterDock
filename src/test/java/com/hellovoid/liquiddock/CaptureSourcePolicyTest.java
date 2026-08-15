package com.hellovoid.liquiddock;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/** HOME/All Apps are wallpaper-backed; external apps and Recents need live composited content. */
public class CaptureSourcePolicyTest {
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String sourceFor(String sceneName) throws Exception {
        Class<?> policy = Class.forName("com.hellovoid.liquiddock.CaptureSourcePolicy");
        Class<?> scene = Class.forName("com.hellovoid.liquiddock.CaptureScene");
        Method sourceFor = policy.getDeclaredMethod("sourceFor", scene);
        sourceFor.setAccessible(true);
        Object sceneValue = Enum.valueOf((Class<? extends Enum>) scene, sceneName);
        return ((Enum<?>) sourceFor.invoke(null, sceneValue)).name();
    }

    @Test public void policyExposesOnlyWallpaperAndFullDisplay() throws Exception {
        Class<?> source = Class.forName("com.hellovoid.liquiddock.CaptureSourcePolicy$Source");
        String[] names = Arrays.stream(source.getEnumConstants())
                .map(v -> ((Enum<?>) v).name()).sorted().toArray(String[]::new);
        assertEquals("[FULL_DISPLAY, WALLPAPER]", Arrays.toString(names));
    }

    @Test public void liveScenesUseFullDisplay() throws Exception {
        assertEquals("FULL_DISPLAY", sourceFor("APP"));
        assertEquals("FULL_DISPLAY", sourceFor("RECENTS"));
    }

    @Test public void wallpaperBackedLauncherScenesUseWallpaper() throws Exception {
        assertEquals("WALLPAPER", sourceFor("HOME"));
        assertEquals("WALLPAPER", sourceFor("ALL_APPS"));
    }
}
