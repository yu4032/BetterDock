package com.hellovoid.prismal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Guards runtime component gates for Launcher compact and Dock single-edge render modes. */
public class PrismalComponentGateContractTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    @Test
    public void bothProductionModesApplyRuntimeComponentGates() throws Exception {
        Path path = Path.of("src/main/java/com/hellovoid/prismal/PrismalComponentGateShader.java");
        assertTrue("runtime component gate transform must exist", Files.exists(path));
        String gate = read(path.toString());
        String launcher = read("src/main/java/com/hellovoid/prismal/PrismalLauncherCompactShader.java");
        String dock = read("src/main/java/com/hellovoid/prismal/PrismalSingleEdgeShader.java");

        String[] uniforms = {
                "u_componentSkyHaze", "u_componentSpecular", "u_componentLitRim",
                "u_componentOppositeRim", "u_componentCornerRim", "u_componentFaceSheen",
                "u_componentPlainHighlight", "u_componentCaustics", "u_componentPressGlow"
        };
        for (String uniform : uniforms) {
            assertTrue("missing runtime gate " + uniform, gate.contains(uniform));
        }
        assertTrue(launcher.contains("PrismalComponentGateShader.apply(corrected)"));
        assertTrue(dock.contains("PrismalComponentGateShader.apply(corrected)"));
        assertFalse("launcher must no longer permanently strip upstream highlights",
                launcher.contains("PrismalLauncherHighlightSuppressionShader.apply(corrected)"));
    }

    @Test
    public void compactSafeHighlightHasItsOwnRuntimeGate() throws Exception {
        String compact = read("src/main/java/com/hellovoid/prismal/PrismalLauncherCompactHighlightShader.java");
        String renderer = read("src/main/java/com/hellovoid/prismal/PrismalRenderer.java");

        assertTrue(compact.contains("u_componentCompactSafeHighlight"));
        assertTrue(renderer.contains("PrismalComponentControls.forMode(mode)"));
        assertTrue(renderer.contains("u_componentSkyHaze"));
        assertTrue(renderer.contains("u_componentCompactSafeHighlight"));
    }
}
