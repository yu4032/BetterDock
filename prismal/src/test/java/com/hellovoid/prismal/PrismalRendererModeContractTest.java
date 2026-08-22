package com.hellovoid.prismal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Guards the renderer boundary between generic Prismal optics and Dock-only corrections. */
public class PrismalRendererModeContractTest {
    private static String rendererSource() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/hellovoid/prismal/PrismalRenderer.java"),
                StandardCharsets.UTF_8);
    }

    private static PrismalRenderer.Mode rendererMode(PrismalRenderer renderer) throws Exception {
        Field mode = PrismalRenderer.class.getDeclaredField("mode");
        mode.setAccessible(true);
        return (PrismalRenderer.Mode) mode.get(renderer);
    }

    @Test
    public void defaultRendererKeepsUpstreamFragment() throws Exception {
        String source = rendererSource();

        assertEquals("default renderer must select upstream Prismal optics",
                PrismalRenderer.Mode.UPSTREAM, rendererMode(new PrismalRenderer()));
        assertFalse("default program creation must not unconditionally apply the Dock patch",
                source.contains(
                        "String glassFragment = PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT);"));
    }

    @Test
    public void explicitDockModeIsTheOnlyPathThatAppliesSingleEdgePatch() throws Exception {
        String source = rendererSource();

        assertEquals("explicit Dock renderer must retain the Dock-only mode",
                PrismalRenderer.Mode.DOCK_SINGLE_EDGE,
                rendererMode(new PrismalRenderer(PrismalRenderer.Mode.DOCK_SINGLE_EDGE)));
        assertTrue("Dock-only patch must be selected from the explicit renderer mode",
                source.contains("Mode.DOCK_SINGLE_EDGE")
                        && source.contains("PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT)"));
        assertTrue("upstream mode must retain the vendored fragment unchanged",
                source.contains("PrismalShaderSources.FRAGMENT"));
    }
}
