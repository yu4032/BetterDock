package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contracts for the clean main-based folder glass source path. */
public class FolderWallpaperOnlySourceContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    private static String readOrEmpty(String name) throws Exception {
        Path path = MAIN.resolve(name);
        return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
    }

    @Test
    public void folderSessionUsesWallpaperOnlyGpuSourceAndPortablePrismal() throws Exception {
        String session = readOrEmpty("LauncherGlassSession.java");

        assertTrue("LauncherGlassSession must be implemented", !session.isBlank());
        assertTrue("folder session must declare the wallpaper-only source contract",
                session.contains("PassBlur-wallpaper-only"));
        assertTrue("folder session must reuse the portable Prismal renderer",
                session.contains("PrismalRenderer"));
        assertTrue("folder session must use the existing GPU PassBlur bridge",
                session.contains("Miuix307PassBlurBridge"));

        assertFalse("Launcher scene replay is forbidden", session.contains("HardwareRenderer"));
        assertFalse("Launcher scene replay is forbidden", session.contains("RenderNode"));
        assertFalse("Launcher scene replay is forbidden", session.contains("RecordingCanvas"));
        assertFalse("GPU-only folder glass must never read pixels to CPU",
                session.contains("glReadPixels"));
        assertFalse("GPU-only folder glass must not build a Bitmap capture path",
                session.contains("Bitmap"));
    }

    @Test
    public void folderHookNeverEntersDockOrWorkstationOwnership() throws Exception {
        String folder = readOrEmpty("MiuixFolderGlassHook.java");
        String module = readOrEmpty("ModuleMain.java");

        assertTrue("MiuixFolderGlassHook must be implemented", !folder.isBlank());
        assertTrue("folder hook must target MIUI FolderIcon",
                folder.contains("com.miui.home.launcher.FolderIcon"));
        assertFalse(folder.contains("HotSeats"));
        assertFalse(folder.contains("DockContainer"));
        assertFalse(folder.contains("Miuix307MaterialPipeline"));
        assertFalse(folder.contains("MiuixGlassHook"));
        assertFalse(folder.contains("Miuix307ZeroCopyRenderer"));

        int mainHook = module.indexOf("new MainHook().install(classLoader);");
        int folderHook = module.indexOf("MiuixFolderGlassHook.install(classLoader, runtimeConfig);");
        assertTrue("folder hook must be installed after MainHook as a separate owner",
                mainHook >= 0 && folderHook > mainHook);
    }
}
