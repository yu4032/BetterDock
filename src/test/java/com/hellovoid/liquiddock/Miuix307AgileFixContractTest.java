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
    public void decompiled450GestureModeStartsContinuousCaptureBeforeTransforms() throws Exception {
        String hook = read("Miuix307GestureBackdropHoldHook.java");
        String module = read("ModuleMain.java");
        int vendorClass = hook.indexOf("com.miui.home.recents.GestureModeApp");
        int startHook = hook.indexOf("\"onStartGesture\"", vendorClass);
        int resetHome = hook.indexOf("vendorHomeCommitted = false", startHook);
        int activate = hook.indexOf("setVendorTransitionActive(true", startHook);
        int proceed = hook.indexOf("chain.proceed", activate);
        assertTrue(module.contains("Miuix307GestureBackdropHoldHook.install(classLoader)"));
        assertTrue(vendorClass >= 0 && startHook > vendorClass);
        assertTrue(startHook >= 0 && resetHome > startHook && activate > resetHome && proceed > activate);
        assertFalse(hook.contains("MotionEvent.ACTION_UP") || hook.contains("MotionEvent.ACTION_CANCEL"));
        assertFalse(hook.contains("setGestureCaptureTarget(\"HOME\")"));
        assertFalse(hook.contains("setGestureCaptureTarget(\"RECENTS\")"));
    }

    @Test
    public void appTransitionNeverBlocksCaptureRequestsOrFrameInstallation() throws Exception {
        String hook = read("Miuix307GestureBackdropHoldHook.java");
        String runtime = read("SystemUiTransitionRuntime.java");
        assertFalse(hook.contains("HookUtil.hookMethod(DockLiquidGlassView.class, \"requestStateCapture\""));
        assertFalse(hook.contains("\"installCapture\".equals(method.getName())"));
        assertFalse(hook.contains("cancelPendingCaptureWork"));
        assertFalse(runtime.contains("installCaptureRequestGate"));
        assertFalse(runtime.contains("installCaptureInstallGate"));
        assertFalse(runtime.contains("cancelPendingCaptureWork"));
    }

    @Test
    public void transitionBurstRenewsCadenceAndSwitchesSourceAtVendorHomeCommit() throws Exception {
        String hook = read("Miuix307GestureBackdropHoldHook.java");
        String runtime = read("SystemUiTransitionRuntime.java");
        assertTrue(hook.contains("postOnAnimation") && hook.contains("scheduleCaptureFrame"));
        assertTrue(hook.contains("captureCadence") && hook.contains("noteInteraction")
                && hook.contains("System.nanoTime()"));
        assertTrue(hook.contains("glass.requestCapture(reason)"));
        assertTrue(hook.contains("appBackdropPrearmActive") && hook.contains("appBackdropPrearmToken"));
        assertTrue(hook.contains("vendorHomeCommitted ? \"HOME\" : \"APP\"")
                && hook.contains("pinTransitionScene")
                && hook.contains("setGestureTarget\", target")
                && hook.contains("updateDesiredScene"));
        assertTrue("SystemUI must pass its exact bound glass into transition authority",
                runtime.contains("setSystemUiTransitionActive(\n                    glass, true"));
        assertTrue("transition authority must retain that exact glass instead of rediscovering it",
                hook.contains("transitionGlassRef = new WeakReference<>(glass)"));
        assertFalse("explicit SystemUI transition start must not be gated by material-install timing",
                hook.contains("if (!Miuix307MaterialPipeline.isInstalled()) return;"));
    }

    @Test
    public void gestureToHomeIsThe307SourceAuthorityAndRetiresIconFlightExclusions() throws Exception {
        String hook = read("Miuix307GestureBackdropHoldHook.java");
        String exclusions = read("CaptureExclusionNames.java");
        String module = read("ModuleMain.java");
        String runtime = read("SystemUiTransitionRuntime.java");

        assertTrue(hook.contains("com.miui.home.launcher.dock.v3.GestureToHome"));
        assertTrue(hook.contains("getDeclaredConstructors()"));
        assertTrue(hook.contains("commitVendorHome(glass, \"GestureToHome\")"));
        assertTrue(hook.contains("vendorHomeCommitted = true"));
        assertTrue(hook.contains("requestCapture(\"miuix307-vendor-home-commit\")"));
        assertTrue(hook.contains("vendor GestureToHome committed HOME source"));
        assertFalse("HOME source handoff must not stop the capture burst",
                hook.contains("commitVendorHome(glass, \"GestureToHome\");\n                        stopAllTransitionCapture"));

        assertFalse(module.contains("Miuix307IconFlightSurfaceHook.install(classLoader)"));
        assertFalse(exclusions.contains("transitionAppLayerPrefix"));
        assertFalse(exclusions.contains("PreColorStarting"));
        assertFalse(exclusions.contains("Splash Screen "));
        assertFalse(exclusions.contains("Miui Caption of Task="));
        assertFalse(runtime.contains("HOME icon-flight exclusion"));
        assertFalse(runtime.contains("setTransitionAppLayerPrefix"));
    }

    @Test
    public void vendorSystemUiAndOverviewShareOneCaptureAuthority() throws Exception {
        String hook = read("Miuix307GestureBackdropHoldHook.java");
        String runtime = read("SystemUiTransitionRuntime.java");
        assertTrue(hook.contains("GestureModeApp$6") && hook.contains("app-to-app-animation-end"));
        assertTrue(hook.contains("GestureModeApp$8") && hook.contains("app-to-home-animation-end"));
        assertTrue(runtime.contains("setSystemUiTransitionActive(")
                && runtime.contains("glass, true, \"app-to-launcher-token-\""));
        assertTrue(runtime.contains("stopAllTransitionCapture(")
                && runtime.contains("systemui-home-finish-token-"));
        assertTrue(hook.contains("stopAllTransitionCapture(\"exact-overview\")"));
    }

    @Test
    public void vendorDropFinishScansAllNonPrimaryOverloadsAcrossHierarchy() throws Exception {
        String compat = read("Miuix307DropFinishCompatHook.java");
        String module = read("ModuleMain.java");
        assertTrue(module.indexOf("new MainHook().install(classLoader)")
                < module.indexOf("Miuix307DropFinishCompatHook.install(classLoader)"));
        assertTrue(compat.contains("cursor.getDeclaredMethods()")
                && compat.contains("cursor = cursor.getSuperclass()"));
        assertTrue(compat.contains("\"onDropAnimationFinished\".equals(method.getName())"));
        assertTrue(compat.contains("primaryInstalled && method.getParameterCount() == 0"));
        assertTrue(compat.contains("HookUtil.invokeStatic(Miuix307DragCaptureHook.class,")
                && compat.contains("\"onDropAnimationFinished\", dragObject"));
        assertTrue(compat.contains("setPrimaryHookInstalled(true)"));
    }

    @Test
    public void nestedVendorOverloadForwardingCannotDoubleDecrementDropState() throws Exception {
        String compat = read("Miuix307DropFinishCompatHook.java");
        assertTrue(compat.contains("ThreadLocal<Integer> CALLBACK_DEPTH"));
        assertTrue(compat.contains("if (depth == 0)"));
        assertFalse(compat.contains("postDelayed("));
    }
}
