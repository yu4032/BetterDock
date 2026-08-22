package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class LauncherGlassDragExtensibilityContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void dragContainerAdapterClassifiesAllLauncherObjectKindsIntoOneOverlay() throws Exception {
        Path hookPath = MAIN.resolve("MiuixLauncherDragOverlayHook.java");
        assertTrue(Files.exists(hookPath));
        String hook = Files.readString(hookPath);
        String overlay = Files.readString(MAIN.resolve("LauncherGlassDragOverlay.java"));
        String state = Files.readString(MAIN.resolve("LauncherGlassDragState.java"));

        assertTrue(state.contains("FOLDER"));
        assertTrue(state.contains("WIDGET"));
        assertTrue(state.contains("ICON"));
        assertTrue(hook.contains("LauncherGlassDragState.Kind.WIDGET"));
        assertTrue(hook.contains("LauncherGlassDragState.Kind.ICON"));
        assertTrue(hook.contains("LauncherGlassDragOverlay.begin"));
        assertTrue(overlay.contains("LauncherGlassDragState.Kind kind"));
    }
}
