package com.hellovoid.liquiddock;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/** Diagnostic-only adapter that reads MainHook's already-computed HOME/APP baseline. */
final class HomeOwnershipShadowLauncherHook {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static Class<?> launcherClass;
    private static Field launcherResumedField;
    private static Field launcherLifecycleKnownField;
    private static Field liquidGlassViewField;
    private static Method foregroundTaskWindowingModeMethod;

    private HomeOwnershipShadowLauncherHook() {}

    static void install(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            launcherClass = Class.forName(
                    "com.miui.home.launcher.Launcher", false, classLoader);
            launcherResumedField = MainHook.class.getDeclaredField("launcherResumed");
            launcherResumedField.setAccessible(true);
            launcherLifecycleKnownField = MainHook.class.getDeclaredField("launcherLifecycleKnown");
            launcherLifecycleKnownField.setAccessible(true);
            liquidGlassViewField = MainHook.class.getDeclaredField("liquidGlassView");
            liquidGlassViewField.setAccessible(true);
            foregroundTaskWindowingModeMethod = MainHook.class.getDeclaredMethod(
                    "foregroundTaskWindowingMode", Object.class);
            foregroundTaskWindowingModeMethod.setAccessible(true);

            HookUtil.hookMethod(launcherClass, "setupViews", new Class<?>[0], chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                postSample("seed", chain.getThisObject(), null);
                return result;
            });

            HookUtil.hookMethod(launcherClass, "onWindowFocusChanged",
                    new Class<?>[]{boolean.class}, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        Boolean focus = Boolean.TRUE.equals(chain.getArgs().get(0));
                        postSample("focus", chain.getThisObject(), focus);
                        return result;
                    });

            Api101Bridge.log("[DC-SHADOW] Launcher HOME ownership observer installed");
        } catch (Throwable error) {
            Api101Bridge.log("[DC-SHADOW] Launcher HOME ownership observer unavailable", error);
        }
    }

    private static void postSample(String reason, Object launcher, Boolean focus) {
        MAIN.post(() -> sampleAfterProductionHooks(reason, launcher, focus));
    }

    private static void sampleAfterProductionHooks(String reason, Object launcher, Boolean focus) {
        try {
            if (launcher == null || launcherClass == null || !launcherClass.isInstance(launcher)) return;
            if (!launcherLifecycleKnownField.getBoolean(null)) {
                Api101Bridge.log("[DC-SHADOW] home-ownership result=SKIP_UNKNOWN reason=" + reason);
                return;
            }

            boolean launcherHome = launcherResumedField.getBoolean(null);
            int topWindowingMode = -1;
            try {
                Object value = foregroundTaskWindowingModeMethod.invoke(null, launcher);
                if (value instanceof Integer) topWindowingMode = (Integer) value;
            } catch (Throwable ignored) {}

            DockLiquidGlassView glass = null;
            try {
                Object value = liquidGlassViewField.get(null);
                if (value instanceof DockLiquidGlassView) glass = (DockLiquidGlassView) value;
            } catch (Throwable ignored) {}

            boolean overview = false;
            boolean allApps = false;
            if (glass != null) {
                try {
                    Object value = HookUtil.getField(glass, "overviewActive");
                    overview = Boolean.TRUE.equals(value);
                } catch (Throwable ignored) {}
                try {
                    allApps = glass.isAllAppsActive();
                } catch (Throwable ignored) {}
            }
            HomeOwnershipShadowProbe.setOverviewActive(overview);
            HomeOwnershipShadowProbe.setAllAppsActive(allApps);

            boolean workstation = MainHook.isWorkstationMode();
            HomeOwnershipShadowProbe.sample(reason, displayId(launcher), focus,
                    topWindowingMode, launcherHome, workstation);
        } catch (Throwable error) {
            Api101Bridge.log("[DC-SHADOW] Launcher HOME ownership sample unavailable", error);
        }
    }

    private static int displayId(Object launcher) {
        if (!(launcher instanceof Activity)) return Display.DEFAULT_DISPLAY;
        try {
            Display display = ((Activity) launcher).getDisplay();
            return display != null ? display.getDisplayId() : Display.DEFAULT_DISPLAY;
        } catch (Throwable ignored) {
            return Display.DEFAULT_DISPLAY;
        }
    }
}
