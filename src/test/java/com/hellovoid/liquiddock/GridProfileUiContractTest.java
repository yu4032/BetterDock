package com.hellovoid.liquiddock;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** UI contract: one optional-layout master plus one 8x4/10x6 selector. */
public class GridProfileUiContractTest {
    private static String read(String path) throws IOException {
        return Files.readString(Paths.get(path), StandardCharsets.UTF_8);
    }

    @Test
    public void composeUsesMasterSwitchAndTwoOptionProfileSelector() throws IOException {
        String source = read("src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt");
        assertTrue(source.contains("GridProfileConfig.ENABLED_KEY"));
        assertTrue(source.contains("GridProfileConfig.PROFILE_KEY"));
        assertTrue(source.contains("\"8×4 / 4×8\" to \"8x4\""));
        assertTrue(source.contains("\"10×6 / 6×10\" to \"10x6\""));
        assertTrue(source.contains("masterEnabled && extendedGrid"));
        assertFalse(source.contains("BooleanSetting(prefs, ConfigSchema.Grid.ENABLED"));
    }

    @Test
    public void chineseCopyMatchesRequestedControlModel() throws IOException {
        String source = read("src/main/res/values-zh-rCN/strings.xml");
        assertTrue(source.contains(">可选扩展布局</string>"));
        assertTrue(source.contains(">扩展布局规格</string>"));
        assertTrue(source.contains("8×4"));
        assertTrue(source.contains("10×6"));
    }

    @Test
    public void legacyPreferenceScreenUsesCanonicalMasterAndList() throws IOException {
        String source = read("src/main/res/xml/preferences.xml");
        assertTrue(source.contains("android:key=\"home_grid_extended\""));
        assertTrue(source.contains("<ListPreference"));
        assertTrue(source.contains("android:key=\"grid_profile\""));
        assertFalse(source.contains("android:key=\"home_grid_8x4\""));
    }

    @Test
    public void canonicalKeysHaveSingleDedicatedOwner() throws IOException {
        String source = read("src/main/java/com/hellovoid/liquiddock/config/GridProfileConfig.java");
        assertTrue(source.contains("ENABLED_KEY = \"home_grid_extended\""));
        assertTrue(source.contains("PROFILE_KEY = \"grid_profile\""));
        assertTrue(source.contains("DEFAULT_PROFILE = \"8x4\""));
    }
}
