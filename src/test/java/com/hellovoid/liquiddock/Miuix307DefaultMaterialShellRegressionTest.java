package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Prevents zero-copy-only mode from erasing or excluding the default MiuiX Dock shell. */
public class Miuix307DefaultMaterialShellRegressionTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void defaultMiuixMaterialAttemptsPassBlurAndKeepsItsVendorBodyAsFailureProtection() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixGlassHook.java"));
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));
        String bridge = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));

        assertTrue("the modern zero-copy path is rooted in SetPassBlurSurface rather than themed backgroundBlur",
                bridge.contains("SetPassBlurSurface")
                        && bridge.contains("View materialHost, Surface producerSurface"));

        assertFalse("PassBlur TextureView install must not reject the default MiuiX owner using the obsolete exact-backgroundBlur gate",
                renderer.contains("if (!Miuix307CompositorOpticsBridge.usesExactBackgroundBlur(materialHost))"));
        assertTrue("both supported HotSeats owners should reach the same TextureView renderer through MiuixGlassHook",
                hook.contains("NATIVE_BACKGROUND_CLASS")
                        && hook.contains("COMPAT_BACKGROUND_CLASS")
                        && hook.contains("Miuix307ZeroCopyRenderer.install("));

        assertTrue("material-body transparency must be explicitly scoped to compat/themed owner",
                hook.contains("shouldSuppressVendorMaterialBody")
                        && hook.contains("COMPAT_BACKGROUND_CLASS.equals(dockBg.getClass().getName())"));
        assertFalse("the default MiuiX owner must never be made transparent by the generic owner test",
                hook.contains("if (dockBg == null || !isNativeVisualOwner(dockBg)) return;\n"
                        + "        float radius = Math.max(0f, nativeRadius);"));
    }
}
