package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression contract: a symmetric horizontal-distance adjustment must not translate the grid. */
public class HomeGridHorizontalCenteringContractTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/com/hellovoid/liquiddock/HomeGridHook.java");

    @Test public void oversizedSourceCellKeepsSymmetricBaseMargins() throws Exception {
        String source = Files.readString(SOURCE);
        assertTrue(source.contains("baseRight = baseLeft"));
        assertFalse(source.contains("baseRight = width - (baseLeft + baseCell * countX"));
    }
}
