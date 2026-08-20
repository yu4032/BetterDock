package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Regression contract for HotSeats teardown/recreation during workstation mode switches. */
public class WorkstationDockBackgroundRecoveryContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void hierarchyDetachClearsTheZeroCopyBindingWithoutRestoringStrongViewState() throws Exception {
        String host = Files.readString(MAIN.resolve("DockLiquidGlassHostView.java"));
        String glass = Files.readString(MAIN.resolve("MiuixGlassHook.java"));

        assertTrue(host.contains("MiuixGlassHook.onHostDetached(this);"));
        assertTrue(glass.contains("static void onHostDetached(DockLiquidGlassHostView detachedHost)"));
        assertTrue(glass.contains("Miuix307ZeroCopyRenderer.clear();"));
        assertTrue(glass.contains("clearTrackedViews();"));
        assertTrue(glass.contains("WeakReference<DockLiquidGlassHostView> hostRef"));
        assertTrue(glass.contains("WeakReference<View> backgroundRef"));
        assertFalse(glass.contains("private static View backgroundRef;"));
        assertFalse(glass.contains("private static DockLiquidGlassHostView hostRef;"));
    }

    @Test public void hotSeatsAttachRecoversWhicheverMaterialThemeIsCurrent() throws Exception {
        String pipeline = Files.readString(MAIN.resolve("Miuix307MaterialPipeline.java"));

        assertTrue(pipeline.contains("installHotSeatsAttachRecovery(classLoader, config);"));
        assertTrue(pipeline.contains("private static void installHotSeatsAttachRecovery("));
        assertTrue(pipeline.contains("hotSeatsClass.getDeclaredMethod(\"onAttachedToWindow\")"));
        assertTrue(pipeline.contains("View background = resolveBackground(hotSeats);"));
        assertTrue(pipeline.contains("ensureGlassBound(background, config, classLoader);"));

        // Avoid the old inherited View/FrameLayout hook; recover at the concrete HotSeats boundary.
        assertFalse(pipeline.contains("installBackgroundAttachRecovery(backgroundClass"));
    }
}
