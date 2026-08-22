package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Regression contracts for the device-observed Dock flicker/master-disable lifecycle. */
public class FlickerLifecycleContractTest {
    private static final Path JAVA = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path KOTLIN = Path.of("src/main/kotlin/com/hellovoid/liquiddock");

    @Test
    public void masterDisabledReturnsBeforeAnyRuntimeHookInstallation() throws Exception {
        String module = Files.readString(JAVA.resolve("ModuleMain.java"));
        int load = module.indexOf("LiquidDockConfig runtimeConfig = LiquidDockConfig.from(configReader);");
        int masterGate = module.indexOf("if (!runtimeConfig.enabled) return;");
        int firstHook = module.indexOf("new MainHook().install(classLoader);");
        assertTrue("ModuleMain must gate the entire injected runtime after loading config",
                load >= 0 && masterGate > load && firstHook > masterGate);

        String main = Files.readString(JAVA.resolve("MainHook.java"));
        int localLoad = main.indexOf("LiquidDockConfig config = LiquidDockConfig.load();");
        int localGate = main.indexOf("if (!config.enabled)");
        int workstationHook = main.indexOf("installWorkstationModeGuard(classLoader);");
        assertTrue("MainHook direct-call safety must gate even the workstation hook",
                localLoad >= 0 && localGate > localLoad && workstationHook > localGate);
    }

    @Test
    public void changingMasterSwitchRestartsLauncherWithSyncedRemoteState() throws Exception {
        String compose = Files.readString(KOTLIN.resolve("ComposeSettingsActivity.kt"));
        assertTrue("master switch callback must restart Launcher so process-start hooks are removed",
                compose.contains("onMasterChanged = { enabled ->")
                        && compose.contains("activity.restartLauncher()"));

        String settings = Files.readString(JAVA.resolve("SettingsActivity.java"));
        assertTrue("restartLauncher must abort rather than restart with stale Remote Preferences",
                settings.contains("if (!LiquidDockApp.syncToRemote("));
        assertTrue("sync failure must be surfaced to the user",
                settings.contains("Remote Preferences") && settings.contains("return;"));
    }

    @Test
    public void zeroCopyDoesNotRewriteVendorCompositorStateEveryPreDraw() throws Exception {
        String glass = Files.readString(JAVA.resolve("MiuixGlassHook.java"));
        int preDraw = glass.indexOf("ViewTreeObserver.OnPreDrawListener listener");
        if (preDraw >= 0) {
            int end = glass.indexOf("observer.addOnPreDrawListener(listener);", preDraw);
            String listenerBody = end > preDraw ? glass.substring(preDraw, end) : glass.substring(preDraw);
            assertFalse("per-frame pre-draw must not repeatedly submit vendor blur suppression",
                    listenerBody.contains("suppressVendorGpuBlur(background)"));
            assertFalse("per-frame pre-draw must not repeatedly rewrite the vendor material body",
                    listenerBody.contains("suppressVendorMaterialBody(background"));
        }
    }
}
