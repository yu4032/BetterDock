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
        String entry = read("ModuleMain.java");
        String ownership = read("Miuix307CaptureOwnershipHook.java");

        assertTrue("launcher init must install the 307 capture ownership bridge",
                entry.contains("Miuix307CaptureOwnershipHook.install(classLoader)"));
        assertTrue("307 must observe the device-proven panel expansion setter",
                ownership.contains("\"setControlPanelExpanded\""));
        assertTrue("panel state must reach the actually bound 307 glass",
                ownership.contains("glass.setSystemUiPanelExpanded(expanded)"));
        assertTrue("a newly bound 307 glass must inherit an already-open panel state",
                ownership.contains("syncBoundGlassState()"));
    }

    @Test
    public void workstationModeReachesMiuix307OwnedGlass() throws Exception {
        String ownership = read("Miuix307CaptureOwnershipHook.java");

        assertTrue("307 must bridge MainHook workstation transitions after the early return",
                ownership.contains("\"setWorkstationMode\"")
                        && ownership.contains("MainHook.class"));
        assertTrue("workstation state must reach the actually bound 307 glass",
                ownership.contains("glass.setWorkstationMode(enabled)"));
        assertTrue("freshly composed 307 glass must inherit current workstation mode",
                ownership.contains("MainHook.isWorkstationMode()"));
    }

    @Test
    public void workstationKnownScenesStayFullDisplayMode1() throws Exception {
        String policy = read("CaptureSourcePolicy.java");

        int method = policy.indexOf("sourceForWorkstationScene");
        String body = policy.substring(method);
        assertTrue("only unknown workstation ownership may stay non-live",
                body.contains("scene == null || scene == CaptureScene.UNKNOWN"));
        assertTrue("every known workstation scene must converge on composed FULL_DISPLAY",
                body.contains("return Source.FULL_DISPLAY;"));
        assertFalse("workstation live scenes must not depend on LOCAL_LAYER fallback",
                body.contains("Source.LOCAL_LAYER"));
    }

    @Test
    public void workstationFullDisplayUsesDedicatedNonFloatingDockExclusion() throws Exception {
        String ownership = read("Miuix307CaptureOwnershipHook.java");

        assertTrue("workstation needs a dedicated runtime resolver",
                ownership.contains("resolveWorkstationDockTarget()"));
        assertTrue("workstation resolver must identify DockContainerView ownership",
                ownership.contains("DockContainerView"));
        assertTrue("ordinary type-2997 Floating Dock must be rejected as a workstation candidate",
                ownership.contains("lp.type == 2997") && ownership.contains("continue;"));
        assertTrue("ordinary Floating Dock title must also be rejected",
                ownership.contains("\"Floating Dock\"") && ownership.contains("continue;"));
        assertTrue("resolved workstation SurfaceControl must replace the normal Dock exclusion",
                ownership.contains("HookUtil.setField(glass, \"dockWindowSurface\", target.surface)")
                        && ownership.contains("HookUtil.setField(glass, \"dockWindowLayerName\", target.layerName)"));
        assertTrue("an unresolved workstation Dock must fail closed before capture submission",
                ownership.contains("workstation Dock surface unresolved; capture remains frozen")
                        && ownership.contains("return null;"));
        assertFalse("dedicated workstation resolver must not use wallpaper as a safety fallback",
                ownership.contains("captureWallpaper") || ownership.contains("mode 2"));
    }
}
