package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contracts for the upstream Prismal refraction pass on the TextureView/EGL backend. */
public class Miuix307TextureViewStrongRefractionTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    private static String view() throws Exception {
        return Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
    }

    private static String shader() throws Exception {
        return Files.readString(MAIN.resolve("Miuix307PrismalShader.java"));
    }

    private static String material() throws Exception {
        return Files.readString(MAIN.resolve("Miuix307PrismalMaterial.java"));
    }

    @Test
    public void prismalDisplacementStaysDockLocalAfterOesNormalization() throws Exception {
        String source = shader();
        assertTrue(source.contains("uniform vec2  u_resolution"));
        assertTrue(source.contains("sdRoundBox"));
        assertTrue(source.contains("getHeightFromDist"));
        assertTrue(source.contains("vec2 currentOffset = lensDeltaUv + snellOff + bulgeUv")
                && source.contains("vec2 baseOffset = currentOffset"));
        assertTrue(source.contains("vec2 uvCenter = backdropUv(v_screenTexCoord, baseOffset, pinchMix)"));
        assertTrue(source.contains("texture2D(u_blurredTexture, uvCenter)"));
        assertFalse("Stage-B must be isolated before the Prismal optical domain",
                source.contains("uBackdropRect") || source.contains("uTexMatrix")
                        || source.contains("samplerExternalOES"));
    }

    @Test
    public void fixedDiagnosticDisplacementIsGoneAndPhysicalControlsDriveRefraction() throws Exception {
        String source = shader();
        assertFalse(source.contains("float displacementPx = 14.0"));
        assertTrue(source.contains("u_lensRefractionPx"));
        assertTrue(source.contains("u_glassThickness"));
        assertTrue(source.contains("u_ior"));
        assertTrue(source.contains("u_normalStrength"));
        assertTrue(source.contains("u_displacementScale"));
        assertTrue(source.contains("refract(-V, N, 1.0 / u_ior)"));
        assertTrue(source.contains("refract(refIn, -N, u_ior)"));
    }

    @Test
    public void fullUpstreamMaterialColorOpticsAreRestoredWithoutCpuReadback() throws Exception {
        String source = shader();
        assertTrue(source.contains("uniform vec4  u_glassColor"));
        assertTrue(source.contains("u_chromaticAberration"));
        assertTrue(source.contains("u_dispersionR"));
        assertTrue(source.contains("u_dispersionB"));
        assertTrue(source.contains("u_shininess"));
        assertTrue(source.contains("u_rimStrength"));
        assertTrue(source.contains("u_causticIntensity"));
        assertTrue(source.contains("u_fresnelReflect"));
        assertTrue(source.contains("u_transmittance"));
        assertTrue(source.contains("u_blurredTexture") && source.contains("u_useBlurredTexture"));
        assertFalse(source.contains("Bitmap")
                || source.contains("captureScreenAsync")
                || source.contains("glReadPixels"));
    }

    @Test
    public void portableRendererKeepsFramebufferAndGlassDomainsSeparateForPixelStableOptics() throws Exception {
        String source = view();
        String renderer = Files.readString(Path.of(
                "prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java"));
        assertTrue(source.contains("PrismalGeometry prismalGeometry = createPrismalGeometry()")
                && source.contains("prismalRenderer.render("));
        assertTrue(renderer.contains("uniform2f(\"u_resolution\", width, height)"));
        assertTrue(renderer.contains("uniform2f(\"u_mousePos\", g.centerX, height - g.centerY)"));
        assertTrue(renderer.contains("uniform2f(\"u_glassSize\", g.glassWidth, g.glassHeight)"));
    }
}
