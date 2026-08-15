package com.hellovoid.liquiddock;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Regression coverage for Floating Dock SurfaceControl recreation and mode-1 EINVAL recovery. */
public class DockSurfaceGenerationRecoveryTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/" + name));
    }

    private static Class<?> requireClass(String simpleName) {
        try {
            return Class.forName("com.hellovoid.liquiddock." + simpleName);
        } catch (ClassNotFoundException e) {
            fail("missing production class " + simpleName);
            return null;
        }
    }

    @Test public void dockLayerIdentityDetectsRecreatedFloatingDockGeneration() throws Exception {
        Class<?> identity = requireClass("DockLayerIdentity");
        Method layerId = identity.getDeclaredMethod("layerId", String.class);
        Method sameGeneration = identity.getDeclaredMethod(
                "sameGeneration", String.class, String.class);
        layerId.setAccessible(true);
        sameGeneration.setAccessible(true);

        assertEquals(3432L, ((Number) layerId.invoke(null, "Floating Dock#3432")).longValue());
        assertEquals(5278L, ((Number) layerId.invoke(null, "Floating Dock#5278")).longValue());
        assertFalse((Boolean) sameGeneration.invoke(
                null, "Floating Dock#3432", "Floating Dock#5278"));
        assertTrue((Boolean) sameGeneration.invoke(
                null, "Floating Dock#5278", "Floating Dock#5278"));
    }

    @Test public void resolverSelectsNewestDockRootInsteadOfFirstType2997Root() throws Exception {
        String glass = source("DockLiquidGlassView.java");
        assertTrue(glass.contains("DockWindowSurfaceSnapshot"));
        assertTrue(glass.contains("DockLayerIdentity.isNewerGeneration"));
        assertTrue(glass.contains("refreshDockWindowSurfaceCache(\"capture\")"));
        assertFalse(glass.contains("if (lp.type == 2997) {\n                        java.lang.reflect.Method getSc"));
    }

    @Test public void validityCheckIncludesLayerGenerationFreshness() throws Exception {
        String glass = source("DockLiquidGlassView.java");
        int start = glass.indexOf("private boolean hasValidDockWindowSurface()");
        int end = glass.indexOf("private boolean refreshDockWindowSurfaceCache", start);
        assertTrue(start >= 0 && end > start);
        String method = glass.substring(start, end);
        assertTrue(method.contains("DockLayerIdentity.sameGeneration"));
        assertFalse(method.contains("return true;"));
    }

    @Test public void firstEInvalidDowngradesSurfaceHandleToNameOnlyAndSecondSuspends() throws Exception {
        Class<?> recovery = requireClass("DockExcludeRecovery");
        Constructor<?> ctor = recovery.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object state = ctor.newInstance();
        Method includeSurface = recovery.getDeclaredMethod("includeSurfaceControl");
        Method suspended = recovery.getDeclaredMethod("suspended");
        Method onInvalid = recovery.getDeclaredMethod("onInvalidArgument");
        Method resetGeneration = recovery.getDeclaredMethod("onSurfaceGenerationChanged");
        includeSurface.setAccessible(true);
        suspended.setAccessible(true);
        onInvalid.setAccessible(true);
        resetGeneration.setAccessible(true);

        assertTrue((Boolean) includeSurface.invoke(state));
        onInvalid.invoke(state);
        assertFalse((Boolean) includeSurface.invoke(state));
        assertFalse((Boolean) suspended.invoke(state));
        onInvalid.invoke(state);
        assertTrue((Boolean) suspended.invoke(state));
        resetGeneration.invoke(state);
        assertTrue((Boolean) includeSurface.invoke(state));
        assertFalse((Boolean) suspended.invoke(state));
    }

    @Test public void asyncStatusMinus22IsPreservedForRecoveryDecision() throws Exception {
        String live = source("LiveScreenCapture.java");
        assertTrue(live.contains("CaptureStatusException"));
        assertTrue(live.contains("new CaptureStatusException(status)"));
        assertTrue(live.contains("isInvalidArgumentStatus"));
    }

    @Test public void eInvalidRecoveryNeverUsesUnexcludedFullDisplay() throws Exception {
        String glass = source("DockLiquidGlassView.java");
        assertTrue(glass.contains("dock-exclude-einval-name-only"));
        assertTrue(glass.contains("dockExcludeRecovery.includeSurfaceControl()"));
        String live = source("LiveScreenCapture.java");
        assertTrue(live.contains("names.add(\"Floating Dock\")"));
        assertFalse(glass.contains("dock-exclude-einval-no-exclude"));
    }

    @Test public void moduleSettingsForegroundHardStopsFullDisplayCapture() throws Exception {
        String glass = source("DockLiquidGlassView.java");
        String controller = source("LauncherSceneController.java");
        assertTrue(glass.contains("moduleSettingsForeground"));
        assertTrue(glass.contains("void setModuleSettingsForeground(boolean foreground)"));
        assertTrue(glass.contains("if (moduleSettingsForeground) return false;"));
        assertTrue(controller.contains("MODULE_PACKAGE"));
        assertTrue(controller.contains("setModuleSettingsForeground"));
    }
}
