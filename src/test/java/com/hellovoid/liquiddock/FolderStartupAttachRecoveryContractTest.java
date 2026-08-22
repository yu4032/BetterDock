package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FolderStartupAttachRecoveryContractTest {
    private static String source() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java"),
                StandardCharsets.UTF_8);
    }

    @Test
    public void alreadyAttachedFolderRetriesUntilStableLauncherRootExists() throws Exception {
        String source = source();
        assertTrue("startup recovery must be bounded",
                source.contains("MAX_STARTUP_RECOVERY_FRAMES"));
        assertTrue("failed initial attach must schedule recovery for an already-attached folder",
                source.contains("if (sink == null && icon.isAttachedToWindow())")
                        && source.contains("scheduleFolderRecovery(icon, glassConfig, 0)"));
        assertTrue("recovery must run on a later UI frame rather than creating a temporary root session",
                source.contains("postOnAnimation")
                        && source.contains("scheduleFolderRecovery"));
        assertTrue("recovery must retry only while attach still fails",
                source.contains("if (sink == null && attempt < MAX_STARTUP_RECOVERY_FRAMES)"));
    }
}
