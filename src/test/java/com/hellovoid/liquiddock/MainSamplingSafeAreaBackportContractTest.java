package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/** Main-backport gate for the previously validated automatic Prismal sampling safe area. */
public class MainSamplingSafeAreaBackportContractTest {
    @Test public void passBlurUsesAutomaticOpticalGuardPlusSignedUserExtras() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java"));
        assertTrue(source.contains("PrismalSampling.requiredGuardPx"));
        assertTrue(source.contains("combineAutoGuardAndUserExtra"));
        assertTrue(source.contains("fitInsetPairToTextureLimit"));
        assertTrue(source.contains("topSamplingExtraPx"));
        assertTrue(source.contains("leftSamplingExtraPx"));
    }
}
