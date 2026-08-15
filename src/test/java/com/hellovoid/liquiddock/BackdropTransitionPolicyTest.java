package com.hellovoid.liquiddock;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BackdropTransitionPolicyTest {
    private static boolean shouldDrop(String installed, String target) throws Exception {
        Class<?> policy = Class.forName("com.hellovoid.liquiddock.BackdropTransitionPolicy");
        Method method = policy.getDeclaredMethod(
                "shouldDropInstalled", CaptureScene.class, CaptureScene.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null,
                CaptureScene.valueOf(installed), CaptureScene.valueOf(target));
    }

    private static boolean shouldRevealNativeFallback(String target) throws Exception {
        Class<?> policy = Class.forName("com.hellovoid.liquiddock.BackdropTransitionPolicy");
        Method method = policy.getDeclaredMethod(
                "shouldRevealNativeFallback", CaptureScene.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, CaptureScene.valueOf(target));
    }

    @Test public void wallpaperToLiveTransitionsDropStaleBackdrop() throws Exception {
        assertTrue(shouldDrop("HOME", "APP"));
        assertTrue(shouldDrop("HOME", "RECENTS"));
        assertTrue(shouldDrop("ALL_APPS", "APP"));
        assertTrue(shouldDrop("ALL_APPS", "RECENTS"));
    }

    @Test public void liveToWallpaperTransitionsDropStaleBackdrop() throws Exception {
        assertTrue(shouldDrop("APP", "HOME"));
        assertTrue(shouldDrop("APP", "ALL_APPS"));
        assertTrue(shouldDrop("RECENTS", "HOME"));
        assertTrue(shouldDrop("RECENTS", "ALL_APPS"));
    }

    @Test public void transitionsWithinSameCaptureDomainDoNotDrop() throws Exception {
        assertFalse(shouldDrop("HOME", "ALL_APPS"));
        assertFalse(shouldDrop("ALL_APPS", "HOME"));
        assertFalse(shouldDrop("APP", "RECENTS"));
        assertFalse(shouldDrop("RECENTS", "APP"));
        assertFalse(shouldDrop("APP", "APP"));
        assertFalse(shouldDrop("RECENTS", "RECENTS"));
    }

    @Test public void liveDomainNeverRevealsNativeWallpaperFallbackWhileWaiting() throws Exception {
        assertFalse(shouldRevealNativeFallback("APP"));
        assertFalse(shouldRevealNativeFallback("RECENTS"));
    }

    @Test public void wallpaperDomainMayUseNativeFallbackWhileWaiting() throws Exception {
        assertTrue(shouldRevealNativeFallback("HOME"));
        assertTrue(shouldRevealNativeFallback("ALL_APPS"));
    }
}
