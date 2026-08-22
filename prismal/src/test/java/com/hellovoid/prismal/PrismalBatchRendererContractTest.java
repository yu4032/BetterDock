package com.hellovoid.prismal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Keeps the folder atlas API additive: Dock retains the same single-edge renderer/model. */
public class PrismalBatchRendererContractTest {
    private static String source() throws Exception {
        Path moduleRelative = Path.of(
                "src/main/java/com/hellovoid/prismal/PrismalRenderer.java");
        Path repoRelative = Path.of(
                "prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java");
        Path path = Files.exists(moduleRelative) ? moduleRelative : repoRelative;
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    public void rendererExposesOneBackdropManyGlassNodes() throws Exception {
        String source = source();
        assertTrue(source.contains("public void prepareBackdrop("));
        assertTrue(source.contains("public void beginGlassFrame()"));
        assertTrue(source.contains("public void drawGlass("));

        int render = source.indexOf("public int render(");
        int output = source.indexOf("public int outputTexture()", render);
        String legacy = source.substring(render, output);
        int prepare = legacy.indexOf("prepareBackdrop(");
        int begin = legacy.indexOf("beginGlassFrame();");
        int draw = legacy.indexOf("drawGlass(geometry, params);");
        assertTrue("legacy render must delegate to the shared-backdrop path",
                prepare >= 0 && begin > prepare && draw > begin);
    }

    @Test
    public void batchApiDoesNotReintroduceLauncherSpecificOptics() throws Exception {
        String source = source();
        assertTrue(source.contains(
                "PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT)"));
        assertFalse(source.contains("enum Mode"));
        assertFalse(source.contains("LAUNCHER_COMPACT"));
        assertFalse(source.contains("PrismalLauncherCompactShader"));
    }
}
