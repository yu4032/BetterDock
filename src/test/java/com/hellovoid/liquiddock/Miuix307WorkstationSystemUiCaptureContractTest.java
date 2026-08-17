package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contracts for capture ownership skipped by the MiuiX 307 early-return path. */
public class Miuix307WorkstationSystemUiCaptureContractTest {
    private static String read(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/" + file));
    }

    @Test
    public void miuix307RestoresSystemUiPanelCaptureGate() throws Exception {
        String pipeline = read("Miuix307MaterialPipeline.java");
        String glassHook = read("MiuixGlassHook.java");

        assertTrue("307 install must restore DeviceConfig panel-state compatibility",
                pipeline.contains("installSystemUiPanelStateHook(classLoader)"));
        assertTrue("307 must observe the device-proven panel expansion setter",
                pipeline.contains("\"setControlPanelExpanded\""));
        assertTrue("307 panel state must be forwarded to its actual bound glass",
                pipeline.contains("MiuixGlassHook.setSystemUiPanelExpanded(expanded)"));
        assertTrue("MiuixGlassHook must forward panel state into DockLiquidGlassView",
                glassHook.contains("glass.setSystemUiPanelExpanded(expanded)"));
    }

    @Test
    public void workstationModeReachesMiuix307OwnedGlass() throws Exception {
        String main = read("MainHook.java");
        String glassHook = read("MiuixGlassHook.java");

        int setter = main.indexOf("private static void setWorkstationMode(boolean enabled)");
        int next = main.indexOf("\n    private static void ", setter + 1);
        String body = next > setter ? main.substring(setter, next) : main.substring(setter);

        assertTrue("Main workstation transition must notify the 307-owned glass",
                body.contains("MiuixGlassHook.setWorkstationMode(enabled)"));
        assertTrue("MiuixGlassHook must forward workstation state to DockLiquidGlassView",
                glassHook.contains("glass.setWorkstationMode(enabled)"));
    }

    @Test
    public void workstationLiveScenesStayFullDisplayMode1() throws Exception {
        String policy = read("CaptureSourcePolicy.java");

        int method = policy.indexOf("sourceForWorkstationScene");
        String body = policy.substring(method);
        assertTrue("workstation Recents must remain composed FULL_DISPLAY",
                body.contains("scene == CaptureScene.RECENTS")
                        && body.contains("Source.FULL_DISPLAY"));
        assertTrue("workstation All Apps must also use FULL_DISPLAY for one exclusion model",
                body.contains("scene == CaptureScene.ALL_APPS")
                        && body.indexOf("scene == CaptureScene.ALL_APPS")
                        < body.lastIndexOf("Source.FULL_DISPLAY"));
        assertFalse("workstation live scenes must not depend on LOCAL_LAYER fallback",
                body.contains("Source.LOCAL_LAYER"));
    }

    @Test
    public void workstationFullDisplayUsesDedicatedNonFloatingDockExclusion() throws Exception {
        String view = read("DockLiquidGlassView.java");

        assertTrue("workstation needs a dedicated resolver, not the ordinary Floating Dock resolver",
                view.contains("resolveWorkstationDockExclusions()"));
        assertTrue("workstation resolver must identify DockContainerView ownership",
                view.contains("DockContainerView"));
        assertTrue("ordinary type-2997 Floating Dock must be rejected as a workstation candidate",
                view.contains("lp.type == 2997") && view.contains("continue;"));
        assertTrue("workstation mode-1 must build excludes from the dedicated workstation result",
                view.contains("workstationDockExclusions.surfaceControls")
                        && view.contains("workstationDockExclusions.layerNames"));
        assertFalse("workstation must never downgrade an unresolved live scene to wallpaper",
                view.contains("wallpaper is preferable to sampling icons"));
    }
}
