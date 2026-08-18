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
        assertTrue("HotSeats resetDraggingView is an observed cleanup boundary",
                resetHook >= 0 && resetProceed > resetHook && cleanup > resetProceed);

        // Completion is lifecycle-driven, never guessed from a fixed animation duration.
        assertFalse(drag.contains("postDelayed("));
    }

    @Test
    public void hotseatCleanupCannotPreemptRealDragObjectFinish() throws Exception {
        String drag = read("Miuix307DragCaptureHook.java");

        int cleanup = drag.indexOf("private static void onHotseatDragCleanup()");
        int next = drag.indexOf("\n    private static void ", cleanup + 1);
        String body = next > cleanup ? drag.substring(cleanup, next) : drag.substring(cleanup);

        assertTrue("when the real DragObject finish hook is available, HotSeats cleanup is fallback-only",
                body.contains("dropAnimationFinishHookInstalled")
                        && body.contains("settlingDragObject != null"));
        assertTrue("HotSeats cleanup must stay frozen and wait for DragObject finish",
                body.contains("waiting for DragObject animation end") && body.contains("return;"));
        assertTrue("fallback release is allowed only after the real-finish guard",
                body.indexOf("dropAnimationFinishHookInstalled")
                        < body.indexOf("finishDropSettling("));
    }

    @Test
    public void authoritativeVendorFinishCannotBeBlockedForeverByRetainedShownView() throws Exception {
        String drag = read("Miuix307DragCaptureHook.java");

        int callback = drag.indexOf("private static void onDropAnimationFinished(Object dragObject)");
        int nextCallback = drag.indexOf("\n    private static void ", callback + 1);
        String callbackBody = nextCallback > callback
                ? drag.substring(callback, nextCallback) : drag.substring(callback);
        assertTrue("vendor DragObject completion must enter an authoritative release path",
                callbackBody.contains("finishDropSettling(\"drag release anim end\", true)"));

        int finish = drag.indexOf("private static void finishDropSettling(String reason, boolean vendorFinished)");
        int nextFinish = drag.indexOf("\n    private static void ", finish + 1);
        String finishBody = nextFinish > finish
                ? drag.substring(finish, nextFinish) : drag.substring(finish);
        assertTrue("only fallback cleanup may keep waiting on a retained DragView",
                finishBody.contains("!vendorFinished && hasVisibleSettlingDragView()"));
        assertTrue("authoritative vendor finish still crosses the compositor frame barrier",
                finishBody.contains("postOnAnimation"));
        assertFalse("release must remain event/VSYNC driven, never timer driven",
                finishBody.contains("postDelayed("));
    }

    @Test
    public void fallbackWithoutVendorFinishStillWaitsForActualDragViews() throws Exception {
        String drag = read("Miuix307DragCaptureHook.java");

        int cleanup = drag.indexOf("private static void onHotseatDragCleanup()");
        int nextCleanup = drag.indexOf("\n    private static void ", cleanup + 1);
        String cleanupBody = nextCleanup > cleanup
                ? drag.substring(cleanup, nextCleanup) : drag.substring(cleanup);
        assertTrue("fallback cleanup must explicitly use the non-authoritative release path",
                cleanupBody.contains("finishDropSettling(\"hotseat drag cleanup fallback\", false)"));

        int check = drag.indexOf("private static void scheduleSettlingDragViewCheck(String reason)");
        int nextCheck = drag.indexOf("\n    private static void ", check + 1);
        String checkBody = nextCheck > check ? drag.substring(check, nextCheck) : drag.substring(check);
        assertTrue("fallback DragView visibility gate stays VSYNC-driven",
                checkBody.contains("postOnAnimation") && !checkBody.contains("postDelayed("));
        assertTrue("visual presence is still read from the actual View state for fallback",
                drag.contains("isAttachedToWindow()")
                        && drag.contains("getVisibility() == View.VISIBLE")
                        && drag.contains("isShown()")
                        && drag.contains("getAlpha() > 0.01f"));
    }

    @Test
    public void compositorBarrierKeepsFreezeUntilNextAnimationFrame() throws Exception {
        String drag = read("Miuix307DragCaptureHook.java");

        int finish = drag.indexOf("private static void finishDropSettling(String reason, boolean vendorFinished)");
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
