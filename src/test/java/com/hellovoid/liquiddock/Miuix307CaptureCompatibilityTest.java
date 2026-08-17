package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contracts for capture protections that existed before the specialized MiuiX 307 return. */
public class Miuix307CaptureCompatibilityTest {
    private static Path path(String file) {
        return Path.of("src/main/java/com/hellovoid/liquiddock/" + file);
    }

    private static String read(String file) throws Exception {
        return Files.readString(path(file));
    }

    @Test public void miuix307ReusesOriginalDragSurfaceExclusionWithoutLegacyHookBundle()
            throws Exception {
        String pipeline = read("Miuix307MaterialPipeline.java");
        Path dragPath = path("Miuix307DragCaptureHook.java");
        assertTrue("307 must have a narrow drag-only compatibility hook", Files.exists(dragPath));
        String drag = Files.readString(dragPath);

        // Keep the original proven DragController -> drag Surface -> setDockDragging path.
        assertTrue(drag.contains("com.miui.home.launcher.DragController"));
        assertTrue(drag.contains("\"startDrag\""));
        assertTrue(drag.contains("\"endDrag\""));
        assertTrue(drag.contains("mDragObject"));
        assertTrue(drag.contains("mDragViews"));
        assertTrue(drag.contains("getSurfaceControl"));
        assertTrue(drag.contains("setDockDragging(true"));
        assertTrue(drag.contains("setDockDragging(false, null)"));
        assertTrue(pipeline.contains("Miuix307DragCaptureHook.install(classLoader)"));
        assertTrue(pipeline.contains("Miuix307DragCaptureHook.bind(background)"));

        // Never restore the complete legacy capture lifecycle just to regain drag exclusion.
        assertFalse(pipeline.contains("installLiquidGlassCaptureHooks"));
    }

    @Test public void miuix307HooksRuntimeStartDragOverloadsNotOneStaleSignature()
            throws Exception {
        String drag = read("Miuix307DragCaptureHook.java");

        // 307 keeps the old eight-argument startDrag method but Dock rearrangement may dispatch
        // through another overload. Hook every instance overload by reflected Method identity so
        // a stale-but-present vendor signature cannot silently install while never executing.
        assertTrue(drag.contains("getDeclaredMethods()"));
        assertTrue(drag.contains("\"startDrag\".equals(method.getName())"));
        assertTrue(drag.contains("HookUtil.hook(method"));
        assertTrue(drag.contains("methodSignature(method)"));
        assertTrue(drag.contains("dragActive"));
        assertTrue(drag.contains("activeDragLayerName"));

        // The old exact signature was the regression: it existed, so install succeeded, but the
        // device evidence showed only endDrag callbacks and no start callback at all.
        assertFalse(drag.contains("android.graphics.drawable.Drawable.class, boolean.class,"));
    }

    @Test public void null307DragSurfaceRetriesWithinTheSameSessionAndStopsAtEnd()
            throws Exception {
        String drag = read("Miuix307DragCaptureHook.java");

        // Device evidence shows the real runtime startDrag callback can arrive before the drag
        // View owns its SurfaceControl. Retry only while this drag is active and no exclusion has
        // been resolved; a later frame must be able to upgrade the same session.
        assertTrue(drag.contains("scheduleDragSurfaceRetry"));
        assertTrue(drag.contains("postOnAnimation"));
        assertTrue(drag.contains("!dragActive || activeDragLayerName != null"));
        assertTrue(drag.contains("drag surface retry"));
    }

    @Test public void freeformGateDiagnosticIsStateDeduplicatedNotPerFrame() throws Exception {
        String gate = read("FreeformCaptureLeashHook.java");

        // The final task-leash gate already owns the correct semantic decision. Instrument only
        // that boundary and dedupe identical states so dynamic APP capture cannot flood logcat.
        assertTrue(gate.contains("LAST_GATE_LOG_SIGNATURE"));
        assertTrue(gate.contains("logGateStateIfChanged"));
        assertTrue(gate.contains("visibleFreeform="));
        assertTrue(gate.contains("remoteLeashes="));
        assertTrue(gate.contains("action="));
        assertTrue(gate.contains("miuix307="));
        assertTrue(gate.contains("compareAndSet"));

        // Preserve real-leash exclusion and a last-resort fail-closed path for unexpected errors.
        assertTrue(gate.contains("resolution.borrowedRemoteLeashes()"));
        assertTrue(gate.contains("args[5] = 2"));
    }
}
