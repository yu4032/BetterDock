package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Ensures device diagnostics are visible even when LiquidDock debugLog is disabled. */
public class AlwaysOnDiagnosticTraceContractTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/" + name), StandardCharsets.UTF_8);
    }

    @Test public void launcherProbeBypassesMainHookDebugLogGate() throws Exception {
        String probe = source("AlwaysOnDiagnosticTrace.java");
        assertTrue(probe.contains("Api101Bridge.log(\"[DC][TR] launcher always-on diagnostics installed\")"));
        assertTrue(probe.contains("Api101Bridge.log(\"[DC][TR] provider changed connected=\""));
        assertTrue(probe.contains("Api101Bridge.log(\"[DC][TR] callback received type=\""));
        assertTrue(probe.contains("Api101Bridge.log(\"[DC][TR-RX] onTransact code=\""));
        assertTrue(probe.contains("Api101Bridge.log(\"[DC][DRAG] vendor finish enter object=\""));
        assertTrue(probe.contains("Api101Bridge.log(\"[DC][DRAG] finish capture glass=\""));
    }

    @Test public void moduleMainInstallsAlwaysOnLauncherProbe() throws Exception {
        String module = source("ModuleMain.java");
        assertTrue(module.contains("AlwaysOnDiagnosticTrace.installLauncher(classLoader);"));
    }
}
