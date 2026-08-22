package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class FolderDragOverlayContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void folderLifecycleFeedsGenericOverlayAndSuppressesStaticSink() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixFolderGlassHook.java"));
        String sink = Files.readString(MAIN.resolve("LauncherGlassSinkView.java"));

        assertTrue(hook.contains("onDragContainerBgAnimAlpha"));
        assertTrue(hook.contains("LauncherGlassDragOverlay.begin"));
        assertTrue(hook.contains("LauncherGlassDragOverlay.end"));
        assertTrue(hook.contains("LauncherGlassDragState.Kind.FOLDER"));
        assertTrue(hook.contains("setSuppressedByDrag(true)"));
        assertTrue(hook.contains("setSuppressedByDrag(false)"));

        assertTrue(sink.contains("suppressedByDrag"));
        assertTrue(sink.contains("void setSuppressedByDrag(boolean suppressed)"));
        assertTrue(sink.contains("suppressedByFolderOpen || suppressedByDrag"));
        assertFalse(sink.contains("changed |= isInDragContainer(material);"));
    }

    @Test
    public void dragContainerDetectionWalksAncestorsInsteadOfDirectParentOnly() throws Exception {
        String overlay = Files.readString(MAIN.resolve("LauncherGlassDragOverlay.java"));
        assertTrue(overlay.contains("while (cursor instanceof View)"));
        assertTrue(overlay.contains("contains(\"DragContainer\")"));
    }
}
