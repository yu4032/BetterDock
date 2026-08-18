package com.hellovoid.liquiddock;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Filters Launcher 4.50's final APP->HOME icon-flight from LiquidDock mode-1 capture.
 *
 * Device logs and direct 4.50 inspection show the target build uses FloatingIconView2, a normal
 * Launcher View rendered into the Launcher root Surface, not FloatingIconLayer2 child surfaces.
 * Therefore there is no independent icon SurfaceControl to exclude. The reliable compositor
 * boundary is GestureModeApp.performAppToHome(): the user's pull remains fully live up to this
 * vendor HOME commit; before the method proceeds into CLOSE_TO_HOME, exclude the closing APP task
 * package and its package-less auxiliary layers while Launcher/wallpaper continue streaming.
 */
final class Miuix307IconFlightSurfaceHook {
    private static final String TAG = "[DC][IX]";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private Miuix307IconFlightSurfaceHook() {}

    static void install(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        installVendorHomeCommitFilter(classLoader);
        installVendorCleanup(classLoader);
        Api101Bridge.log(TAG + " 4.50 performAppToHome closing-task filter installed");
    }

    private static void installVendorHomeCommitFilter(ClassLoader classLoader) {
        try {
            Class<?> appMode = Class.forName(
                    "com.miui.home.recents.GestureModeApp", false, classLoader);
            HookUtil.hookMethod(appMode, "performAppToHome", new Class<?>[0], chain -> {
                Object owner = chain.getThisObject();
                DockLiquidGlassView glass = currentGlass();
                String closingPackage = resolveClosingPackage(owner, glass);
                CaptureExclusionNames.setTransitionAppLayerPrefix(closingPackage);
                Api101Bridge.log(TAG + " vendor HOME exclusion armed before performAppToHome pkg="
                        + closingPackage + " glass=" + objectId(glass));
                if (glass != null) glass.requestCapture("vendor-home-exclusion-start");
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " performAppToHome filter unavailable", error);
        }
    }

    private static void installVendorCleanup(ClassLoader classLoader) {
        try {
            Class<?> appMode = Class.forName(
                    "com.miui.home.recents.GestureModeApp", false, classLoader);
            HookUtil.hookMethod(appMode, "onRecentsAnimationCanceled",
                    new Class<?>[]{boolean.class}, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        clear("recents-animation-canceled");
                        return result;
                    });

            hookAnimationEnd(
                    Class.forName("com.miui.home.recents.GestureModeApp$6", false, classLoader),
                    "app-to-app-animation-end");
            hookAnimationEnd(
                    Class.forName("com.miui.home.recents.GestureModeApp$8", false, classLoader),
                    "app-to-home-animation-end");
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " vendor HOME exclusion cleanup unavailable", error);
        }
    }

    private static void hookAnimationEnd(Class<?> listenerClass, String reason) {
        for (Method method : listenerClass.getDeclaredMethods()) {
            if (!"onAnimationEnd".equals(method.getName())) continue;
            HookUtil.hook(method, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                clear(reason);
                return result;
            });
        }
    }

    private static DockLiquidGlassView currentGlass() {
        try {
            Object value = HookUtil.invokeStatic(Miuix307RecentsInputHook.class, "boundGlass");
            if (value instanceof DockLiquidGlassView) return (DockLiquidGlassView) value;
        } catch (Throwable ignored) {}
        try {
            Object value = HookUtil.invokeStatic(Miuix307GestureBackdropHoldHook.class,
                    "transitionGlass");
            return value instanceof DockLiquidGlassView ? (DockLiquidGlassView) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String resolveClosingPackage(Object appMode, DockLiquidGlassView glass) {
        if (glass != null) {
            try {
                glass.refreshForegroundAppLayer();
                Object value = HookUtil.getField(glass, "appLayerPkg");
                if (value instanceof String && !((String) value).isEmpty()) {
                    return (String) value;
                }
            } catch (Throwable error) {
                Api101Bridge.log(TAG + " foreground APP package refresh unavailable", error);
            }
        }

        // 4.50 DEX exposes mRunningTaskComponentName on GestureModeApp. Use it as a direct
        // fallback at performAppToHome if the capture-side package cache has not populated yet.
        try {
            Object component = HookUtil.getField(appMode, "mRunningTaskComponentName");
            Object value = component == null ? null : HookUtil.invoke(component, "getPackageName");
            return value instanceof String ? (String) value : null;
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " running task package fallback unavailable", error);
            return null;
        }
    }

    private static void clear(String reason) {
        String before = CaptureExclusionNames.transitionAppLayerPrefixForTests();
        CaptureExclusionNames.clearTransitionAppLayerPrefix();
        if (before != null) {
            Api101Bridge.log(TAG + " vendor HOME exclusion cleared reason=" + reason
                    + " pkg=" + before);
        }
    }

    private static String objectId(Object value) {
        if (value == null) return "null";
        return value.getClass().getSimpleName() + "@"
                + Integer.toHexString(System.identityHashCode(value));
    }
}
