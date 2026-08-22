package com.hellovoid.prismal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void defaultRendererKeepsUpstreamFragment() throws Exception {
        String source = rendererSource();

        assertTrue("renderer must expose an explicit optics mode",
                source.contains("enum Mode")
                        && source.contains("UPSTREAM")
                        && source.contains("DOCK_SINGLE_EDGE"));
        assertTrue("default renderer must select upstream Prismal optics",
                source.contains("this(Mode.UPSTREAM);"));
        assertFalse("default program creation must not unconditionally apply the Dock patch",
                source.contains(
                        "String glassFragment = PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT);"));
    }

    @Test
    public void dockModeIsTheOnlyPathThatAppliesSingleEdgePatch() throws Exception {
        String source = rendererSource();

        assertTrue("Dock-only patch must be selected from the explicit renderer mode",
                source.contains("Mode.DOCK_SINGLE_EDGE")
                        && source.contains("PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT)"));
        assertTrue("upstream mode must retain the vendored fragment unchanged",
                source.contains("PrismalShaderSources.FRAGMENT"));
    }
}
