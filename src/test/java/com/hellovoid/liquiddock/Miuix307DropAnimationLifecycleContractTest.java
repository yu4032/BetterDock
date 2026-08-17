package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contract for the 307 Launcher drop-settling lifecycle. */
public class Miuix307DropAnimationLifecycleContractTest {
    private static String read(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/" + file));
    }

    @Test
    public void endDragFreezesEvenWhenMiuiCounterIsAlreadyZero() throws Exception {
        String drag = read("Miuix307DragCaptureHook.java");

        int endHook = drag.indexOf("HookUtil.hookMethod(dragController, \"endDrag\"");
        int retain = drag.indexOf(
                "Object dragObject = currentDragObject(chain.getThisObject());", endHook);
        int proceed = drag.indexOf("chain.proceed", retain);
        int handoff = drag.indexOf("onEndDrag(dragObject)", proceed);
        int handler = drag.indexOf("private static void onEndDrag(Object dragObject)");
        int freeze = drag.indexOf("glass.setDockDragging(true, null, null);", handler);
        int nextMethod = drag.indexOf("\n    private static void ", handler + 1);
        String endDragBody = nextMethod > handler
                ? drag.substring(handler, nextMethod) : drag.substring(handler);

        assertTrue("307 endDrag hook must exist", endHook >= 0);
        assertTrue("DragObject must be retained before endDrag clears mDragObject",
                retain > endHook);
        assertTrue("retention must happen before the original endDrag proceeds",
                proceed > retain);
        assertTrue("retained DragObject must be handed to the logical end handler",
                handoff > proceed);
        assertTrue("endDrag must enter settling even if mDropAnimationCounter reads zero",
                endDragBody.contains("dropSettling = true;"));
        assertTrue("endDrag must retain the finishing DragObject",
                endDragBody.contains("settlingDragObject = dragObject;"));
        assertTrue("endDrag must keep the last clean backdrop frozen", freeze > handler);
        assertFalse("endDrag must not immediately resume capture on counter == 0",
                endDragBody.contains("finishDockDragCapture(\"drag end\")"));
    }

    @Test
    public void completionUsesVendorCallbacksNotAGuessedDelay() throws Exception {
        String drag = read("Miuix307DragCaptureHook.java");

        assertTrue(drag.contains("com.miui.home.launcher.DragObject"));
        assertTrue(drag.contains("\"onDropAnimationFinished\""));
        assertTrue(drag.contains("mDropAnimationCounter"));
        assertTrue(drag.contains("settlingDropCallbacksRemaining"));
        assertTrue(drag.contains("finishDropSettling("));

        int resetHook = drag.indexOf("\"resetDraggingView\"");
        int resetProceed = drag.indexOf("chain.proceed", resetHook);
        int cleanup = drag.indexOf("onHotseatDragCleanup()", resetProceed);
        assertTrue("HotSeats resetDraggingView is an observed final cleanup boundary",
                resetHook >= 0 && resetProceed > resetHook && cleanup > resetProceed);

        // Completion is lifecycle-driven, never guessed from a fixed animation duration.
        assertFalse(drag.contains("postDelayed("));
    }

    @Test
    public void compositorBarrierKeepsFreezeUntilNextAnimationFrame() throws Exception {
        String drag = read("Miuix307DragCaptureHook.java");

        int finish = drag.indexOf("private static void finishDropSettling(String reason)");
        int next = drag.indexOf("\n    private static void ", finish + 1);
        String body = next > finish ? drag.substring(finish, next) : drag.substring(finish);

        assertTrue("drop completion needs an explicit release-scheduled guard",
                drag.contains("dropReleaseScheduled"));
        assertTrue("drop completion must cross a VSYNC barrier before fresh capture",
                body.contains("postOnAnimation"));
        assertTrue("the deferred release must be tied to the current drag session",
                body.contains("releaseSession") && body.contains("dragSessionId"));
        assertTrue("fresh capture belongs inside the deferred compositor-barrier callback",
                body.indexOf("postOnAnimation") < body.indexOf("finishDockDragCapture(reason)"));
        assertFalse("do not replace the frame barrier with a guessed time delay",
                body.contains("postDelayed("));
    }

    @Test
    public void dropAnimationCompletionIsCheckedAfterMiuiDecrementsItsCounter() throws Exception {
        String drag = read("Miuix307DragCaptureHook.java");

        int finishHook = drag.indexOf("\"onDropAnimationFinished\"");
        int proceed = drag.indexOf("chain.proceed", finishHook);
        int callback = drag.indexOf(
                "onDropAnimationFinished(chain.getThisObject())", proceed);

        assertTrue("DragObject completion hook must exist", finishHook >= 0);
        assertTrue("MIUI must update its animation state first", proceed > finishHook);
        assertTrue("LiquidDock must inspect the post-proceed state", callback > proceed);
    }

    @Test
    public void systemDockLogsOnlyRealStateTransitions() throws Exception {
        String drag = read("Miuix307DragCaptureHook.java");
        assertTrue(drag.contains("private static volatile boolean systemDockDragActive;"));
        assertTrue(drag.contains("if (systemDockDragActive == active) return;"));
    }

    @Test
    public void dropAnimationLifecycleHookRemains307Only() throws Exception {
        String pipeline = read("Miuix307MaterialPipeline.java");
        String main = read("MainHook.java");

        assertTrue(pipeline.contains("Miuix307DragCaptureHook.install(classLoader)"));
        assertFalse(main.contains("Miuix307DragCaptureHook.install(classLoader)"));
    }
}
