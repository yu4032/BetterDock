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

        assertTrue(zh.contains("<string name=\"page_workstation\">工作台</string>"));
        assertTrue(en.contains("<string name=\"page_workstation\">Workstation</string>"));
        assertFalse(zh.contains("<string name=\"page_workstation\">工作台 Dock</string>"));
        assertFalse(en.contains("<string name=\"page_workstation\">Workstation Dock</string>"));
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
    public void wholeDockShadowHooksInstallBeforeDockCustomizationEarlyReturn() throws Exception {
        String main = read("src/main/java/com/hellovoid/liquiddock/MainHook.java");
        int shadowInstall = main.indexOf("installDockShadowHooks(classLoader, config.dock)");
        int dockCustomization = main.indexOf("boolean dockCustomization = config.dock.enabled;");

        assertTrue("shadow ownership must initialize before dock_customization is consulted",
                shadowInstall >= 0 && dockCustomization > shadowInstall);
    }

    @Test
    public void wholeDockShadowUsesOwnDpScaleAndStrokeRadiusBasis() throws Exception {
        String main = read("src/main/java/com/hellovoid/liquiddock/MainHook.java");
        int start = main.indexOf("private static void installDockShadowHooks");
        int end = main.indexOf("private static DockLiquidGlassView installLiquidGlassLayer", start);
        assertTrue(start >= 0 && end > start);
        String shadowHooks = main.substring(start, end);

        assertTrue("shadow radius/size/Y are dp settings and must always use density",
                shadowHooks.contains("getDisplayMetrics().density"));
        assertFalse("shadow settings must not inherit dock_dimensions_dp",
                shadowHooks.contains("dimensionsDp ?"));
        assertTrue("shadow contour must use the same configured/native radius basis as stroke",
                shadowHooks.contains("DockStrokeRenderer.resolveConfiguredRadius("));
    }
}
