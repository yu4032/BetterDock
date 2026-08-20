package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class GridProfileRuntimeWiringTest {
    @Test public void moduleMainSelectsProfileAndInstallsOverlayAfterBaseGrid() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/ModuleMain.java"));
        assertTrue(source.contains("GridProfileConfig.PROFILE_KEY"));
        assertTrue(source.contains("HomeGridProfile.fromPersisted"));
        assertTrue(source.contains("new MainHook().install(classLoader);"));
        assertTrue(source.contains("HomeGridProfileOverlayHook.install"));
        assertTrue(source.indexOf("new MainHook().install(classLoader);")
                < source.indexOf("HomeGridProfileOverlayHook.install"));
    }
}
