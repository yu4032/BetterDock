package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Contracts the authoritative HyperOS folder open/close lifecycle without hiding sibling folders. */
public class FolderOpenLifecycleContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    private static String read(String name) throws Exception {
        return Files.readString(MAIN.resolve(name));
    }

    @Test
    public void folderOpenSuppressesOnlyItsOwnLauncherGlassSink() throws Exception {
        String hook = read("MiuixFolderGlassHook.java");
        String sink = read("LauncherGlassSinkView.java");

        assertTrue("FolderIcon.onOpen is the authoritative source-owner open edge",
                hook.contains("findMethodExact(folderIcon, \"onOpen\", new Class<?>[0])"));
        assertTrue("open edge must suppress only the FolderIcon that opened",
                hook.contains("setOwnerSuppressed((ViewGroup) chain.getThisObject(), true)"));
        assertFalse("opening one folder must never hide every folder output",
                hook.contains("setAllFolderOutputsVisible(false)"));

        assertTrue("sink needs persistent folder-open state independent of vendor View visibility",
                sink.contains("suppressedByFolderOpen"));
        assertTrue("folder-open suppression must compose with independent drag suppression",
                sink.contains("suppressedByFolderOpen || suppressedByDrag"));
    }

    @Test
    public void folderCloseRestoresOnlyAfterAnimationCompletion() throws Exception {
        String hook = read("MiuixFolderGlassHook.java");

        assertTrue("FolderIcon.onClose must be observed but must not restore early",
                hook.contains("findMethodExact(folderIcon, \"onClose\", new Class<?>[0])"));
        assertTrue("Folder close completion is the authoritative restore edge",
                hook.contains("findMethodExact(folder, \"onClose\",")
                        && hook.contains("new Class<?>[]{Boolean.TYPE, Runnable.class})"));
        assertTrue("completion must restore the one tracked opened owner",
                hook.contains("restoreOpenedFolderOwner()"));
        assertFalse("close completion must not restore every folder output",
                hook.contains("setAllFolderOutputsVisible(true)"));
    }
}
