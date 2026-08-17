package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contract for the 307 Launcher DragObject drop-animation lifecycle. */
public class Miuix307DropAnimationLifecycleContractTest {
    private static String read(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/" + file));
    }

    @Test
    public void endDragWaitsForTheFinalMiuiDropAnimationBeforeFreshCapture() throws Exception {
        String drag = read("Miuix307DragCaptureHook.java");

        int endHook = drag.indexOf("HookUtil.hookMethod(dragController, \"endDrag\"");
        int retain = drag.indexOf(
                "Object dragObject = currentDragObject(chain.getThisObject());", endHook);
        int proceed = drag.indexOf("chain.proceed", retain);
        int handoff = drag.indexOf("onEndDrag(dragObject)", proceed);

        assertTrue("307 endDrag hook must exist", endHook >= 0);
        assertTrue("DragObject must be retained before endDrag clears mDragObject",
                retain > endHook);
        assertTrue("retention must happen before the original endDrag proceeds",
                proceed > retain);
        assertTrue("retained DragObject must be handed to the logical end handler",
                handoff > proceed);

        assertTrue(drag.contains("com.miui.home.launcher.DragObject"));
        assertTrue(drag.contains("\"onDropAnimationFinished\""));
        assertTrue(drag.contains("mDropAnimationCounter"));
        assertTrue(drag.contains("settlingDragObject"));
        assertTrue(drag.contains("dropAnimationCounter > 0"));
        assertTrue(drag.contains("dropAnimationCounter == 0"));

        // A settling drop must deliberately remove any excludable drag Surface so the
        // last clean backdrop stays frozen until MIUI's own final animation completion.
        assertTrue(drag.contains("glass.setDockDragging(true, null, null);"));
        assertTrue(drag.contains("glass.setDockDragging(false, null, null);"));

        // Completion is lifecycle-driven, never guessed from a fixed animation duration.
        assertFalse(drag.contains("postDelayed("));
    }

    @Test
    public void dropAnimationCompletionIsCheckedAfterMiuiDecrementsItsCounter() throws Exception {
        String drag = read("Miuix307DragCaptureHook.java");

        int finishHook = drag.indexOf("\"onDropAnimationFinished\"");
        int proceed = drag.indexOf("chain.proceed", finishHook);
        int callback = drag.indexOf(
                "onDropAnimationFinished(chain.getThisObject())", proceed);

        assertTrue("DragObject completion hook must exist", finishHook >= 0);
        assertTrue("MIUI must decrement mDropAnimationCounter first", proceed > finishHook);
        assertTrue("LiquidDock must inspect the post-proceed counter", callback > proceed);
    }

    @Test
    public void dropAnimationLifecycleHookRemains307Only() throws Exception {
        String pipeline = read("Miuix307MaterialPipeline.java");
        String main = read("MainHook.java");

        assertTrue(pipeline.contains("Miuix307DragCaptureHook.install(classLoader)"));
        assertFalse(main.contains("Miuix307DragCaptureHook.install(classLoader)"));
    }
}
