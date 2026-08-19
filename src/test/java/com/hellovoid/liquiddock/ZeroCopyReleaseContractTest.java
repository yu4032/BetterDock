package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;

public class ZeroCopyReleaseContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path MANIFEST = Path.of("src/main/AndroidManifest.xml");
    private static final Path SCOPE = Path.of("src/main/resources/META-INF/xposed/scope.list");

    @Test public void legacyScreenCaptureImplementationIsRemoved() throws Exception {
        assertFalse(Files.exists(MAIN.resolve("LiveScreenCapture.java")));
        assertNoProductionToken("captureScreenAsync");
        assertNoProductionToken("BitmapShader");
        assertNoProductionToken("BitmapCompat");
        assertNoProductionToken("glReadPixels");
    }

    @Test public void systemUiIsNoLongerInModuleScope() throws Exception {
        List<String> packages = Files.readAllLines(SCOPE).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
        assertEquals(List.of("com.miui.home"), packages);

        String module = Files.readString(MAIN.resolve("ModuleMain.java"));
        assertFalse(module.contains("com.android.systemui"));
        assertFalse(module.contains("SystemUiTaskExecutorSource"));
        assertFalse(module.contains("SystemUiTransitionSource"));
        assertFalse(module.contains("SystemUiHomeOwnershipSource"));
        assertFalse(module.contains("SystemUiFreeformLeashProvider"));
    }

    @Test public void retiredSceneAndSystemUiBridgesArePhysicallyRemoved() {
        String[] retired = {
                "AlwaysOnDiagnosticTrace.java",
                "BackdropVisualPolicy.java",
                "CaptureCadence.java",
                "CaptureExclusionNames.java",
                "CaptureScene.java",
                "CaptureSceneState.java",
                "CaptureSourcePolicy.java",
                "DiagnosticTraceHook.java",
                "DockLiquidGlassView.java",
                "FreeformBridgePolicy.java",
                "FreeformLayerResolver.java",
                "FreeformLeashBrokerClient.java",
                "FreeformLeashBrokerService.java",
                "FreeformLeashProtocol.java",
                "FreeformLeashRuntime.java",
                "FreeformTaskLeashResolver.java",
                "HomeOwnershipPolicy.java",
                "HomeOwnershipProtocol.java",
                "HomeOwnershipResolver.java",
                "HomeOwnershipRuntime.java",
                "LiquidBlurBackendPolicy.java",
                "LiquidBlurMode.java",
                "LiquidGlassFactory.java",
                "Miuix307CaptureOwnershipHook.java",
                "Miuix307GestureBackdropHoldHook.java",
                "Miuix307RecentsInputHook.java",
                "SystemUiFreeformLeashProvider.java",
                "SystemUiHomeOwnershipSource.java",
                "SystemUiTaskExecutorSource.java",
                "SystemUiTaskStateProvider.java",
                "SystemUiTransitionPolicy.java",
                "SystemUiTransitionProtocol.java",
                "SystemUiTransitionRuntime.java",
                "SystemUiTransitionSource.java",
                "WorkstationCaptureBurst.java",
                "WorkstationWallpaperOnlyHook.java"
        };
        for (String file : retired) {
            assertFalse(file + " should be removed", Files.exists(MAIN.resolve(file)));
        }
    }

    @Test public void zeroCopyFailureCannotFallBackToLegacyGlass() throws Exception {
        String hook = Files.readString(MAIN.resolve("MainHook.java"));
        assertFalse(hook.contains("falling back to legacy pipeline"));
        assertFalse(hook.contains("installLiquidGlassCaptureHooks"));
        assertFalse(hook.contains("installLiquidGlassLayer"));
        assertFalse(hook.contains("DockLiquidGlassView"));
        assertFalse(hook.contains("HomeOwnershipRuntime"));

        String manifest = Files.readString(MANIFEST);
        assertFalse(manifest.contains("com.android.systemui"));
        assertFalse(manifest.contains("FreeformLeashBrokerService"));
    }

    @Test public void retiredCompatibilityToggleCannotRestoreCaptureBackend() throws Exception {
        String reader = Files.readString(MAIN.resolve("ConfigReader.java"));
        assertTrue(reader.contains("ZERO_COPY_PIPELINE_KEY"));
        assertTrue(reader.contains("if (ZERO_COPY_PIPELINE_KEY.equals(key)) return true;"));
    }

    private static void assertNoProductionToken(String token) throws Exception {
        try (var paths = Files.walk(MAIN)) {
            boolean found = paths.filter(path -> path.toString().endsWith(".java"))
                    .anyMatch(path -> {
                        try {
                            return Files.readString(path).contains(token);
                        } catch (Exception error) {
                            throw new RuntimeException(error);
                        }
                    });
            assertFalse("production token should be absent: " + token, found);
        }
    }
}
