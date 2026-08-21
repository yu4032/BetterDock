package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression contract for restarting MIUI Home as the HOME activity, not a standard task. */
public class LauncherRestartContractTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/com/hellovoid/liquiddock/SettingsActivity.java");

    @Test public void restartUsesHomeIntentInsteadOfDirectLauncherComponent() throws Exception {
        String source = Files.readString(SOURCE);
        assertTrue(source.contains("android.intent.action.MAIN"));
        assertTrue(source.contains("android.intent.category.HOME"));
        assertFalse(source.contains("am start -n com.miui.home/.launcher.Launcher"));
    }
}
