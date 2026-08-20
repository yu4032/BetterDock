package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Regression contract for HotSeats teardown/recreation during workstation mode switches. */
public class WorkstationDockBackgroundRecoveryContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void hierarchyDetachInvalidatesTheZeroCopyBindingBeforeRebind() throws Exception {
        String pipeline = Files.readString(MAIN.resolve("Miuix307MaterialPipeline.java"));
        String glass = Files.readString(MAIN.resolve("MiuixGlassHook.java"));

        assertTrue(pipeline.contains("MiuixGlassHook.invalidateBinding(watchedBackground);"));
        assertTrue(glass.contains("static void invalidateBinding(View dockBg)"));
        assertTrue(glass.contains("bindingInvalidated = true;"));
        assertTrue(glass.contains("if (bindingInvalidated) return false;"));
        assertTrue(glass.contains("Miuix307ZeroCopyRenderer.clear();"));
    }

    @Test public void hotSeatsAttachRecoversWhicheverMaterialThemeIsCurrent() throws Exception {
        String pipeline = Files.readString(MAIN.resolve("Miuix307MaterialPipeline.java"));

        assertTrue(pipeline.contains("installHotSeatsAttachRecovery(classLoader, config);"));
        assertTrue(pipeline.contains("private static void installHotSeatsAttachRecovery("));
        assertTrue(pipeline.contains("hotSeatsClass.getDeclaredMethod(\"onAttachedToWindow\")"));
        assertTrue(pipeline.contains("View background = resolveBackground(hotSeats);"));
        assertTrue(pipeline.contains("ensureGlassBound(background, config, classLoader);"));

        // Do not resolve onAttachedToWindow through the default FrameLayout subclass: HookUtil's
        // resolver walks superclasses and could otherwise hook a generic framework declaration.
        assertFalse(pipeline.contains("installBackgroundAttachRecovery(backgroundClass"));
    }
}
