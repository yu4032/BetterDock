package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class AppHomeAnimationHandoffContractTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test public void closeToHomeLifecycleHasDedicatedLauncherHook() throws Exception {
        Path path = Path.of("src/main/java/com/hellovoid/liquiddock/AppHomeAnimationHook.java");
        assertTrue(Files.exists(path));
        String source = Files.readString(path);
        assertTrue(source.contains("com.miui.home.recents.GestureModeApp$8"));
        assertTrue(source.contains("getDeclaredConstructors"));
        assertTrue(source.contains("onAppHomeAnimationStart"));
        assertTrue(source.contains("onAnimationEnd"));
        assertTrue(source.contains("onAppHomeAnimationEnd"));
    }

    @Test public void appHomeHookIsInstalledBesideRecentsLifecycle() throws Exception {
        String haptic = read("src/main/java/com/hellovoid/liquiddock/RecentsHapticHook.java");
        assertTrue(haptic.contains("RecentsExitAnimationHook.install(classLoader"));
        assertTrue(haptic.contains("AppHomeAnimationHook.install(classLoader"));
    }

    @Test public void animationLifecycleRoutesToCurrentGlassWithoutChangingOwnershipAuthority() throws Exception {
        String runtime = read("src/main/java/com/hellovoid/liquiddock/HomeOwnershipRuntime.java");
        assertTrue(runtime.contains("onAppHomeAnimationStart"));
        assertTrue(runtime.contains("glass.setGestureCaptureTarget(\"APP_HOME_ANIMATION_START\")"));
        assertTrue(runtime.contains("onAppHomeAnimationEnd"));
        assertTrue(runtime.contains("glass.setGestureCaptureTarget(\"HOME_ANIMATION_END\")"));
    }
}
