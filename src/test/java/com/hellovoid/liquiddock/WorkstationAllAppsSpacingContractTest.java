package com.hellovoid.liquiddock;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Persisted keys stay compatible, while runtime/UI semantics are absolute dp edge spacing. */
public class WorkstationAllAppsSpacingContractTest {
    private static String read(String path) throws IOException {
        Path p = Paths.get(path);
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    @Test
    public void allAppsSpacingAlwaysUsesDensityInsteadOfGridUnitMode() throws IOException {
        String source = read("src/main/java/com/hellovoid/liquiddock/MainHook.java");
        assertTrue(source.contains("float workstationAllAppsScale = android.content.res.Resources.getSystem().getDisplayMetrics().density;"));
        assertTrue(source.contains("allAppsLandscapeHorizontalOffset * workstationAllAppsScale"));
        assertTrue(source.contains("allAppsLandscapeVerticalOffset * workstationAllAppsScale"));
        assertTrue(source.contains("allAppsPortraitHorizontalOffset * workstationAllAppsScale"));
        assertTrue(source.contains("allAppsPortraitVerticalOffset * workstationAllAppsScale"));
    }

    @Test
    public void composeLabelsDescribeSpacingNotOffset() throws IOException {
        String source = read("src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt");
        assertTrue(source.contains("所有应用 · 横屏水平间距"));
        assertTrue(source.contains("所有应用 · 横屏垂直间距"));
        assertTrue(source.contains("所有应用 · 竖屏水平间距"));
        assertTrue(source.contains("所有应用 · 竖屏垂直间距"));
        assertFalse(source.contains("所有应用 · 横屏水平偏移"));
        assertTrue(source.contains("不叠加系统默认位置"));
    }

    @Test
    public void newAbsoluteSpacingCannotBeConfiguredNegative() throws IOException {
        String source = read("src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java");
        assertTrue(source.contains("\"workstation_all_apps_landscape_horizontal_offset\", 0, 0, 0, 0, 240"));
        assertTrue(source.contains("\"workstation_all_apps_landscape_vertical_offset\", 0, 0, 0, 0, 240"));
        assertTrue(source.contains("\"workstation_all_apps_portrait_horizontal_offset\", 0, 0, 0, 0, 240"));
        assertTrue(source.contains("\"workstation_all_apps_portrait_vertical_offset\", 0, 0, 0, 0, 240"));
    }
}
