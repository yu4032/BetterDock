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
        int activate = hook.indexOf("setVendorTransitionActive(true", startHook);
        int proceed = hook.indexOf("chain.proceed", activate);
        assertTrue(module.contains("Miuix307GestureBackdropHoldHook.install(classLoader)"));
        assertTrue(vendorClass >= 0 && startHook > vendorClass);
        assertTrue(startHook >= 0 && activate > startHook && proceed > activate);
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
    public void transitionBurstRenewsSixtyFpsCadencePinsAppSourceAndUsesRuntimeGlass() throws Exception {
        String hook = read("Miuix307GestureBackdropHoldHook.java");
        String runtime = read("SystemUiTransitionRuntime.java");
        assertTrue(hook.contains("postOnAnimation") && hook.contains("scheduleCaptureFrame"));
        assertTrue(hook.contains("captureCadence") && hook.contains("noteInteraction")
                && hook.contains("System.nanoTime()"));
        assertTrue(hook.contains("glass.requestCapture(reason)"));
        assertTrue(hook.contains("appBackdropPrearmActive") && hook.contains("appBackdropPrearmToken"));
        assertTrue(hook.contains("pinTransitionSceneToApp")
                && hook.contains("setGestureTarget\", \"APP\"")
                && hook.contains("updateDesiredScene"));
        assertTrue("SystemUI must pass its exact bound glass into transition authority",
                runtime.contains("setSystemUiTransitionActive(\n                    glass, true"));
        assertTrue("transition authority must retain that exact glass instead of rediscovering it",
                hook.contains("transitionGlassRef = new WeakReference<>(glass)"));
        assertFalse("explicit SystemUI transition start must not be gated by material-install timing",
                hook.contains("if (!Miuix307MaterialPipeline.isInstalled()) return;"));
    }

    @Test
    public void finalHomeIconFlightExcludesOnlyClosingAppLayer() throws Exception {
        String runtime = read("SystemUiTransitionRuntime.java");
        String exclusions = read("CaptureExclusionNames.java");

        int begin = runtime.indexOf("beginAppToLauncherVisualHold");
        int setExclude = runtime.indexOf("setTransitionAppLayerPrefix", begin);
        int startBurst = runtime.indexOf("setSystemUiTransitionActive", begin);
        assertTrue("closing APP exclusion must begin only at authoritative APP_TO_LAUNCHER start",
                begin >= 0 && setExclude > begin && startBurst > setExclude);
        assertTrue("foreground APP package must be cached while APP becomes top",
                runtime.contains("refreshForegroundAppPackage(glass)")
                        && runtime.contains("appLayerPkg"));
        assertTrue("transition APP prefix must be emitted into the mode-1 exclusion names",
                exclusions.contains("add(names, transitionAppLayerPrefix)"));
        assertTrue("active transition exclusion must force the 307 merge path even if the material"
                        + " pipeline runtime flag is false on the target device",
                exclusions.contains("boolean transitionExclusionActive = transitionAppLayerPrefix != null")
                        && exclusions.contains("Miuix307MaterialPipeline.isInstalled() || transitionExclusionActive"));
        assertTrue("HOME/Overview/abort paths must clear the temporary closing APP exclusion",
                runtime.contains("clearTransitionAppLayerPrefix()"));
        assertFalse("icon-flight cleanup must not freeze capture or switch to wallpaper",
                runtime.contains("setTransitionAppLayerPrefix(\"com.miui.home\")"));
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
