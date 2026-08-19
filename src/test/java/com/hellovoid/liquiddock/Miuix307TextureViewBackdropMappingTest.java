package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Stage-B contracts for one-coordinate-model Dock-local PassBlur mapping. */
public class Miuix307TextureViewBackdropMappingTest {
    private static final Path MAIN =
            Path.of("src/main/java/com/hellovoid/liquiddock");

    private static String view() throws Exception {
        return Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
    }

    private static String material() throws Exception {
        return Files.readString(MAIN.resolve("Miuix307PrismalMaterial.java"));
    }

    @Test
    public void shaderMapsDockLocalUvIntoRootBackdropBeforeTextureMatrix() throws Exception {
        String source = material();
        assertTrue(source.contains("uniform vec4 uBackdropRect"));
        assertTrue(source.contains("uniform int uConfigRot"));
        assertTrue(source.contains("uBackdropRect.xy + safeDockUv * uBackdropRect.zw"));
        assertTrue(source.contains("uTexMatrix * vec4(textureInputUv, 0.0, 1.0)"));
        assertTrue("Prismal displacement must remain in Dock-local space before root mapping",
                source.contains("vec2 baseOffset = lensDeltaUv + snellOff + bulgeUv")
                        && source.contains("vec2 uvCenter = backdropUv(vScreenTexCoord, baseOffset, pinchMix)")
                        && source.contains("sampleBackdrop(uvCenter)"));
        assertFalse("Stage-B material must not reintroduce capture/readback",
                source.contains("Bitmap")
                        || source.contains("captureScreenAsync")
                        || source.contains("glReadPixels"));
        assertTrue("upstream Prismal blur must stay GPU-only over the OES sampler",
                source.contains("uBlurRadiusPx")
                        && source.contains("sampleBackdropRaw"));
    }

    @Test
    public void swappedQuarterTurnProducerUsesMatchingHyperOsRotationDirection() throws Exception {
        String source = material();
        int rot1 = source.indexOf("if (uConfigRot == 1) {");
        int rot2 = source.indexOf("else if (uConfigRot == 2) {", rot1);
        int rot3 = source.indexOf("else if (uConfigRot == 3) {", rot2);
        int transformed = source.indexOf("uTexMatrix * vec4(", rot3);
        assertTrue(rot1 >= 0 && rot2 > rot1 && rot3 > rot2 && transformed > rot3);

        String rot1Branch = source.substring(rot1, rot2);
        String rot2Branch = source.substring(rot2, rot3);
        String rot3Branch = source.substring(rot3, transformed);

        assertTrue("configRot=1 must use the quarter-turn direction paired with swapped producer dimensions",
                rot1Branch.contains("orientedUv = vec2(rootUv.y, 1.0 - rootUv.x);"));
        assertTrue("configRot=2 remains its own inverse",
                rot2Branch.contains("orientedUv = vec2(1.0 - rootUv.x, 1.0 - rootUv.y);"));
        assertTrue("configRot=3 must use the opposite quarter-turn from the pre-swap calibration",
                rot3Branch.contains("orientedUv = vec2(1.0 - rootUv.y, rootUv.x);"));
    }

    @Test
    public void surfaceTextureCropIsPrecompensatedBeforeFinalMatrix() throws Exception {
        String source = material();
        assertTrue("screen-space Dock mapping must neutralize SurfaceTexture's extra X crop before sampling",
                source.contains("vec2 textureInputUv = orientedUv")
                        && source.contains("uTexMatrix[0][0]")
                        && source.contains("uTexMatrix[3][0]")
                        && source.contains("(orientedUv.x - textureOffsetX) / textureScaleX"));
        assertTrue("SurfaceTexture matrix must still perform the final OES transform/Y flip",
                source.contains("uTexMatrix * vec4(textureInputUv, 0.0, 1.0)"));
    }

    @Test
    public void backdropRectComesOnlyFromHostAndRootScreenCoordinates() throws Exception {
        String source = view();
        assertTrue(source.contains("materialHost.getLocationOnScreen(hostScreen)"));
        assertTrue(source.contains("root.getLocationOnScreen(rootScreen)"));
        assertTrue(source.contains("boundSurfaceWidth"));
        assertTrue(source.contains("boundSurfaceHeight"));
        assertTrue(source.contains("1f -"));
        assertFalse("retired SurfaceView compositor geometry must not return",
                source.contains("mScreenRect")
                        || source.contains("mRTLastReportedPosition")
                        || source.contains("getSurfaceRenderPosition"));
    }

    @Test
    public void drawUploadsBackdropRectAndConfigRotation() throws Exception {
        String source = view();
        assertTrue(source.contains("glGetUniformLocation(program, \"uBackdropRect\")"));
        assertTrue(source.contains("glGetUniformLocation(program, \"uConfigRot\")"));
        assertTrue(source.contains("GLES20.glUniform4f"));
        assertTrue(source.contains("GLES20.glUniform1i"));
        assertTrue(source.contains("configRotation"));
    }

    @Test
    public void stageBDiagnosticsExposePreAndPostMatrixGeometry() throws Exception {
        String source = view();
        assertTrue(source.contains("stage-B mapping rootScreen="));
        assertTrue(source.contains("hostScreen="));
        assertTrue(source.contains("backdropRect="));
        assertTrue(source.contains("texture matrix="));
        assertTrue(source.contains("mapped corners"));
        assertTrue(source.contains("configRot="));
    }
}
