package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Keeps upstream Prismal optical offsets oriented correctly in the zero-copy texture domain. */
public class Miuix307PrismalRefractionDirectionContractTest {
    private static final Path SHADER = Path.of(
            "src/main/java/com/hellovoid/liquiddock/Miuix307PrismalShader.java");

    @Test
    public void upstreamOpticalOffsetsConvertFromTopOriginToLocalBottomOriginTextureUv() throws Exception {
        String shader = Files.readString(SHADER);

        assertTrue("zero-copy normalized backdrop keeps local bottom-origin UV",
                shader.contains("v_screenTexCoord = aUv;"));
        assertTrue("Prismal optical vectors must flip Y before entering local texture UV",
                shader.contains("vec2 upstreamOffsetToLocalTextureUv(vec2 offset)")
                        && shader.contains("return vec2(offset.x, -offset.y);"));
        assertTrue("all center/chromatic optical samples must use the converted offset",
                shader.contains("vec2 dockUv = scaled + upstreamOffsetToLocalTextureUv(offset);"));
        assertTrue("reflection displacement must use the same basis conversion",
                shader.contains("upstreamOffsetToLocalTextureUv(reflOffset)"));

        assertFalse("do not fix direction by flipping the whole backdrop image",
                shader.contains("v_screenTexCoord = vec2(aUv.x, 1.0 - aUv.y);"));
    }

    @Test
    public void overscanMappingMayScaleDisplacementButMustNeverChangeItsSign() throws Exception {
        String shader = Files.readString(SHADER);

        assertTrue(shader.contains("u_dockUvRect.xy + dockUv * u_dockUvRect.zw"));
        assertFalse("overscan mapping must not introduce an additional Y sign inversion",
                shader.contains("u_dockUvRect.xy + vec2(dockUv.x, -dockUv.y) * u_dockUvRect.zw"));
    }
}
