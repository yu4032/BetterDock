package com.hellovoid.liquiddock;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/** Covers API101 compatibility plus confirmed Recents/freeform/workstation source selection. */
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String sourceFor(String sceneName, boolean localLayerAvailable,
                                    boolean recentsLiveConfirmed) throws Exception {
        Class<?> policy = Class.forName("com.hellovoid.liquiddock.CaptureSourcePolicy");
        Class<?> scene = Class.forName("com.hellovoid.liquiddock.CaptureScene");
        final Method method;
        try {
            method = policy.getDeclaredMethod("sourceFor", scene, boolean.class, boolean.class);
        } catch (NoSuchMethodException e) {
            fail("CaptureSourcePolicy must expose confirmed-Recents source selection");
            return null;
        }
        method.setAccessible(true);
        Object sceneValue = Enum.valueOf((Class<? extends Enum>) scene, sceneName);
        return ((Enum<?>) method.invoke(null, sceneValue, localLayerAvailable,
                recentsLiveConfirmed)).name();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String workstationSourceFor(String sceneName) throws Exception {
        Class<?> policy = Class.forName("com.hellovoid.liquiddock.CaptureSourcePolicy");
        Class<?> scene = Class.forName("com.hellovoid.liquiddock.CaptureScene");
        Method method = policy.getDeclaredMethod("sourceForWorkstationScene", scene, boolean.class);
        method.setAccessible(true);
        Object sceneValue = Enum.valueOf((Class<? extends Enum>) scene, sceneName);
        return ((Enum<?>) method.invoke(null, sceneValue, false)).name();
    }

    @Test public void policyExposesOnlyWallpaperAndFullDisplay() throws Exception {
        Class<?> source = Class.forName("com.hellovoid.liquiddock.CaptureSourcePolicy$Source");
        String[] names = Arrays.stream(source.getEnumConstants())
                .map(v -> ((Enum<?>) v).name()).sorted().toArray(String[]::new);
        assertEquals("[FULL_DISPLAY, WALLPAPER]", Arrays.toString(names));
    }

    @Test public void api101CompatibilityKeepsAuthoritativeRecentsLive() throws Exception {
        assertEquals("FULL_DISPLAY", sourceFor("APP"));
        assertEquals("FULL_DISPLAY", sourceFor("RECENTS"));
        assertEquals("WALLPAPER", sourceFor("HOME"));
        assertEquals("WALLPAPER", sourceFor("ALL_APPS"));
    }

    @Test public void speculativeRecentsStaysWallpaperUntilConfirmed() throws Exception {
        assertEquals("WALLPAPER", sourceFor("RECENTS", false, false));
        assertEquals("FULL_DISPLAY", sourceFor("RECENTS", false, true));
    }

    @Test public void workstationLiveScenesUseSafeComposedDisplay() throws Exception {
        assertEquals("FULL_DISPLAY", workstationSourceFor("RECENTS"));
        assertEquals("FULL_DISPLAY", workstationSourceFor("ALL_APPS"));
        assertEquals("WALLPAPER", workstationSourceFor("HOME"));
        assertEquals("WALLPAPER", workstationSourceFor("APP"));
    }
}
