package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Regression contracts for P1 runtime lifecycle/ownership bugs found in the main-branch audit. */
public class P1RuntimeLifecycleContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void modernConfigMigrationRunsBeforeRuntimeSnapshot() throws Exception {
        String module = Files.readString(MAIN.resolve("ModuleMain.java"));
        String migration = Files.readString(MAIN.resolve("config/ConfigMigration.java"));

        assertTrue("ModuleMain must invoke modern config migration at launcher process start",
                module.contains("ConfigMigration.migrateAtProcessStart()"));
        assertTrue("modern migration must run before ConfigReader snapshots Remote Preferences",
                module.indexOf("ConfigMigration.migrateAtProcessStart()")
                        < module.indexOf("ConfigReader.load()"));
        assertTrue("ConfigMigration must expose an injected-process migration entry point",
                migration.contains("public static void migrateAtProcessStart()"));
        assertTrue("runtime migration must operate on API101 Remote Preferences",
                migration.contains("Api101Bridge.remotePreferences(ConfigReader.REMOTE_GROUP)"));
    }
}
