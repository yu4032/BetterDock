package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Source contracts for the device-confirmed HyperOS 307 capture regressions. */
public class Miuix307AgileFixContractTest {
    private static String read(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/" + file));
    }

    @Test
    public void appGestureHoldStartsAtVendorInputBeforeTransformedCaptureAndNeverGuessesDestination()
            throws Exception {
        String hold = read("Miuix307GestureBackdropHoldHook.java");
        String module = read("ModuleMain.java");

        int vendorClass = hold.indexOf("com.miui.home.recents.GestureInputHelper");
        int inputHook = hold.indexOf("\"onInputEvent\".equals(method.getName())", vendorClass);
        int arm = hold.indexOf("maybeArm(rawX, rawY, true)", inputHook);
        int proceed = hold.indexOf("chain.proceed(args)", arm);
        assertTrue("gesture hold must receive the Launcher class loader",
                module.contains("Miuix307GestureBackdropHoldHook.install(classLoader)"));
        assertTrue("the device-confirmed vendor GestureInputHelper must be the primary boundary",
                vendorClass >= 0 && inputHook > vendorClass);
        assertTrue("APP hold must arm before vendor input processing can start transformed capture",
                inputHook >= 0 && arm > inputHook && proceed > arm);
        assertTrue("vendor hidden signatures must be discovered by overload/class-hierarchy scan",
                hold.contains("cursor.getDeclaredMethods()")
                        && hold.contains("cursor = cursor.getSuperclass()"));
        assertTrue("arming must cancel a readback already in flight at ACTION_DOWN",
                hold.contains("cancelPendingCaptureWork"));
        assertTrue("hold must apply only to the current APP scene",
                hold.contains("desiredScene(glass) != CaptureScene.APP"));
        assertTrue("the previous LiquidDock input observer remains only a fallback",
                hold.contains("installCompatInputBoundary") && hold.contains("\"onInputMotion\""));
        assertFalse("gesture hold must not predict HOME",
                hold.contains("setGestureCaptureTarget(\"HOME\")"));
        assertFalse("gesture hold must not predict RECENTS",
                hold.contains("setGestureCaptureTarget(\"RECENTS\")"));
    }

    @Test
    public void appGestureHoldBlocksRequestsAndInstallsUntilAuthorityOrVsyncRelease()
            throws Exception {
        String hold = read("Miuix307GestureBackdropHoldHook.java");

        assertTrue("queued capture requests must be blocked while the clean APP frame is held",
                hold.contains("\"requestStateCapture\"")
                        && hold.contains("appGestureHold && chain.getThisObject() == heldGlass.get()"));
        assertTrue("in-flight capture results must also be rejected/recycled",
                hold.contains("\"installCapture\"") && hold.contains("HookUtil.invoke(args[0], \"recycle\")"));
        assertTrue("release must cross a compositor frame rather than use a guessed delay",
                hold.contains("postOnAnimation") && !hold.contains("postDelayed("));
        assertTrue("existing SystemUI visual hold must take ownership without opening a gap",
                hold.contains("SystemUiTransitionRuntime.isVisualHoldActive(glass)"));
        assertTrue("exact Overview remains an immediate destination authority",
                hold.contains("clearForOverview") && hold.contains("setOverviewActive"));
        assertTrue("a gesture with no transition authority refreshes only APP",
                hold.contains("if (scene == CaptureScene.APP)")
                        && hold.contains("miuix307-app-gesture-release"));
    }

    @Test
    public void vendorDropFinishScansAllNonPrimaryOverloadsAcrossHierarchy() throws Exception {
        String compat = read("Miuix307DropFinishCompatHook.java");
        String module = read("ModuleMain.java");

        assertTrue("compat hook must be installed after MainHook initialized the 307 pipeline",
                module.indexOf("new MainHook().install(classLoader)")
                        < module.indexOf("Miuix307DropFinishCompatHook.install(classLoader)"));
        assertTrue("vendor completion discovery must scan declared methods and superclasses",
                compat.contains("cursor.getDeclaredMethods()")
                        && compat.contains("cursor = cursor.getSuperclass()"));
        assertTrue("discovery is by callback name rather than a hard-coded signature",
                compat.contains("\"onDropAnimationFinished\".equals(method.getName())"));
        assertTrue("an already-hooked primary zero-arg method alone may be skipped",
                compat.contains("primaryInstalled && method.getParameterCount() == 0"));
        assertTrue("other overloads still feed the existing authoritative release state machine",
                compat.contains("HookUtil.invokeStatic(Miuix307DragCaptureHook.class,")
                        && compat.contains("\"onDropAnimationFinished\", dragObject"));
        assertTrue("compat coverage must publish vendor-hook availability for fallback semantics",
                compat.contains("setPrimaryHookInstalled(true)"));
    }

    @Test
    public void nestedVendorOverloadForwardingCannotDoubleDecrementDropState() throws Exception {
        String compat = read("Miuix307DropFinishCompatHook.java");

        assertTrue("nested overload calls need a per-thread depth guard",
                compat.contains("ThreadLocal<Integer> CALLBACK_DEPTH"));
        assertTrue("only the outer vendor callback may notify the LiquidDock state machine",
                compat.contains("if (depth == 0)"));
        assertFalse("vendor completion must remain callback/VSYNC driven, not timer driven",
                compat.contains("postDelayed("));
    }
}
