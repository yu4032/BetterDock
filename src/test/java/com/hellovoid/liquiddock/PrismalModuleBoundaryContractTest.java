package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Architectural boundary: Prismal is reusable GL, LiquidDock owns platform adaptation only. */
public class PrismalModuleBoundaryContractTest {
    private static final Path MODULE = Path.of("prismal/src/main");
    private static final Path APP = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void appDependsOnStandalonePrismalModule() throws Exception {
        String settings = Files.readString(Path.of("settings.gradle.kts"));
        String build = Files.readString(Path.of("build.gradle.kts"));
        assertTrue(settings.contains("include(\":prismal\")"));
        assertTrue(build.contains("implementation(project(\":prismal\"))"));
    }

    @Test
    public void officialFragmentContainsNoLiquidDockPlatformMapping() throws Exception {
        String shader = Files.readString(MODULE.resolve("res/raw/prismal_fragment.glsl"));
        assertTrue(shader.contains("vec2 baseOffset = lensDeltaUv + snellOff + bulgeUv;"));
        assertTrue(shader.contains("return clamp(scaled + offset, vec2(0.0), vec2(1.0));"));
        assertFalse(shader.contains("u_dockUvRect"));
        assertFalse(shader.contains("samplerExternalOES"));
        assertFalse(shader.contains("uTexMatrix"));
        assertFalse(shader.contains("legacySCurve"));
        assertFalse(shader.contains("LiquidDock"));
    }

    @Test
    public void portableRendererUsesOfficialFramebufferAndGlassDomains() throws Exception {
        String renderer = Files.readString(
                MODULE.resolve("java/com/hellovoid/prismal/PrismalRenderer.java"));
        assertTrue(renderer.contains("uniform2f(\"u_resolution\", width, height)"));
        assertTrue(renderer.contains("uniform2f(\"u_mousePos\", g.centerX, height - g.centerY)"));
        assertTrue(renderer.contains("uniform2f(\"u_glassSize\", g.glassWidth, g.glassHeight)"));
        assertTrue(renderer.contains("sourceFramebuffer")
                && renderer.contains("blurFramebufferH")
                && renderer.contains("blurFramebufferV")
                && renderer.contains("outputFramebuffer"));
        assertFalse(renderer.contains("import android.graphics.SurfaceTexture"));
        assertFalse(renderer.contains("import android.view."));
        assertFalse(renderer.contains("GLES11Ext"));
        assertFalse(renderer.contains("import com.hellovoid.liquiddock"));
        assertFalse(renderer.contains("Miuix307"));
        assertFalse(renderer.contains("android.content.Context"));
        assertFalse(renderer.contains("android.content.res.Resources"));
        assertFalse(renderer.contains("openRawResource"));
        assertTrue(renderer.contains("PrismalShaderSources.FRAGMENT"));
        assertTrue(renderer.contains("glassUniformLocations"));
        assertTrue(renderer.contains("GLES20.glGetUniformLocation(glassProgram, name)"));
        assertTrue(renderer.contains("GLES20.glUniform1f(glassUniformLocation(name), value)"));
        assertFalse(renderer.contains("GLES20.glUniform1f(requireUniform(glassProgram, name), value)"));
        assertFalse(renderer.contains("requireUniform(glassProgram, \"u_backgroundTexture\")"));
    }

    @Test
    public void liquidDockAdapterOwnsOesNormalizationMappingLogAndFinalCrop() throws Exception {
        String view = Files.readString(APP.resolve("Miuix307PassBlurTextureView.java"));
        String composite = Files.readString(APP.resolve("Miuix307PrismalCompositeShaders.java"));
        assertTrue(view.contains("Miuix307PassBlurShaders.OES_NORMALIZE_FRAGMENT"));
        assertTrue(view.contains("prismalRenderer.render("));
        assertTrue(view.contains("createPrismalGeometry(mapping)"));
        assertTrue(view.contains("private volatile BackdropSnapshot backdropSnapshot"));
        assertTrue(view.contains("BackdropSnapshot mapping = backdropSnapshot"));
        assertTrue(view.contains("ensureFboSizeExact(mapping.sampleWidth, mapping.sampleHeight)"));
        assertTrue(view.contains("renderNormalizationPass(mapping)"));
        assertTrue(view.contains("[DC][PRISMAL-MAP]"));
        assertTrue(view.contains("renderCompositePass(prismalTexture, mapping)"));
        assertTrue(view.contains("if (backdropSnapshot != mapping"));
        assertTrue(composite.contains("uCropRect.xy + vUv * uCropRect.zw"));
    }
}
