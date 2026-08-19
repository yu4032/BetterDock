package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contracts for semantic parity with the proven legacy Prismal RuntimeShader. */
public class Miuix307PrismalLegacyParityTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    private static String material() throws Exception {
        return Files.readString(MAIN.resolve("Miuix307PrismalMaterial.java"));
    }

    @Test
    public void zeroCopyShaderUsesLegacyPrismalNormalAndColorModelExactly() throws Exception {
        String source = material();
        assertFalse("legacy Prismal has no extra forced meniscus normal blend",
                source.contains("meniscusN") || source.contains("menBlend"));
        assertFalse("legacy Prismal has no invented secondary reflection sampling pass",
                source.contains("reflShell") || source.contains("reflW") || source.contains("reflUv"));
        assertTrue("legacy Prismal color transform must be retained before highlights",
                source.contains("vec3(0.137, 0.145, 1.0)")
                        || source.contains("vec3(0.137,0.145,1.0)"));
    }

    @Test
    public void zeroCopyShaderUsesOneLegacyYDownLocalCoordinateSystem() throws Exception {
        String source = material();
        assertTrue("GL bottom-left UV must be converted once to legacy RuntimeShader Y-down UV",
                source.contains("vec2 localUv = vec2(vUv.x, 1.0 - vUv.y)"));
        assertTrue("all Prismal shape/normal work must derive from the same Y-down local coordinate",
                source.contains("vec2 coordPx = localUv * uViewSize")
                        && source.contains("vec2 pPx = coordPx - uViewSize * 0.5"));
        assertTrue("sampling must convert legacy Y-down UV back to GL-local UV only at the OES adapter",
                source.contains("sampleBackdropYDown")
                        && source.contains("vec2(dockUvYDown.x, 1.0 - dockUvYDown.y)"));
        assertFalse("mixed ad-hoc coordinate aliases must be removed",
                source.contains("vec2 cKy = vec2(pPx.x, -pPx.y)"));
    }

    @Test
    public void legacyHighlightGateAndGuiBlurBackendAreRestored() throws Exception {
        String source = material();
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));
        String host = Files.readString(MAIN.resolve("DockLiquidGlassHostView.java"));

        assertTrue(source.contains("uniform float uHighlightEnabled"));
        assertTrue(source.contains("if (uHighlightEnabled > 0.5)"));
        assertTrue(source.contains("highlightEnabled"));
        assertTrue(source.contains("glass.blurMode == LiquidBlurMode.SHADER ? 1f : 0f"));
        assertTrue("zero-copy host must know the selected GUI blur backend so ADVANCED highlight stays external",
                renderer.contains("host.setZeroCopyBlurBackend(glassConfig.blurMode)")
                        && renderer.contains("hostRef.get()")
                        && host.contains("void setZeroCopyBlurBackend(LiquidBlurMode mode)"));
    }

    @Test
    public void guiBlurAndAllPrismalParamsHotSyncIntoTheOesShader() throws Exception {
        String source = material();
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        assertTrue(source.contains("uniform float uBlurRadiusPx"));
        assertTrue(source.contains("uniform float uShaderBlurEnabled"));
        assertTrue("legacy shader blur kernel must be present for the GUI shader backend",
                source.contains("sampleBlurred")
                        && source.contains("0.256")
                        && source.contains("0.045")
                        && source.contains("0.028")
                        && source.contains("0.013")
                        && source.contains("0.005")
                        && source.contains("0.002"));
        assertTrue(source.contains("blurRadiusPx"));
        assertTrue(source.contains("shaderBlurEnabled"));
        assertTrue(source.contains("fromConfig(LiquidDockConfig.Glass glass, float density, float blurRadiusPx)"));
        assertTrue(view.contains("setGlassConfig(LiquidDockConfig.Glass glassConfig, int blurRadiusPx)"));
        assertTrue(renderer.contains("gpuBackdrop.setGlassConfig(glassConfig, blurRadiusPx)"));
        assertTrue(renderer.contains("gpuBackdrop.setGlassConfig(glassConfig, blurRadiusPx)"));

        String[] guiFields = new String[]{
                "glass.ior", "glass.thickness", "glass.normalStrength", "glass.dome",
                "glass.lensRefraction", "glass.chromatic", "glass.highlightWidth",
                "glass.depthEffect", "glass.brightness", "glass.specularSharp",
                "glass.specularStrength", "glass.rimLight", "glass.caustics",
                "glass.edgeBand", "glass.highlightAlpha", "glass.tintR",
                "glass.tintG", "glass.tintB", "glass.tintAlpha", "glass.blurMode"
        };
        for (String field : guiFields) {
            assertTrue("missing GUI material mapping: " + field, source.contains(field));
        }
        assertFalse("fixed diagnostic lens must never return", source.contains("14.0"));
    }
}
