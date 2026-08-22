package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Contracts for the independent whole-Dock shadow during vendor Dock resize animation. */
public class DockShadowAnimationRegressionTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void transientAnimationSiblingDoesNotReparentTrackedShadow() throws Exception {
        String main = Files.readString(MAIN.resolve("MainHook.java"));

        assertFalse("temporary siblings must not force strict shadow/background adjacency",
                main.contains("shadowIndex + 1 == backgroundIndex"));
        assertTrue("an already-lower shadow remains valid even when animation siblings sit between it and the background",
                main.contains("shadowIndex < backgroundIndex"));
    }

    @Test
    public void shadowZOrderRepairWaitsForResizeAnimationToSettle() throws Exception {
        String main = Files.readString(MAIN.resolve("MainHook.java"));

        assertTrue("z-order mutations must be deferred while the vendor Dock reports animation",
                main.contains("if (!animating(dockBg)) {\n            ensureShadowBelowBackground"));
    }

    @Test
    public void deliberateShadowReinsertRestoresWeakTracking() throws Exception {
        String main = Files.readString(MAIN.resolve("MainHook.java"));
        int helper = main.indexOf("private static void ensureShadowBelowBackground");
        int next = main.indexOf("private static void installDockResizeAnimationBypass", helper);
        String body = main.substring(helper, next);

        assertTrue("detach clears the tracked owner, so a deliberate reinsert must restore it",
                body.contains("shadowViewRef = new WeakReference<>(shadow);"));
    }
}
