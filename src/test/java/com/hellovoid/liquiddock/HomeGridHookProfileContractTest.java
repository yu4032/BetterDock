package com.hellovoid.liquiddock;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Source-level regression contract for profile-driven Workspace grid hooks. */
public class HomeGridHookProfileContractTest {
    private static String source() throws IOException {
        Path path = Paths.get("src/main/java/com/hellovoid/liquiddock/HomeGridHook.java");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    public void hookOwnsAnExtendedMasterAndSelectedProfile() throws IOException {
        String source = source();
        assertTrue(source.contains("boolean enableExtendedGrid"));
        assertTrue(source.contains("HomeGridProfile profile"));
        assertTrue(source.contains("extendedGridEnabled"));
        assertFalse(source.contains("private static boolean grid8x4Enabled"));
    }

    @Test
    public void axisCountsComeFromTheSelectedProfile() throws IOException {
        String source = source();
        assertTrue(source.contains("profile.columns(portrait)"));
        assertTrue(source.contains("profile.rows(portrait)"));
        assertFalse(source.contains("portrait ? 4 : 8"));
        assertFalse(source.contains("portrait ? 8 : 4"));
    }

    @Test
    public void rotationRuleUsesGeneratedProfileBlocks() throws IOException {
        String source = source();
        assertTrue(source.contains("profile.blockOrigins(portrait)"));
        assertTrue(source.contains("profile.totalBlocks()"));
        assertFalse(source.contains("isEightByFourGrid"));
    }

    @Test
    public void gridConfigNormalizationIsNotTheOldGlobalSixToEightRule() throws IOException {
        String source = source();
        assertFalse(source.contains("if ((Integer) args[0] == 6) args[0] = 8;"));
        assertFalse(source.contains("if ((Integer) result == 6) result = 8;"));
        assertTrue("workstation All Apps identity must remain available",
                source.contains("isLaptopAllApps(cellLayout)"));
    }
}
