package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
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

    @Test
    public void dragContainerIgnoresHelperChildrenAndInspectsOnlyRealDragViewMetadata() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixLauncherDragOverlayHook.java"));

        assertTrue(hook.contains("com.miui.home.launcher.DragView"));
        assertTrue(hook.contains("isActualDragView"));
        assertTrue(hook.contains("getTag()"));
        assertTrue(hook.contains("getDeclaredFields()"));
        assertTrue(hook.contains("classifyMetadata"));
        assertFalse("arbitrary DragContainer children must not become icon drags",
                hook.contains("return new ResolvedSource(child, LauncherGlassDragState.Kind.ICON"));
    }
}
