package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class ZeroCopyReleaseContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void legacyScreenCaptureImplementationIsRemoved() throws Exception {
        assertFalse(Files.exists(MAIN.resolve("LiveScreenCapture.java")));
        String glass = Files.readString(MAIN.resolve("DockLiquidGlassView.java"));
        assertFalse(glass.contains("captureScreenAsync"));
        assertFalse(glass.contains("BitmapShader"));
        assertFalse(glass.contains("BitmapCompat"));
        assertFalse(glass.contains("LiveScreenCapture"));
    }

    @Test public void launcherDoesNotInstallLegacyCaptureBridges() throws Exception {
        String module = Files.readString(MAIN.resolve("ModuleMain.java"));
        assertFalse(module.contains("FreeformCaptureLeashHook.install"));
        assertFalse(module.contains("Miuix307RecentsInputHook.install"));
        assertFalse(module.contains("Miuix307GestureBackdropHoldHook.install"));
        assertFalse(module.contains("Miuix307CaptureOwnershipHook.install"));
        assertFalse(module.contains("WorkstationWallpaperOnlyHook.install"));
        assertFalse(module.contains("DiagnosticTraceHook.installLauncher"));
        assertFalse(module.contains("AlwaysOnDiagnosticTrace.installLauncher"));
    }

    @Test public void retiredCompatibilityToggleCannotRestoreCaptureBackend() throws Exception {
        String reader = Files.readString(MAIN.resolve("ConfigReader.java"));
        assertTrue(reader.contains("ZERO_COPY_PIPELINE_KEY"));
        assertTrue(reader.contains("if (ZERO_COPY_PIPELINE_KEY.equals(key)) return true;"));
    }
}
