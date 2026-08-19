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

    @Test
    public void shaderMapsDockLocalUvIntoRootBackdropBeforeTextureMatrix() throws Exception {
        String source = view();
        assertTrue(source.contains("uniform vec4 uBackdropRect"));
        assertTrue(source.contains("uniform int uConfigRot"));
        assertTrue(source.contains("uBackdropRect.xy + lensUv * uBackdropRect.zw"));
        assertTrue(source.contains("uTexMatrix * vec4(orientedUv, 0.0, 1.0)"));
        assertTrue("diagnostic refraction must stay in Dock-local UV space before backdrop mapping",
                source.indexOf("lensUv = mix(vUv, refractedUv, edgeWeight)")
                        < source.indexOf("uBackdropRect.xy + lensUv * uBackdropRect.zw"));
        assertFalse("diagnostic lens must not reintroduce material color or blur effects",
                source.contains("uTint")
                        || source.contains("uHighlight")
                        || source.contains("chromatic")
                        || source.contains("dispersion")
                        || source.contains("blurRadius"));
    }

    @Test
    public void shaderUsesInverseHyperOsConfigRotationToMapScreenBackToProducer() throws Exception {
        String source = view();
        assertTrue("configRot=1 branch must exist",
                source.contains("if (uConfigRot == 1) {\\n"));
        assertTrue("configRot=1 must inverse-map screen coordinates with the former rot3 transform",
                source.contains("orientedUv = vec2(1.0 - rootUv.y, rootUv.x);\\n"));
        assertTrue("configRot=2 branch must exist",
                source.contains("else if (uConfigRot == 2) {\\n"));
        assertTrue("configRot=2 remains its own inverse",
                source.contains("orientedUv = vec2(1.0 - rootUv.x, 1.0 - rootUv.y);\\n"));
        assertTrue("configRot=3 branch must exist",
                source.contains("else if (uConfigRot == 3) {\\n"));
        assertTrue("configRot=3 must inverse-map screen coordinates with the former rot1 transform",
                source.contains("orientedUv = vec2(rootUv.y, 1.0 - rootUv.x);\\n"));
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
