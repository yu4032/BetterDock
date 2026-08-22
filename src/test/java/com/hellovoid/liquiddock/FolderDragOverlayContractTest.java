package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class FolderDragOverlayContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void dragContainerFeedsGenericOverlayAndFolderYieldsStaticSink() throws Exception {
        Path hookPath = MAIN.resolve("MiuixLauncherDragOverlayHook.java");
        assertTrue(Files.exists(hookPath));
        String hook = Files.readString(hookPath);
        String sink = Files.readString(MAIN.resolve("LauncherGlassSinkView.java"));

        assertTrue(hook.contains("onViewAdded"));
        assertTrue(hook.contains("onViewRemoved"));
        assertTrue(hook.contains("contains(\"DragContainer\")"));
        assertTrue(hook.contains("LauncherGlassDragOverlay.begin"));
        assertTrue(hook.contains("LauncherGlassDragOverlay.end"));
        assertTrue(hook.contains("LauncherGlassDragState.Kind.FOLDER"));

        assertTrue(sink.contains("suppressedByDrag"));
        assertTrue(sink.contains("void setSuppressedByDrag(boolean suppressed)"));
        assertTrue(sink.contains("suppressedByFolderOpen || suppressedByDrag"));
        assertFalse(sink.contains("changed |= isInDragContainer(material);"));
    }

    @Test
    public void folderMaterialOwnsAuthoritativeStaticSinkDragSuppression() throws Exception {
        String folderHook = Files.readString(MAIN.resolve("MiuixFolderGlassHook.java"));

        assertTrue(folderHook.contains("onDragContainerBgAnimAlpha"));
        assertTrue(folderHook.contains("new Class<?>[]{Boolean.TYPE, Boolean.TYPE}"));
        assertTrue(folderHook.contains("setSuppressedByDrag(!normalState)"));
    }

    @Test
    public void dragContainerDetectionWalksAncestorsInsteadOfDirectParentOnly() throws Exception {
        String overlay = Files.readString(MAIN.resolve("LauncherGlassDragOverlay.java"));
        assertTrue(overlay.contains("while (cursor instanceof View)"));
        assertTrue(overlay.contains("contains(\"DragContainer\")"));
    }
}
