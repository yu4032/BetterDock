package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contract for avoiding a second crop on the SurfaceTexture producer domain. */
public class Miuix307PassBlurTextureDomainTest {
    private static final Path VIEW = Path.of(
            "src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurGpuView.java");

    @Test
    public void shaderSamplesTheFullSurfaceTextureDomainWithoutManualSecondCrop() throws Exception {
        String source = Files.readString(VIEW);
        int shaderStart = source.indexOf("private static final String FRAGMENT_SHADER");
        int shaderEnd = source.indexOf("private static final class ProducerGeometry", shaderStart);
        assertTrue(shaderStart >= 0 && shaderEnd > shaderStart);
        String shader = source.substring(shaderStart, shaderEnd);

        assertFalse("SurfaceTexture transform already owns the producer crop; shader must not crop again",
                shader.contains("uniform vec4 uCrop")
                        || shader.contains("uCrop.x")
                        || shader.contains("uCrop.y")
                        || shader.contains("uCrop.z")
                        || shader.contains("uCrop.w"));
        assertTrue("edge lens must feed its local UV directly into the SurfaceTexture transform",
                shader.contains("uTexMatrix * vec4(lensUv, 0.0, 1.0)"));

        assertTrue("manual crop diagnostics may remain for comparison but must not drive sampling",
                source.contains("cropSF=") || source.contains("cropX"));
    }
}
