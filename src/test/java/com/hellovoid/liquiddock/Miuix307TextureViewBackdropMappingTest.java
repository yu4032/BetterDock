package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Stage-B contracts for the OES-normalization boundary. */
public class Miuix307TextureViewBackdropMappingTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final float EPS = 0.0001f;

    private static String view() throws Exception {
        return Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
    }

    private static String adapter() throws Exception {
        return Files.readString(MAIN.resolve("Miuix307PassBlurShaders.java"));
    }

    private static String prismal() throws Exception {
        return Files.readString(MAIN.resolve("Miuix307PrismalShader.java"));
    }

    private static float[] mapFinal(float x, float y, int rotation, float[] matrix) throws Exception {
        Method method = Miuix307PassBlurTextureView.class.getDeclaredMethod(
                "mapFinalCoordinate", float.class, float.class, int.class, float[].class);
        method.setAccessible(true);
        return (float[]) method.invoke(null, x, y, rotation, matrix);
    }

    private static float[] identityMatrix() {
        return new float[]{
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
        };
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
        assertTrue(source.contains("vec2 baseOffset = lensDeltaUv + snellOff + bulgeUv"));
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

        float[] r1 = mapFinal(0.2f, 0.3f, 1, identityMatrix());
        assertEquals(0.3f, r1[0], EPS);
        assertEquals(0.8f, r1[1], EPS);
        float[] r3 = mapFinal(0.2f, 0.3f, 3, identityMatrix());
        assertEquals(0.7f, r3[0], EPS);
        assertEquals(0.2f, r3[1], EPS);
    }

    @Test
    public void surfaceTextureCropCompensationPreservesFlipOnBothAxes() throws Exception {
        // A centered X crop plus a non-unit vertically flipped crop. The calibration must remove
        // the crop/scale while preserving the matrix's vertical-flip orientation.
        float[] matrix = new float[]{
                0.8f, 0f, 0f, 0f,
                0f, -0.75f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0.1f, 0.9f, 0f, 1f
        };
        float[] mapped = mapFinal(0.25f, 0.2f, 0, matrix);
        assertEquals(0.25f, mapped[0], EPS);
        assertEquals(0.8f, mapped[1], EPS);
    }

    @Test
    public void surfaceTextureCropCompensationPreservesQuarterTurnAffineOrientation() throws Exception {
        // 90-degree orientation with independent crop scales on both producer axes.
        float[] matrix = new float[]{
                0f, 0.8f, 0f, 0f,
                -0.7f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0.8f, 0.1f, 0f, 1f
        };
        float[] mapped = mapFinal(0.25f, 0.2f, 0, matrix);
        assertEquals(0.8f, mapped[0], EPS);
        assertEquals(0.25f, mapped[1], EPS);
    }

    @Test
    public void surfaceTextureCropUsesFullTwoAxisOrientationPreservingCompensation() throws Exception {
        String source = adapter();
        assertTrue(source.contains("compensateSurfaceTextureCropPreservingOrientation"));
        assertTrue(source.contains("uTexMatrix[0][0]")
                && source.contains("uTexMatrix[1][0]")
                && source.contains("uTexMatrix[0][1]")
                && source.contains("uTexMatrix[1][1]"));
        assertTrue(source.contains("float determinant"));
        assertTrue(source.contains("orientationBias"));
        assertTrue(source.contains("uTexMatrix * vec4(textureInputUv, 0.0, 1.0)"));
        assertFalse("one-axis-only crop compensation must not return",
                source.contains("(orientedUv.x - textureOffsetX) / textureScaleX"));
    }

    @Test
    public void backdropRectUsesTextureViewOutputGeometryAndViewRootWindowFrame() throws Exception {
        String source = view();
        int start = source.indexOf("private void updateBackdropMapping()");
        int end = source.indexOf("private ProducerGeometry readSurfaceGeometry", start);
        assertTrue(start >= 0 && end > start);
        String region = source.substring(start, end);

        assertTrue(region.contains("int visibleWidth = outputWidth > 0 ? outputWidth : getWidth();"));
        assertTrue(region.contains("int visibleHeight = outputHeight > 0 ? outputHeight : getHeight();"));
        assertTrue(region.contains("getLocationOnScreen(viewScreen);"));
        assertTrue(region.contains("mWinFrameInScreen"));
        assertTrue(region.contains("Miuix307BackdropMapping.compute"));
        assertTrue(region.contains("producerCoverage"));
        assertFalse("mapping must not mix parent dimensions with the TextureView/FBO dimensions",
                region.contains("materialHost.getWidth()")
                        || region.contains("materialHost.getHeight()")
                        || region.contains("materialHost.getLocationOnScreen"));
        assertFalse(region.contains("mScreenRect")
                || region.contains("mRTLastReportedPosition")
                || region.contains("getSurfaceRenderPosition"));
    }

    @Test
    public void normalizationAndDockUvRectShareOneResolvedSamplingInsetSet() throws Exception {
        String source = view();
        int fboStart = source.indexOf("private void ensureFboSize");
        int fboEnd = source.indexOf("private void drawLatestFrame", fboStart);
        int mapStart = source.indexOf("private void updateBackdropMapping()");
        int mapEnd = source.indexOf("private ProducerGeometry readSurfaceGeometry", mapStart);
        assertTrue(fboStart >= 0 && fboEnd > fboStart && mapStart >= 0 && mapEnd > mapStart);

        String fbo = source.substring(fboStart, fboEnd);
        String mapping = source.substring(mapStart, mapEnd);
        assertTrue(fbo.contains("SamplingInsets insets = resolveSamplingInsets(width, height);"));
        assertTrue(mapping.contains(
                "SamplingInsets insets = resolveSamplingInsets(visibleWidth, visibleHeight, frameParams);"));
        assertTrue(source.contains("ensureFboSizeExact(mapping.sampleWidth, mapping.sampleHeight)"));
        assertTrue(source.contains("renderNormalizationPass(mapping)"));
        assertTrue(source.contains("createPrismalGeometry(mapping)"));
        assertTrue(source.contains("renderCompositePass(prismalTexture, mapping)"));
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
        assertTrue(source.contains("viewScreen="));
        assertTrue(source.contains("hostScreen="));
        assertTrue(source.contains("backdropRect="));
        assertTrue(source.contains("validDockRect="));
        assertTrue(source.contains("coverage="));
        assertTrue(source.contains("texture matrix="));
        assertTrue(source.contains("mapped corners"));
        assertTrue(source.contains("configRot="));
    }
}
