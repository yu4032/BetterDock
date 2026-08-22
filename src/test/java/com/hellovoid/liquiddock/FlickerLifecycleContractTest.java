package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Regression contracts for the device-observed Dock flicker/master-disable lifecycle. */
public class FlickerLifecycleContractTest {
    private static final Path JAVA = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void masterDisabledReturnsBeforeAnyRuntimeHookInstallation() throws Exception {
        String module = Files.readString(JAVA.resolve("ModuleMain.java"));
        int load = module.indexOf("LiquidDockConfig runtimeConfig = LiquidDockConfig.from(configReader);");
        int masterGate = module.indexOf("if (!runtimeConfig.enabled)");
        int firstHook = module.indexOf("new MainHook().install(classLoader);");
        assertTrue("ModuleMain must gate the entire injected runtime after loading config",
                load >= 0 && masterGate > load && firstHook > masterGate);
    }

    @Test
    public void changingMasterSwitchSynchronizesThenRestartsLauncher() throws Exception {
        String app = Files.readString(JAVA.resolve("LiquidDockApp.java"));
        assertTrue("the central preference bridge must recognize the master key",
                app.contains("ConfigSchema.Core.ENABLED.name().equals(key)"));
        assertTrue("master changes must use a synchronous Remote Preferences write",
                app.contains("syncKeyToRemote(key, sharedPreferences, masterChange)"));
        int sync = app.indexOf("syncKeyToRemote(key, sharedPreferences, masterChange)");
        int restart = app.indexOf("restartLauncherAfterMasterChange()", sync);
        assertTrue("Launcher restart must happen only after successful remote synchronization",
                sync >= 0 && restart > sync);
    }

    @Test
    public void zeroCopyDoesNotRewriteVendorCompositorStateEveryPreDraw() throws Exception {
        String glass = Files.readString(JAVA.resolve("MiuixGlassHook.java"));
        assertFalse("zero-copy must not install a perpetual pre-draw compositor suppressor",
                glass.contains("addOnPreDrawListener("));
    }
}
