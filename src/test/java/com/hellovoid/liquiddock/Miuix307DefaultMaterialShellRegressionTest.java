package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Prevents zero-copy-only mode from erasing the unsupported default MiuiX Dock shell. */
public class Miuix307DefaultMaterialShellRegressionTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void defaultMiuixMaterialBodyRemainsVisibleWhenExactZeroCopyIsUnsupported() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixGlassHook.java"));
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));
        String bridge = Files.readString(MAIN.resolve("Miuix307CompositorOpticsBridge.java"));

        assertTrue("exact framework background blur remains scoped to themed BlurBackground2",
                bridge.contains("COMPAT_BLUR_BACKGROUND2")
                        && bridge.contains("usesExactBackgroundBlur"));

        assertTrue("material-body transparency must be explicitly scoped to compat/themed owner",
                hook.contains("shouldSuppressVendorMaterialBody")
                        && hook.contains("COMPAT_BACKGROUND_CLASS.equals(dockBg.getClass().getName())"));
        assertFalse("the default MiuiX owner must never be made transparent by the generic owner test",
                hook.contains("if (dockBg == null || !isNativeVisualOwner(dockBg)) return;\n"
                        + "        float radius = Math.max(0f, nativeRadius);"));

        int unsupported = renderer.indexOf("if (!Miuix307CompositorOpticsBridge.usesExactBackgroundBlur(materialHost))");
        int backdrop = renderer.indexOf("Miuix307PassBlurTextureView gpuBackdrop", unsupported);
        assertTrue(unsupported >= 0 && backdrop > unsupported);
        String unsupportedRegion = renderer.substring(unsupported, backdrop);
        assertTrue("unsupported default material must report no zero-copy candidate", unsupportedRegion.contains("return false;"));
        assertFalse("unsupported default material must not retain fake renderer ownership",
                unsupportedRegion.contains("hostRef =") || unsupportedRegion.contains("materialHostRef ="));
    }
}
