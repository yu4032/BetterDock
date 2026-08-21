package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** The Compose screen is the only settings UI; legacy Preference XML must not survive. */
public class LegacySettingsUiRemovalContractTest {
    @Test public void legacyPreferenceUiIsAbsent() throws Exception {
        String settings = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/SettingsActivity.java"));
        String compose = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));

        assertFalse(settings.contains("PreferenceFragmentCompat"));
        assertFalse(settings.contains("SettingsFragment"));
        assertFalse(settings.contains("useLegacyPreferenceUi"));
        assertFalse(settings.contains("setContentView("));
        assertFalse(settings.contains("R.xml.preferences"));
        assertFalse(compose.contains("useLegacyPreferenceUi"));
        assertFalse(Files.exists(Path.of("src/main/res/xml/preferences.xml")));
        assertFalse(Files.exists(Path.of("src/main/res/layout/activity_settings.xml")));
    }
}
