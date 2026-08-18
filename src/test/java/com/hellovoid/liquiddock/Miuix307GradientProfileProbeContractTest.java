package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contracts for passive discovery of MIUIX compositor gradient profiles. */
public class Miuix307GradientProfileProbeContractTest {
    private static String read(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/" + file));
    }

    @Test
    public void probeObservesOnlyNativeGradientProfilesWithoutApplyingGuesses() throws Exception {
        String probe = read("Miuix307GradientProfileProbe.java");
        String module = read("ModuleMain.java");

        assertTrue(probe.contains("miuix.core.util.MiuiBlurUtils"));
        assertTrue(probe.contains("setBackgroundGradientBlurParams"));
        assertTrue(probe.contains("MaterialConfig"));
        assertTrue(probe.contains("MaterialToken"));
        assertTrue(probe.contains("blurType"));
        assertTrue(probe.contains("blurSubType"));
        assertTrue(probe.contains("blurExtraParams"));
        assertTrue(probe.contains("type != 2"));
        assertTrue(probe.contains("params.clone()"));
        assertTrue(probe.contains("native gradient profile observed"));
        assertTrue(module.contains("Miuix307GradientProfileProbe.install(classLoader)"));
        assertFalse(probe.contains("captureScreenAsync"));
        assertFalse(probe.contains("Bitmap"));
        assertFalse(probe.contains("setBackgroundBlurType(target"));
    }
}
