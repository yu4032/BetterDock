package com.hellovoid.liquiddock;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression contract for the first Launcher start before the divider parent is measured. */
public class DockDividerInitializationContractTest {
    private static String source() throws IOException {
        Path path = Paths.get("src/main/java/com/hellovoid/liquiddock/DockDividerHook.java");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    public void zeroParentHeightNeverFallsBackToTheDividerOwnHeight() throws IOException {
        String source = source();
        assertFalse("divider height is not a valid substitute for parent height",
                source.contains("parentH = lp.height > 0 ? lp.height : line.getHeight()"));
    }

    @Test
    public void geometryIsDeferredUntilTheParentHasRealLayoutBounds() throws IOException {
        String source = source();
        assertTrue("first bind must schedule a post-layout geometry retry",
                source.contains("scheduleGeometryAfterLayout(line)"));
        assertTrue("post-layout retry must use a pre-draw/layout callback",
                source.contains("addOnPreDrawListener") || source.contains("addOnLayoutChangeListener"));
    }
}
