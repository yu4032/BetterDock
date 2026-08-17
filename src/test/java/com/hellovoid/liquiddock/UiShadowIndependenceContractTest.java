package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** UI/runtime contracts for workstation naming and shadow ownership. */
public class UiShadowIndependenceContractTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    @Test
    public void firstLevelWorkstationLabelDropsDockSuffix() throws Exception {
        String zh = read("src/main/res/values-zh-rCN/strings.xml");
        String en = read("src/main/res/values/strings.xml");
        String ui = read("src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt");

        assertTrue(zh.contains("<string name=\"home_workstation_title\">工作台</string>"));
        assertTrue(en.contains("<string name=\"home_workstation_title\">Workstation</string>"));
        assertTrue("only the first-level item should use the shorter title",
                ui.contains("ArrowPreference(stringResource(R.string.home_workstation_title)"));
    }

    @Test
    public void shadowSettingsDoNotDependOnDockSizeAndBlurSwitch() throws Exception {
        String ui = read("src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt");
        int start = ui.indexOf("private fun ShadowPage");
        int end = ui.indexOf("private fun DataPage", start);
        assertTrue(start >= 0 && end > start);
        String shadowPage = ui.substring(start, end);

        assertFalse("shadow page must not read the Dock size/blur master",
                shadowPage.contains("ConfigSchema.Dock.ENABLED"));
        assertFalse("shadow controls must not be gated by dockEnabled",
                shadowPage.contains("dockEnabled"));
        assertTrue("whole-Dock shadow remains gated by its own switch",
                shadowPage.contains("\"dock_shadow\" -> dockShadow"));
        assertTrue("stroke shadow remains gated by its own switch",
                shadowPage.contains("\"stroke_shadow\" -> strokeShadow"));
    }

    @Test
    public void dockSizeAndBlurSummaryDoesNotClaimStrokeOrShadowOwnership() throws Exception {
        String zh = read("src/main/res/values-zh-rCN/strings.xml");
        assertTrue(zh.contains("描边和阴影独立设置"));
    }

    @Test
    public void wholeDockShadowHasRuntimePathWhenDockCustomizationIsDisabled() throws Exception {
        String entry = read("src/main/java/com/hellovoid/liquiddock/ModuleMain.java");
        String shadow = read("src/main/java/com/hellovoid/liquiddock/DockShadowIndependenceHook.java");

        assertTrue("launcher entry must install independent shadow ownership",
                entry.contains("DockShadowIndependenceHook.install(classLoader)"));
        assertTrue("the compatibility hook is specifically active when Dock customization is off",
                shadow.contains("if (config.dock.enabled) return;"));
        assertTrue("global LiquidDock master must remain authoritative",
                shadow.contains("if (!config.enabled) return;"));
    }

    @Test
    public void wholeDockShadowUsesOwnDpScaleAndStrokeRadiusBasis() throws Exception {
        String shadow = read("src/main/java/com/hellovoid/liquiddock/DockShadowIndependenceHook.java");

        assertTrue("shadow radius/size/Y are dp settings and must always use density",
                shadow.contains("float shadowScale = background.getResources().getDisplayMetrics().density;"));
        assertTrue("shadow contour must use the same configured/native radius basis as stroke",
                shadow.contains("DockStrokeRenderer.resolveConfiguredRadius("));
        assertTrue("workstation must suppress the ordinary Dock shadow",
                shadow.contains("MainHook.isWorkstationMode()"));
    }
}
