package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Locks the requirements that were intentionally left after the folder-runtime milestone. */
public class RemainingRequirementsContractTest {
    private static final Path ROOT = Path.of("");
    private static final Path MAIN = ROOT.resolve("src/main/java/com/hellovoid/liquiddock");
    private static final Path UI = ROOT.resolve("src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt");

    private static String read(Path path) throws Exception {
        return Files.readString(path);
    }

    private static String listBlock(String source, String name) {
        int start = source.indexOf("private val " + name + " = listOf(");
        if (start < 0) return "";
        int next = source.indexOf("\nprivate val ", start + 1);
        return next < 0 ? source.substring(start) : source.substring(start, next);
    }

    @Test
    public void cornerOffsetLivesOnStrokePageButBlurCornerStaysOnDockPage() throws Exception {
        String ui = read(UI);
        String dock = listBlock(ui, "dockSpecs");
        String stroke = listBlock(ui, "strokeSpecs");
        assertFalse(dock.contains("ConfigSchema.Dock.CORNER_OFFSET"));
        assertTrue(dock.contains("ConfigSchema.Dock.BLUR_CORNER_OFFSET"));
        assertTrue(stroke.contains("ConfigSchema.Dock.CORNER_OFFSET"));
        assertTrue(stroke.contains("IntSection.StrokeGeometry"));
    }

    @Test
    public void liquidPageUsesOnlyUserFacingFunctionalLanguage() throws Exception {
        String ui = read(UI);
        assertFalse(ui.contains("Prismal ·"));
        assertFalse(ui.contains("PassBlur → OES → Prismal zero-copy"));
        assertFalse(ui.contains("Launcher Prismal"));
        assertFalse(ui.contains("zero-copy 后端"));
        assertFalse(ui.contains("双通道 FBO"));
        assertFalse(ui.contains("v1.0.6 Quick Start"));
        int summariesStart = ui.indexOf("private fun optionSummary");
        int summariesEnd = ui.indexOf("private val gridSpecs", summariesStart);
        assertTrue(summariesStart >= 0 && summariesEnd > summariesStart);
        String summaries = ui.substring(summariesStart, summariesEnd);
        assertFalse("Liquid setting descriptions must describe effects, not the Prismal implementation",
                summaries.contains("Prismal"));
        assertTrue(ui.contains("显示表面法线（调试）"));
    }

    @Test
    public void liquidPageLinksToFolderAndWidgetHighlightControls() throws Exception {
        String ui = read(UI);
        assertTrue(ui.contains("LauncherHighlights(R.string.page_launcher_highlights)"));
        assertTrue(ui.contains("Page.LauncherHighlights -> LauncherHighlightsPage"));
        assertTrue(ui.contains("openLauncherHighlights: () -> Unit"));
        assertTrue(ui.contains("R.string.launcher_highlights_entry"));
    }

    @Test
    public void launcherHighlightPreferencesDoNotDefineDockPreferences() throws Exception {
        Path prefsPath = MAIN.resolve("LauncherHighlightPreferences.java");
        assertTrue(Files.exists(prefsPath));
        String prefs = read(prefsPath);
        assertTrue(prefs.contains("launcher_surface_component_sky_haze"));
        assertTrue(prefs.contains("launcher_surface_component_specular"));
        assertTrue(prefs.contains("launcher_surface_component_lit_rim"));
        assertTrue(prefs.contains("launcher_surface_component_opposite_rim"));
        assertTrue(prefs.contains("launcher_surface_component_corner_rim"));
        assertTrue(prefs.contains("launcher_surface_component_face_sheen"));
        assertTrue(prefs.contains("launcher_surface_component_plain_highlight"));
        assertTrue(prefs.contains("launcher_surface_component_caustics"));
        assertTrue(prefs.contains("launcher_surface_component_press_glow"));
        assertFalse(prefs.contains("prismal_dock_component"));
        assertFalse(prefs.contains("compact_safe"));
    }

    @Test
    public void folderRuntimeReceivesAProfileWithoutChangingDockOwnership() throws Exception {
        String config = read(MAIN.resolve("LiquidDockConfig.java"));
        String session = read(MAIN.resolve("LauncherGlassSession.java"));
        assertTrue(config.contains("launcherHighlightProfile"));
        assertTrue(config.contains("LauncherHighlightPreferences.read(c)"));
        assertTrue(session.contains("launcherHighlightProfile"));
        assertTrue(session.contains("prismalRenderer.drawGlass("));
        assertTrue(session.contains("launcherHighlightProfile"));

        String[] protectedOwners = {
                "Miuix307MaterialPipeline.java",
                "MiuixGlassHook.java",
                "Miuix307ZeroCopyRenderer.java",
                "WorkstationDockGeometryHook.java"
        };
        for (String owner : protectedOwners) {
            String source = read(MAIN.resolve(owner));
            assertFalse(owner + " must not read Launcher highlight preferences",
                    source.contains("LauncherHighlightPreferences"));
            assertFalse(owner + " must not read Launcher surface component keys",
                    source.contains("launcher_surface_component_"));
        }
    }
}
