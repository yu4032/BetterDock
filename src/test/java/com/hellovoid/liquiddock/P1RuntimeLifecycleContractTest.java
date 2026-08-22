package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
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

    @Test
    public void localConfigMigrationPrecedesRemoteReconciliation() throws Exception {
        String app = Files.readString(MAIN.resolve("LiquidDockApp.java"));
        assertTrue("Application startup must migrate the local store before service reconciliation",
                app.contains("ConfigMigration.migrate(this, localPreferences)"));
        assertTrue("local migration must finish before the Xposed service listener can bind",
                app.indexOf("ConfigMigration.migrate(this, localPreferences)")
                        < app.indexOf("XposedServiceHelper.registerListener(this)"));
    }

    @Test
    public void processStartUnitMigrationIsSynchronous() throws Exception {
        String migration = Files.readString(MAIN.resolve("config/ConfigMigration.java"));
        assertFalse("runtime unit migration cannot leave corners pending behind async apply()",
                migration.contains("corners.putBoolean(\"corners_dp\", true).apply()"));
        assertTrue("corner unit migration must be committed before ConfigReader.load()",
                migration.contains("corners.putBoolean(\"corners_dp\", true).commit()"));
    }

    @Test
    public void zeroCopyPipelineRetainsWholeDockShadowOwner() throws Exception {
        String main = Files.readString(MAIN.resolve("MainHook.java"));
        String glass = Files.readString(MAIN.resolve("MiuixGlassHook.java"));

        int shadowInstall = main.indexOf("installDockShadowSetupHook(classLoader)");
        int zeroCopyInstall = main.indexOf("Miuix307MaterialPipeline.install(classLoader, config)");
        assertTrue("whole-Dock shadow setup must be installed before the 307 early return",
                shadowInstall >= 0 && zeroCopyInstall >= 0 && shadowInstall < zeroCopyInstall);
        assertTrue("zero-copy geometry updates must keep the independent whole-Dock shadow synced",
                glass.contains("MainHook.syncDockShadow(dockBg, config.dock)"));
        assertTrue("shadow setup must resolve the active vendor material, not only mBlurBackground2",
                main.contains("getHotSeatsBackground"));
        assertTrue("startup in workstation mode must still hide the ordinary HotSeats material",
                main.contains("if (workstationMode) {\n            dockBg.setAlpha(0f);"));
        assertTrue("theme replacement in the same parent must move the shadow under the active material",
                main.contains("ensureShadowBelowBackground(parent, currentShadow, dockBg);"));
    }

    @Test
    public void wholeDockShadowDoesNotReparentDuringResizeAnimation() throws Exception {
        String main = Files.readString(MAIN.resolve("MainHook.java"));

        assertTrue("transient animation siblings must not force a remove/add of an already-below shadow",
                main.contains("if (shadowIndex < backgroundIndex) return;"));
        assertFalse("shadow/background adjacency is too strict during icon fly-in animation",
                main.contains("shadowIndex + 1 == backgroundIndex"));
        assertTrue("shadow z-order repair must wait until the vendor Dock animation settles",
                main.contains("if (!animating(dockBg)) {\n            ensureShadowBelowBackground"));
        assertTrue("a deliberate shadow reinsert must restore the weak owner cleared by detach",
                main.contains("shadowViewRef = new WeakReference<>(shadow);"));
    }

    @Test
    public void dockBottomOffsetHasOneRuntimeOwner() throws Exception {
        String module = Files.readString(MAIN.resolve("ModuleMain.java"));
        String main = Files.readString(MAIN.resolve("MainHook.java"));
        String pipeline = Files.readString(MAIN.resolve("Miuix307MaterialPipeline.java"));
        String geometry = Files.readString(MAIN.resolve("DockBottomGeometryHook.java"));

        assertFalse("legacy MainHook must not mutate the HotSeats bottom reserve",
                main.contains("getHotSeatsMarginBottom"));
        assertFalse("307 compatibility must not restore the retired bottom-margin owner",
                pipeline.contains("getHotSeatsMarginBottom"));
        assertTrue("DockBottomGeometryHook must remain the sole stock-margin fence",
                geometry.contains("getHotSeatsMarginBottom"));
        assertTrue("visual bottom-offset owner must install after MainHook regardless of 307 return",
                module.indexOf("new MainHook().install(classLoader)")
                        < module.indexOf("DockBottomGeometryHook.install(classLoader)"));
    }
}
