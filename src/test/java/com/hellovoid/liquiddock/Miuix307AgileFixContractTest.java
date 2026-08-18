package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Source contracts for the device-confirmed HyperOS 307/4.50 capture regressions. */
public class Miuix307AgileFixContractTest {
    private static String read(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/" + file));
    }

    @Test
    public void appGestureHoldUsesDecompiled450GestureModeBoundaryInsteadOfRawInputEnd()
            throws Exception {
        String hold = read("Miuix307GestureBackdropHoldHook.java");
        String module = read("ModuleMain.java");

        int vendorClass = hold.indexOf("com.miui.home.recents.GestureModeApp");
        int startHook = hold.indexOf("\"onStartGesture\"", vendorClass);
        int arm = hold.indexOf("armFromVendorAppGesture()", startHook);
        int proceed = hold.indexOf("chain.proceed", arm);

        assertTrue("gesture lifecycle hook must receive the Launcher class loader",
                module.contains("Miuix307GestureBackdropHoldHook.install(classLoader)"));
        assertTrue("4.50 GestureModeApp must be the primary pre-transform authority",
                vendorClass >= 0 && startHook > vendorClass);
        assertTrue("hold must arm before GestureModeApp starts its vendor transform pipeline",
                startHook >= 0 && arm > startHook && proceed > arm);
        assertFalse("raw GestureInputHelper onInputEvent must no longer own visual hold lifetime",
                hold.contains("\"onInputEvent\".equals(method.getName())"));
        assertFalse("raw ACTION_UP/CANCEL must not reopen the capture gate",
                hold.contains("ACTION_UP") || hold.contains("ACTION_CANCEL"));
        assertFalse("gesture hold must never predict HOME through the legacy target API",
                hold.contains("setGestureCaptureTarget(\"HOME\")"));
        assertFalse("gesture hold must never predict RECENTS through the legacy target API",
                hold.contains("setGestureCaptureTarget(\"RECENTS\")"));
    }

    @Test
    public void appGestureHoldNeverFreezesAStaleHomeWallpaperAsApp() throws Exception {
        String hold = read("Miuix307GestureBackdropHoldHook.java");

        assertTrue("arming must inspect the actually installed capture scene",
                hold.contains("installedCaptureScene") && hold.contains("installedBefore"));
        assertTrue("a non-APP installed scene must be invalidated before the hold closes",
                hold.contains("installedBefore != CaptureScene.APP")
                        && hold.contains("invalidateInstalledBackdropForApp"));
        assertTrue("arming must cancel readbacks already in flight before movement transforms",
                hold.contains("cancelPendingCaptureWork"));
    }

    @Test
    public void appGestureHoldBlocksRequestsAndInstallsUntilRealVisualAuthority() throws Exception {
        String hold = read("Miuix307GestureBackdropHoldHook.java");

        assertTrue("queued capture requests must be blocked while the clean APP frame is held",
                hold.contains("\"requestStateCapture\"")
                        && hold.contains("appGestureHold && chain.getThisObject() == heldGlass.get()"));
        assertTrue("in-flight capture results must also be rejected and recycled",
                hold.contains("\"installCapture\"")
                        && hold.contains("HookUtil.invoke(args[0], \"recycle\")"));
        assertTrue("exact Overview remains a destination authority",
                hold.contains("clearForOverview") && hold.contains("setOverviewActive"));
        assertTrue("SystemUI remains a no-gap handoff authority",
                hold.contains("clearForSystemUi")
                        && hold.contains("SystemUiTransitionRuntime.isVisualHoldActive(glass)"));
        assertFalse("no millisecond settle timer may reopen the gesture hold",
                hold.contains("postDelayed("));
    }

    @Test
    public void decompiled450AnimationEndsOwnAppAndHomeRelease() throws Exception {
        String hold = read("Miuix307GestureBackdropHoldHook.java");

        assertTrue("4.50 AppToApp listener $6 must release back to APP only after animation end",
                hold.contains("GestureModeApp$6")
                        && hold.contains("app-to-app-animation-end"));
        assertTrue("4.50 AppToHome listener $8 must own the HOME animation-end fallback",
                hold.contains("GestureModeApp$8")
                        && hold.contains("releaseAfterVendorHomeAnimation"));
        assertTrue("HOME fallback must cross a real compositor frame before fresh capture",
                hold.contains("postOnAnimation")
                        && hold.contains("miuix307-vendor-home-animation-end"));
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
