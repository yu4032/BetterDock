package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contracts for the full Prismal refraction pass on the TextureView/EGL backend. */
public class Miuix307TextureViewStrongRefractionTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    private static String view() throws Exception {
        return Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
    }

    private static String material() throws Exception {
        return Files.readString(MAIN.resolve("Miuix307PrismalMaterial.java"));
    }

    @Test
    public void prismalDisplacementStaysDockLocalBeforeBackdropMapping() throws Exception {
        String source = material();
        assertTrue(source.contains("uniform vec2 uViewSize"));
        assertTrue(source.contains("sdRoundRect"));
        assertTrue(source.contains("getHeightFromDist"));
        assertTrue(source.contains("vec2 baseOffset = lensDeltaUv + snellOff + bulgeUv"));
        assertTrue(source.contains("vec2 uvG = vUv + baseOffset"));
        assertTrue("all displaced optical samples must enter the shared Stage-B sampler",
                source.contains("sampleBackdrop(uvG)")
                        && source.contains("uBackdropRect.xy + safeDockUv * uBackdropRect.zw"));
    }

    @Test
    public void fixedDiagnosticDisplacementIsGoneAndPhysicalControlsDriveRefraction() throws Exception {
        String source = material();
        assertFalse("the temporary fixed 14px diagnostic displacement must stay retired",
                source.contains("float displacementPx = 14.0"));
        assertTrue(source.contains("uLensRefractionPx"));
        assertTrue(source.contains("uThicknessPx"));
        assertTrue(source.contains("uIor"));
        assertTrue(source.contains("uNormalStrength"));
        assertTrue(source.contains("refract(-V, N, 1.0 / uIor)"));
        assertTrue(source.contains("refract(refIn, -N, uIor)"));
    }

    @Test
    public void fullMaterialColorOpticsAreRestoredWithoutCpuReadback() throws Exception {
        String source = material();
        assertTrue(source.contains("uniform vec4 uTint"));
        assertTrue(source.contains("uChromaticAberration"));
        assertTrue(source.contains("uSpecularSharp"));
        assertTrue(source.contains("uRimLight"));
        assertTrue(source.contains("uCausticStrength"));
        assertFalse(source.contains("Bitmap")
                || source.contains("captureScreenAsync")
                || source.contains("glReadPixels")
                || source.contains("blurRadius"));
    }

    @Test
    public void drawUploadsTextureViewSizeForPixelStableOptics() throws Exception {
        String source = view();
        assertTrue(source.contains("glGetUniformLocation(program, \"uViewSize\")"));
        assertTrue(source.contains("GLES20.glUniform2f"));
        assertTrue(source.contains("outputWidth"));
        assertTrue(source.contains("outputHeight"));
        assertTrue(source.contains("Miuix307PrismalMaterial.applyUniforms"));
    }
}
