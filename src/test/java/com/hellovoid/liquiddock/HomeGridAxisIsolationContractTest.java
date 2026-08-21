package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Regression contract: horizontal grid spacing must not resize the vertical axis. */
public class HomeGridAxisIsolationContractTest {
    @Test public void normalWorkspaceUsesIndependentCellWidthAndHeight() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/HomeGridHook.java"));

        assertFalse(source.contains("int cellSize = Math.min(baseCell, Math.min("));
        assertTrue(source.contains("int cellWidth = Math.min(baseCell"));
        assertTrue(source.contains("int cellHeight = Math.min(baseCell"));
        assertTrue(source.contains("\"mCellWidth\", cellWidth"));
        assertTrue(source.contains("\"mCellHeight\", cellHeight"));
    }
}
