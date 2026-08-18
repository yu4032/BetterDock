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
    public void decompiled450GestureModeStartsContinuousCaptureBeforeTransforms()
            throws Exception {
        String hook = read("Miuix307GestureBackdropHoldHook.java");
        String module = read("ModuleMain.java");

        int vendorClass = hook.indexOf("com.miui.home.recents.GestureModeApp");
        int startHook = hook.indexOf("\"onStartGesture\"", vendorClass);
        int activate = hook.indexOf("setVendorTransitionActive(true", startHook);
        int proceed = hook.indexOf("chain.proceed", activate);

        assertTrue("gesture lifecycle hook must receive the Launcher class loader",
                module.contains("Miuix307GestureBackdropHoldHook.install(classLoader)"));
        assertTrue("4.50 GestureModeApp must be the pre-transform lifecycle authority",
                vendorClass >= 0 && startHook > vendorClass);
        assertTrue("continuous capture must start before vendor task transforms begin",
                startHook >= 0 && activate > startHook && proceed > activate);
        assertFalse("raw ACTION_UP/CANCEL must not control transition capture lifetime",
                hook.contains("ACTION_UP") || hook.contains("ACTION_CANCEL"));
        assertFalse("gesture capture must never predict HOME through the legacy target API",
                hook.contains("setGestureCaptureTarget(\"HOME\")"));
        assertFalse("gesture capture must never predict RECENTS through the legacy target API",
                hook.contains("setGestureCaptureTarget(\"RECENTS\")"));
    }

    @Test
    public void appTransitionNeverBlocksCaptureRequestsOrFrameInstallation() throws Exception {
        String hook = read("Miuix307GestureBackdropHoldHook.java");
        String runtime = read("SystemUiTransitionRuntime.java");

        assertFalse("gesture lifecycle must not hook requestStateCapture as a freeze gate",
                hook.contains("HookUtil.hookMethod(DockLiquidGlassView.class, \"requestStateCapture\""));
        assertFalse("gesture lifecycle must not hook installCapture as a freeze gate",
                hook.contains("\"installCapture\".equals(method.getName())"));
        assertFalse("gesture transition start must not cancel valid in-flight animation frames",
                hook.contains("cancelPendingCaptureWork"));
        assertFalse("SystemUI runtime must not install a request freeze gate",
                runtime.contains("installCaptureRequestGate"));
        assertFalse("SystemUI runtime must not install an install/recycle freeze gate",
                runtime.contains("installCaptureInstallGate"));
        assertFalse("SystemUI transition start must not invalidate animation readbacks",
                runtime.contains("cancelPendingCaptureWork"));
    }

    @Test
    public void transitionBurstRenewsSixtyFpsCadenceAndVisibilityPrearm() throws Exception {
        String hook = read("Miuix307GestureBackdropHoldHook.java");

        assertTrue("transition capture must be driven every display frame",
                hook.contains("postOnAnimation")
                        && hook.contains("scheduleCaptureFrame"));
        assertTrue("each transition frame must renew the existing interaction cadence",
                hook.contains("captureCadence")
                        && hook.contains("noteInteraction")
                        && hook.contains("System.nanoTime()"));
        assertTrue("each transition frame must request a normal capture rather than bypass install",
                hook.contains("glass.requestCapture(reason)"));
        assertTrue("collapsed Floating Dock visibility must reuse the existing safe APP prearm gate",
                hook.contains("appBackdropPrearmActive")
                        && hook.contains("appBackdropPrearmToken"));
    }

    @Test
    public void vendorSystemUiAndOverviewShareOneCaptureAuthority() throws Exception {
        String hook = read("Miuix307GestureBackdropHoldHook.java");
        String runtime = read("SystemUiTransitionRuntime.java");

        assertTrue("4.50 AppToApp listener $6 must stop vendor transition capture at animation end",
                hook.contains("GestureModeApp$6")
                        && hook.contains("app-to-app-animation-end"));
        assertTrue("4.50 AppToHome listener $8 must stop only its vendor capture lease",
                hook.contains("GestureModeApp$8")
                        && hook.contains("app-to-home-animation-end"));
        assertTrue("SystemUI APP_TO_LAUNCHER must keep the shared transition burst alive",
                runtime.contains("setSystemUiTransitionActive(")
                        && runtime.contains("true, \"app-to-launcher-token-\""));
        assertTrue("exact Overview must transfer to the existing RECENTS continuation loop",
                hook.contains("stopAllTransitionCapture(\"exact-overview\")"));
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
