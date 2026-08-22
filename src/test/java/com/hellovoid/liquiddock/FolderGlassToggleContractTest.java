package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Contracts the independent GUI/runtime switch for Launcher folder glass. */
public class FolderGlassToggleContractTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    public void folderGlassHasIndependentDefaultOnConfigAndRuntimeGate() throws Exception {
        String schema = read("src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java");
        String runtime = read("src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java");
        String hook = read("src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java");

        assertTrue(schema.contains("FOLDER_GLASS = bool(\n                \"liquid_folder_glass\", true, true, true"));
        assertTrue(schema.contains("Glass.ENABLED, Glass.FOLDER_GLASS"));
        assertTrue(runtime.contains("final boolean enabled, folderEnabled;"));
        assertTrue(runtime.contains("folderEnabled = c.b(ConfigSchema.Glass.FOLDER_GLASS.name()"));
        assertTrue(hook.contains("!runtimeConfig.glass.folderEnabled"));
    }

    @Test
    public void liquidSettingsExposeFolderGlassSwitchUnderMasterGlassSwitch() throws Exception {
        String ui = read("src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt");
        String strings = read("src/main/res/values/strings.xml");

        assertTrue(ui.contains("BooleanSetting(prefs, ConfigSchema.Glass.FOLDER_GLASS"));
        assertTrue(ui.contains("R.string.liquid_folder_glass_enable"));
        assertTrue(ui.contains("masterEnabled && liquidGlass"));
        assertTrue(strings.contains("name=\"liquid_folder_glass_enable\">Enable folder glass<"));
        assertTrue(strings.contains("name=\"liquid_folder_glass_enable_summary\""));
    }
}
