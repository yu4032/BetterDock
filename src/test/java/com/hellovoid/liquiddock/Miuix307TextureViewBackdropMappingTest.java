package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Stage-B contracts for the OES-normalization boundary. */
public class Miuix307TextureViewBackdropMappingTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    private static String view() throws Exception {
        return Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
    }

    private static String adapter() throws Exception {
        return Files.readString(MAIN.resolve("Miuix307PassBlurShaders.java"));
    }

    private static String prismal() throws Exception {
        return Files.readString(MAIN.resolve("Miuix307PrismalShader.java"));
    }

    @Test
    public void normalizationMapsDockLocalUvIntoRootBackdropBeforeTextureMatrix() throws Exception {
        String source = adapter();
        assertTrue(source.contains("uniform vec4 uBackdropRect"));
        assertTrue(source.contains("uniform int uConfigRot"));
        assertTrue(source.contains("vec2 sampleDockUv = mirrorDockUv(vUv)"));
        assertTrue(source.contains("uBackdropRect.xy + sampleDockUv * uBackdropRect.zw"));
        assertTrue(source.contains("uTexMatrix * vec4(textureInputUv, 0.0, 1.0)"));
        assertTrue(source.contains("uValidDockRect"));
        assertFalse("normalization must never reintroduce CPU readback",
                source.contains("Bitmap") || source.contains("captureScreenAsync")
                        || source.contains("glReadPixels"));
    }

    @Test
    public void prismalOpticsStayDockLocalAfterNormalization() throws Exception {
        String source = prismal();
        assertTrue(source.contains("vec2 currentOffset = lensDeltaUv + snellOff + bulgeUv")
                && source.contains("vec2 baseOffset = currentOffset"));
        assertTrue(source.contains("vec2 uvCenter = backdropUv(v_screenTexCoord, baseOffset, pinchMix)"));
        assertTrue(source.contains("texture2D(u_blurredTexture, uvCenter)"));
        assertFalse(source.contains("uBackdropRect"));
        assertFalse(source.contains("uTexMatrix"));
        assertFalse(source.contains("samplerExternalOES"));
    }

    @Test
    public void swappedQuarterTurnProducerUsesMatchingHyperOsRotationDirection() throws Exception {
        String source = adapter();
        int rot1 = source.indexOf("if (uConfigRot == 1) {");
        int rot2 = source.indexOf("else if (uConfigRot == 2) {", rot1);
        int rot3 = source.indexOf("else if (uConfigRot == 3) {", rot2);
        int transformed = source.indexOf("uTexMatrix * vec4(", rot3);
        assertTrue(rot1 >= 0 && rot2 > rot1 && rot3 > rot2 && transformed > rot3);

        String rot1Branch = source.substring(rot1, rot2);
        String rot2Branch = source.substring(rot2, rot3);
        String rot3Branch = source.substring(rot3, transformed);
        assertTrue(rot1Branch.contains("return vec2(rootUv.y, 1.0 - rootUv.x);"));
        assertTrue(rot2Branch.contains("return vec2(1.0 - rootUv.x, 1.0 - rootUv.y);"));
        assertTrue(rot3Branch.contains("return vec2(1.0 - rootUv.y, rootUv.x);"));
    }

    @Test
    public void surfaceTextureCropIsPrecompensatedBeforeFinalMatrix() throws Exception {
        String source = adapter();
        assertTrue(source.contains("vec2 textureInputUv = orientedUv")
                && source.contains("uTexMatrix[0][0]")
                && source.contains("uTexMatrix[3][0]")
                && source.contains("(orientedUv.x - textureOffsetX) / textureScaleX"));
        assertTrue(source.contains("uTexMatrix * vec4(textureInputUv, 0.0, 1.0)"));
    }

    @Test
    public void backdropRectUsesHostAndViewRootWindowFrameAndCoverageHelper() throws Exception {
        String source = view();
        assertTrue(source.contains("materialHost.getLocationOnScreen(hostScreen)"));
        assertTrue(source.contains("mWinFrameInScreen"));
        assertTrue(source.contains("Miuix307BackdropMapping.compute"));
        assertTrue(source.contains("producerCoverage"));
        assertFalse(source.contains("mScreenRect")
                || source.contains("mRTLastReportedPosition")
                || source.contains("getSurfaceRenderPosition"));
    }

    @Test
    public void normalizationUploadsBackdropRectRotationAndValidDockRect() throws Exception {
        String source = view();
        assertTrue(source.contains("requireUniform(normalizeProgram, \"uBackdropRect\")"));
        assertTrue(source.contains("requireUniform(normalizeProgram, \"uConfigRot\")"));
        assertTrue(source.contains("requireUniform(normalizeProgram, \"uValidDockRect\")"));
        assertTrue(source.contains("GLES20.glUniform4f"));
        assertTrue(source.contains("GLES20.glUniform1i"));
    }

    @Test
    public void stageBDiagnosticsExposeCoverageAndPrePostMatrixGeometry() throws Exception {
        String source = view();
        assertTrue(source.contains("stage-B mapping rootScreen="));
        assertTrue(source.contains("hostScreen="));
        assertTrue(source.contains("backdropRect="));
        assertTrue(source.contains("validDockRect="));
        assertTrue(source.contains("coverage="));
        assertTrue(source.contains("texture matrix="));
        assertTrue(source.contains("mapped corners"));
        assertTrue(source.contains("configRot="));
    }
}
