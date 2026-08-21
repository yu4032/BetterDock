package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class HomeGridDeviceConfigCountContractTest {
    @Test public void tenBySixOwnsDeviceConfigCellCounts() throws Exception {
        String entry = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/ModuleMain.java"));
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/HomeGridDeviceConfigCountHook.java"));

        assertTrue(entry.contains("HomeGridDeviceConfigCountHook.install(classLoader"));
        assertTrue(source.contains("com.miui.home.launcher.DeviceConfig"));
        assertTrue(source.contains("getCellCountX"));
        assertTrue(source.contains("getCellCountY"));
        assertTrue(source.contains("HomeGridCountPolicy.profileRewrite(profile"));
    }
}
