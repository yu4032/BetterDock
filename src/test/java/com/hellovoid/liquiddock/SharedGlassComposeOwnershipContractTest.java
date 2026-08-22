package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Prevents the legacy compressed shared-glass patch from reclaiming the modern Compose GUI. */
public class SharedGlassComposeOwnershipContractTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    @Test
    public void sharedGlassWorkflowsExcludeLegacyComposeHunk() throws Exception {
        String wallpaper = read(".github/workflows/wallpaper-only-ci.yml");
        String shared = read(".github/workflows/shared-launcher-glass-ci.yml");
        String exclusion = "git apply --check --exclude=\"$COMPOSE\" /tmp/shared-launcher-glass.patch";
        assertTrue(wallpaper.contains(exclusion));
        assertTrue(shared.contains(exclusion));
        assertTrue(wallpaper.contains("python3 ci/shared_glass_compose_ui_transform.py"));
        assertTrue(shared.contains("python3 ci/shared_glass_compose_ui_transform.py"));
    }

    @Test
    public void materializedSurfaceSwitchesUseSchemaAndLocalizedResources() throws Exception {
        String transform = read("ci/shared_glass_compose_ui_transform.py");
        String english = read("src/main/res/values/strings.xml");
        String chinese = read("src/main/res/values-zh-rCN/strings.xml");

        assertTrue(transform.contains("ConfigSchema.Glass.FOLDER_ENABLED"));
        assertTrue(transform.contains("ConfigSchema.Glass.WIDGET_ENABLED"));
        assertTrue(transform.contains("R.string.liquid_folder_glass_title"));
        assertTrue(transform.contains("R.string.liquid_widget_glass_title"));
        assertTrue(english.contains("name=\"liquid_folder_glass_title\""));
        assertTrue(english.contains("name=\"liquid_widget_glass_title\""));
        assertTrue(chinese.contains("name=\"liquid_folder_glass_title\""));
        assertTrue(chinese.contains("name=\"liquid_widget_glass_title\""));
    }
}
