package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Guards independent Launcher/Dock component controls and their localized secondary pages. */
public class PrismalComponentGuiContractTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    @Test
    public void componentDefaultsPreserveVerifiedLauncherAndDockVisuals() throws Exception {
        Path path = Path.of("src/main/java/com/hellovoid/liquiddock/PrismalComponentPreferences.java");
        assertTrue("component preference contract must exist", Files.exists(path));
        String source = read(path.toString());

        String[] launcherOff = {
                "LAUNCHER_SKY_HAZE", "LAUNCHER_SPECULAR", "LAUNCHER_LIT_RIM",
                "LAUNCHER_OPPOSITE_RIM", "LAUNCHER_CORNER_RIM", "LAUNCHER_FACE_SHEEN",
                "LAUNCHER_PLAIN_HIGHLIGHT", "LAUNCHER_CAUSTICS", "LAUNCHER_PRESS_GLOW"
        };
        for (String key : launcherOff) {
            assertTrue(key + " must default off", source.contains(key + ", false"));
        }
        assertTrue("compact-safe edge highlight must preserve the verified launcher baseline",
                source.contains("LAUNCHER_COMPACT_SAFE_HIGHLIGHT, true"));

        String[] dockOn = {
                "DOCK_SKY_HAZE", "DOCK_SPECULAR", "DOCK_LIT_RIM", "DOCK_OPPOSITE_RIM",
                "DOCK_CORNER_RIM", "DOCK_FACE_SHEEN", "DOCK_PLAIN_HIGHLIGHT",
                "DOCK_CAUSTICS", "DOCK_PRESS_GLOW"
        };
        for (String key : dockOn) {
            assertTrue(key + " must preserve the current Dock baseline",
                    source.contains(key + ", true"));
        }
    }

    @Test
    public void composeGuiUsesIndependentLocalizedSecondaryPages() throws Exception {
        String compose = read("src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt");
        String english = read("src/main/res/values/strings.xml");
        String chinese = read("src/main/res/values-zh-rCN/strings.xml");

        assertTrue(compose.contains("LauncherComponents(R.string.page_launcher_components)"));
        assertTrue(compose.contains("DockComponents(R.string.page_dock_components)"));
        assertTrue(compose.contains("LauncherComponentPage("));
        assertTrue(compose.contains("DockComponentPage("));
        assertTrue("secondary back navigation must return to Liquid rather than Home",
                compose.contains("Page.LauncherComponents -> Page.Liquid"));
        assertTrue("secondary back navigation must return to Dock rather than Home",
                compose.contains("Page.DockComponents -> Page.Dock"));

        String[] resources = {
                "page_launcher_components", "page_dock_components", "optics_components_summary",
                "component_sky_haze", "component_specular", "component_lit_rim",
                "component_opposite_rim", "component_corner_rim", "component_face_sheen",
                "component_plain_highlight", "component_caustics", "component_press_glow",
                "component_compact_safe_highlight"
        };
        for (String name : resources) {
            assertTrue("missing English resource " + name, english.contains("name=\"" + name + "\""));
            assertTrue("missing Chinese resource " + name, chinese.contains("name=\"" + name + "\""));
        }

        assertFalse("Liquid page runtime description must no longer claim every surface uses PassBlur",
                compose.contains("当前使用 PassBlur → OES → Prismal zero-copy"));
    }
}
