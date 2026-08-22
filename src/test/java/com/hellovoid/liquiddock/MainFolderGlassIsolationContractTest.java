package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/**
 * Protects main's Dock/workstation ownership while the Launcher folder glass path is rebuilt.
 */
public class MainFolderGlassIsolationContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    private static String read(String name) throws Exception {
        return Files.readString(MAIN.resolve(name), StandardCharsets.UTF_8);
    }

    @Test
    public void currentDockAndWorkstationOwnershipRemainsTheMainPath() throws Exception {
        String module = read("ModuleMain.java");
        String pipeline = read("Miuix307MaterialPipeline.java");
        String glass = read("MiuixGlassHook.java");
        String zeroCopy = read("Miuix307ZeroCopyRenderer.java");

        assertTrue(module.contains("new MainHook().install(classLoader);"));
        assertTrue(pipeline.contains("installWorkstationResumeProducerRecovery(classLoader);"));
        assertTrue(glass.contains("Miuix307ZeroCopyRenderer.clear();"));
        assertTrue(zeroCopy.contains("new Miuix307PassBlurTextureView("));

        assertFalse(pipeline.contains("MiuixFolderGlassHook"));
        assertFalse(glass.contains("MiuixFolderGlassHook"));
        assertFalse(zeroCopy.contains("MiuixFolderGlassHook"));
        assertFalse(module.contains("workstation_transition_ownership_transform"));
    }

    @Test
    public void folderGlassGetsAnIndependentModuleEntryPoint() throws Exception {
        String module = read("ModuleMain.java");
        int mainHook = module.indexOf("new MainHook().install(classLoader);");
        int folderHook = module.indexOf("MiuixFolderGlassHook.install(classLoader, runtimeConfig);");

        assertTrue("MainHook must remain the first Dock/runtime owner", mainHook >= 0);
        assertTrue(
                "folder glass must be installed independently after MainHook without entering Dock ownership",
                folderHook > mainHook);
    }
}
