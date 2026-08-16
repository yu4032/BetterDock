package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class RecentsExitAnimationHandoffContractTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test public void exitOverviewEventDoesNotEndLiveRecents() throws Exception {
        String source = read("src/main/java/com/hellovoid/liquiddock/MainHook.java");
        assertTrue(source.contains("hookOverviewStateEvent(cl, \"EnterOverviewStateEvent\", true);"));
        assertFalse(source.contains("hookOverviewStateEvent(cl, \"ExitOverviewStateEvent\", false);"));
    }

    @Test public void launcherExitAnimationLifecycleIsHooked() throws Exception {
        Path path = Path.of("src/main/java/com/hellovoid/liquiddock/RecentsExitAnimationHook.java");
        assertTrue(Files.exists(path));
        String source = Files.readString(path);
        assertTrue(source.contains("com.miui.home.recents.views.RecentsContainer"));
        assertTrue(source.contains("setIsExitRecentsAnimating"));
        assertTrue(source.contains("boolean.class"));
    }

    @Test public void exitAnimationHookControlsOverviewLifecycle() throws Exception {
        String haptic = read("src/main/java/com/hellovoid/liquiddock/RecentsHapticHook.java");
        String runtime = read("src/main/java/com/hellovoid/liquiddock/HomeOwnershipRuntime.java");
        assertTrue(haptic.contains("RecentsExitAnimationHook.install(classLoader"));
        assertTrue(runtime.contains("onRecentsExitAnimationChanged"));
        assertTrue(runtime.contains("glass.setOverviewActive(active"));
    }
}
