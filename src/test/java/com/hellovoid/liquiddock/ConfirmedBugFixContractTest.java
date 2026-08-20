package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hellovoid.liquiddock.config.ConfigCodec;
import com.hellovoid.liquiddock.config.ConfigSchema;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class ConfirmedBugFixContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void dpTenthsImportClampsWholeAndSidecarToSchemaRange() {
        Map<String, Object> json = new HashMap<>();
        json.put(ConfigSchema.Dock.WIDTH_OFFSET.name(), 9999.7d);
        json.put(ConfigSchema.Dock.SPACING.name(), -9999.4d);

        Map<String, Object> imported = ConfigCodec.importValues(json);

        assertEquals(80, imported.get(ConfigSchema.Dock.WIDTH_OFFSET.name()));
        assertEquals(800, imported.get(ConfigSchema.Dock.WIDTH_OFFSET.name() + "_tenths"));
        assertEquals(-8, imported.get(ConfigSchema.Dock.SPACING.name()));
        assertEquals(-80, imported.get(ConfigSchema.Dock.SPACING.name() + "_tenths"));
    }

    @Test
    public void viewLifecycleCachesDoNotDefeatWeakKeys() throws Exception {
        String workstation = Files.readString(MAIN.resolve("WorkstationDockGeometryHook.java"));
        String home = Files.readString(MAIN.resolve("HomeGridHook.java"));
        String pipeline = Files.readString(MAIN.resolve("Miuix307MaterialPipeline.java"));
        String glass = Files.readString(MAIN.resolve("MiuixGlassHook.java"));
        String host = Files.readString(MAIN.resolve("DockLiquidGlassHostView.java"));
        String main = Files.readString(MAIN.resolve("MainHook.java"));

        assertTrue(workstation.contains("WeakReference<Binding>"));
        assertTrue(workstation.contains("onViewDetachedFromWindow"));
        assertFalse(workstation.contains("private final View container;"));

        assertTrue(home.contains("class IndicatorPositionGuard"));
        assertTrue(home.contains("WeakReference<android.view.View>"));
        assertTrue(home.contains("removeOnPreDrawListener"));

        assertFalse(pipeline.contains("private static View workspaceRef;"));
        assertFalse(pipeline.contains("private static View observedBackground;"));
        assertFalse(pipeline.contains("private static View observedHost;"));
        assertFalse(pipeline.contains("private static View geometryDeferredLoggedFor;"));

        assertFalse(glass.contains("private static DockLiquidGlassHostView hostRef;"));
        assertFalse(glass.contains("private static View backgroundRef;"));
        assertTrue(glass.contains("onHostDetached"));
        assertTrue(host.contains("MiuixGlassHook.onHostDetached(this)"));

        assertFalse(main.contains("private static View shadowView, oldBg, nativeShadowTarget;"));
        assertTrue(main.contains("WeakReference<View>"));
    }

    @Test
    public void nativeAndTerminalFailureResourcesHaveDeterministicCleanup() throws Exception {
        String bridge = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        assertTrue(bridge.contains("try (SurfaceControl.Transaction transaction"));
        assertTrue(view.contains("requestTerminalShutdown"));
        assertTrue(view.contains("removeGeometryObserver()"));
        assertTrue(view.contains("Miuix307PassBlurBridge.unbind(currentBinding)"));
        assertTrue(view.contains("releaseRenderResources"));
    }

    @Test
    public void settingsUseModernInsetsAndBoundedRootRestart() throws Exception {
        String settings = Files.readString(MAIN.resolve("SettingsActivity.java"));

        assertTrue(settings.contains("WindowInsetsController"));
        assertFalse(settings.contains("setStatusBarColor("));
        assertFalse(settings.contains("SYSTEM_UI_FLAG_LIGHT_STATUS_BAR"));
        assertTrue(settings.contains("waitFor("));
        assertTrue(settings.contains("TimeUnit.SECONDS"));
        assertTrue(settings.contains("Redirect.DISCARD"));
        assertTrue(settings.contains("destroyForcibly"));
    }

    @Test
    public void ipadPresetWritesDpTenthsInsteadOfPixelValues() throws Exception {
        String preset = Files.readString(MAIN.resolve("config/PresetManager.java"));
        assertTrue(preset.contains("putDp(editor, ConfigSchema.Dock.HEIGHT_OFFSET"));
        assertTrue(preset.contains("putDp(editor, ConfigSchema.Dock.WIDTH_OFFSET"));
        assertTrue(preset.contains("putDp(editor, ConfigSchema.Dock.SPACING"));
        assertTrue(preset.contains("putDp(editor, ConfigSchema.Dock.BOTTOM_OFFSET"));
        assertTrue(preset.contains("putDp(editor, ConfigSchema.Dock.SHADOW_RADIUS"));
    }
}
