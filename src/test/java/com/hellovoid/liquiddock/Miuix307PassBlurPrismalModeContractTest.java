package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Locks the HyperOS Dock renderer to its intentional Dock-only Prismal correction. */
public class Miuix307PassBlurPrismalModeContractTest {
    @Test
    public void dockExplicitlyRequestsSingleEdgePrismalMode() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java"),
                StandardCharsets.UTF_8);

        assertTrue("Dock must opt in to the single-edge correction explicitly",
                source.contains(
                        "new PrismalRenderer(PrismalRenderer.Mode.DOCK_SINGLE_EDGE)"));
        assertFalse("Dock must not rely on the generic renderer default",
                source.contains("prismalRenderer = new PrismalRenderer();"));
    }
}
