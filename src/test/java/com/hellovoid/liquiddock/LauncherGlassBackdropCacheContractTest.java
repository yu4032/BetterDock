package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Ensures cheap redraws reuse the already prepared wallpaper backdrop. */
public class LauncherGlassBackdropCacheContractTest {
    private static final Path SESSION = Path.of(
            "src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java");

    @Test
    public void interactionRedrawDoesNotRebuildNormalizationAndBlur() throws Exception {
        String source = Files.readString(SESSION);

        assertTrue(source.contains("private boolean backdropPrepared;"));
        assertTrue(source.contains(
                "renderScene(work.rebuildBackdrop || sourceChanged || !backdropPrepared);"));
        assertTrue(source.contains("private void renderScene(boolean rebuildBackdrop)"));
        assertTrue(source.contains(
                "if (rebuildBackdrop || rawTargetChanged || !backdropPrepared)"));
        assertTrue(source.contains("backdropPrepared = true;"));
    }

    @Test
    public void explicitProducerRefreshUsesBoundedBridgeBurst() throws Exception {
        String source = Files.readString(SESSION);

        assertTrue(source.contains(
                "Miuix307PassBlurBridge.requestSingleUpdate(current, root);"));
    }
}
