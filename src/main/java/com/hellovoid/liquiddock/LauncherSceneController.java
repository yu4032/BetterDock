package com.hellovoid.liquiddock;

import android.app.Activity;
import android.content.Context;
import android.view.View;

import java.lang.reflect.Constructor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Owns Launcher scene arbitration and stock scene hooks. MainHook only composes this
 * controller with the current DockLiquidGlassView.
 */
final class LauncherSceneController {
    private final Supplier<DockLiquidGlassView> glassProvider;
    private final BooleanSupplier workstationModeProvider;
    private final Consumer<String> logger;
    private final ForegroundTaskResolver foregroundTaskResolver;
    private final RecentsStateHooks recentsStateHooks;
    private final AllAppsStateHooks allAppsStateHooks;

    private volatile boolean launcherResumed;
    private volatile boolean launcherLifecycleKnown;
    private volatile boolean systemUiPanelExpanded;

    LauncherSceneController(Supplier<DockLiquidGlassView> glassProvider,
                            BooleanSupplier workstationModeProvider,
                            Consumer<String> logger) {
        this.glassProvider = glassProvider;
        this.workstationModeProvider = workstationModeProvider;
        this.logger = logger;
        this.foregroundTaskResolver = new ForegroundTaskResolver(logger);
        this.recentsStateHooks = new RecentsStateHooks(glassProvider, workstationModeProvider, logger);
        this.allAppsStateHooks = new AllAppsStateHooks(glassProvider, logger);
    }

    boolean lifecycleKnown() { return launcherLifecycleKnown; }
    boolean launcherResumed() { return launcherResumed; }
    boolean systemUiPanelExpanded() { return systemUiPanelExpanded; }

    /**
     * Foreground task ownership is stronger evidence than Launcher focus/lifecycle state.
     * A positive external result also repairs a stale resumed hint immediately so later
     * scene synchronization cannot reintroduce HOME while the app still owns foreground.
     */
    boolean isExternalAppForeground(Context context) {
        String topPackage = foregroundTaskResolver.resolveTopPackage(context);
        boolean external = topPackage != null && !"com.miui.home".equals(topPackage);
        if (external) {
            launcherLifecycleKnown = true;
            launcherResumed = false;
            logger.accept("[DC] foreground authority external pkg=" + topPackage);
        }
        return external;
    }

    void seed(Object launcher) {
        if (launcher == null) return;
        try {
            Object paused = HookUtil.invoke(launcher, "isPause");
            Object visible = HookUtil.invoke(launcher, "isVisible");
            Object focused = HookUtil.invoke(launcher, "isWindowFocus");
            if (paused instanceof Boolean && !((Boolean) paused)) {
                launcherLifecycleKnown = true;
                launcherResumed = true;
            }
            logger.accept("[DC] liquid lifecycle seed: known=" + launcherLifecycleKnown
                    + " resumed=" + launcherResumed + " paused=" + paused
                    + " visible=" + visible + " focus=" + focused);
        } catch (Throwable e) {
            logger.accept("[DC] liquid lifecycle seed unavailable; using window gate: " + e);
        }
    }

    void bindRecentsView(DockLiquidGlassView glass, Object launcher) {
        if (glass != null) recentsStateHooks.bindRecentsView(glass, launcher);
    }

    void installFocusAndPanelHooks(ClassLoader cl, Class<?> launcherClass) {
        try {
            Class<?> deviceConfig = Class.forName("com.miui.home.launcher.DeviceConfig", false, cl);
            HookUtil.hookMethod(deviceConfig, "setControlPanelExpanded", new Class<?>[]{boolean.class},
                    chain -> {
                        Object r = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        boolean expanded = Boolean.TRUE.equals(chain.getArgs().get(0));
                        systemUiPanelExpanded = expanded;
                        DockLiquidGlassView glass = glassProvider.get();
                        if (glass != null) glass.setSystemUiPanelExpanded(expanded);
                        logger.accept("[DC] liquid SystemUI panel expanded=" + expanded);
                        return r;
                    });
        } catch (Throwable e) {
            logger.accept("[DC] SystemUI panel capture gate unavailable: " + e);
        }

        // Window focus only distinguishes HOME from an external APP after the current
        // top task agrees. Launcher-owned ALL_APPS/RECENTS state remains higher priority.
        try {
            HookUtil.hookMethod(launcherClass, "onWindowFocusChanged", new Class<?>[]{boolean.class},
                    chain -> {
                        Object r = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        boolean hasFocus = Boolean.TRUE.equals(chain.getArgs().get(0));
                        DockLiquidGlassView glass = glassProvider.get();
                        if (glass != null
                                && (glass.isAllAppsActive() || glass.isOverviewActive())) {
                            logger.accept("[DC] liquid focus ignored while Launcher-owned scene is active: "
                                    + hasFocus);
                            return r;
                        }
                        logger.accept("[DC] liquid focus hint: " + hasFocus);
                        if (!hasFocus) {
                            launcherLifecycleKnown = true;
                            launcherResumed = false;
                            if (glass != null) {
                                glass.onLauncherFocusLost();
                                glass.refreshForegroundAppLayer();
                                glass.setLauncherState(true, false);
                                glass.prearmAppBackdrop("focus-loss");
                            }
                        } else if (!confirmLauncherHomeFocus(
                                chain.getThisObject(), glass, "focus-gain")) {
                            scheduleLauncherHomeFocusRecheck(chain.getThisObject(), glass);
                        }
                        return r;
                    });
        } catch (Throwable e) {
            logger.accept("[DC] onWindowFocusChanged hook failed: " + e);
        }
    }

    void installStateHooks(ClassLoader cl, Class<?> launcherClass) {
        hookDockGestureTarget(cl, "GestureToHome", "HOME");
        hookDockGestureTarget(cl, "GestureToApp", "APP");
        hookDockGestureTarget(cl, "GestureToRecent", "RECENTS");

        boolean stockRecentsState = recentsStateHooks.installStock(cl);
        if (!stockRecentsState) recentsStateHooks.installFallback(cl);
        allAppsStateHooks.install(cl);

        try {
            HookUtil.hookMethod(launcherClass, "showOrHideRecent", new Class<?>[0],
                    chain -> {
                        DockLiquidGlassView glass = glassProvider.get();
                        if (workstationModeProvider.getAsBoolean() && glass != null) {
                            glass.onWorkstationRecentsButton();
                            logger.accept("[DC] workstation Recents button boundary");
                        }
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });
        } catch (Throwable e) {
            logger.accept("[DC] workstation showOrHideRecent hook unavailable: " + e);
        }
    }

    void installLifecycleHooks(Class<?> launcherClass) {
        boolean directLifecycleHooked = false;
        try {
            HookUtil.hookMethod(launcherClass, "onResume", new Class<?>[0],
                    chain -> {
                        Object r = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        logger.accept("[DC] liquid lifecycle: onResume (focus decides)");
                        return r;
                    });
            HookUtil.hookMethod(launcherClass, "onPause", new Class<?>[0],
                    chain -> {
                        logger.accept("[DC] liquid lifecycle: onPause (focus decides)");
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });
            HookUtil.hookMethod(launcherClass, "onStart", new Class<?>[0],
                    chain -> {
                        Object r = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        logger.accept("[DC] liquid lifecycle: onStart (visibility decides)");
                        return r;
                    });
            HookUtil.hookMethod(launcherClass, "onStop", new Class<?>[0],
                    chain -> {
                        logger.accept("[DC] liquid lifecycle: onStop (visibility decides)");
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });
            directLifecycleHooked = true;
        } catch (Throwable directError) {
            logger.accept("[DC] Launcher lifecycle direct hook unavailable: " + directError);
        }

        if (!directLifecycleHooked) {
            try {
                HookUtil.hookMethod(Activity.class, "onResume", new Class<?>[0],
                        chain -> {
                            Object r = chain.proceed(chain.getArgs().toArray(new Object[0]));
                            if (launcherClass.isInstance(chain.getThisObject())) {
                                launcherLifecycleKnown = true;
                                launcherResumed = true;
                                logger.accept("[DC] liquid lifecycle fallback: onResume");
                                DockLiquidGlassView glass = glassProvider.get();
                                if (glass != null) glass.setLauncherState(true, true);
                            }
                            return r;
                        });
                HookUtil.hookMethod(Activity.class, "onPause", new Class<?>[0],
                        chain -> {
                            if (launcherClass.isInstance(chain.getThisObject())) {
                                launcherLifecycleKnown = true;
                                launcherResumed = false;
                                logger.accept("[DC] liquid lifecycle fallback: onPause");
                                DockLiquidGlassView glass = glassProvider.get();
                                if (glass != null) glass.setLauncherState(true, false);
                            }
                            return chain.proceed(chain.getArgs().toArray(new Object[0]));
                        });
            } catch (Throwable fallbackError) {
                logger.accept("[DC] Launcher lifecycle fallback hook unavailable: " + fallbackError);
            }
        }
    }

    private boolean confirmLauncherHomeFocus(Object launcher,
                                             DockLiquidGlassView glass,
                                             String reason) {
        Context context = launcher instanceof Context ? (Context) launcher : null;
        String topPackage = foregroundTaskResolver.resolveTopPackage(context);
        if (!"com.miui.home".equals(topPackage)) {
            if (topPackage != null) {
                launcherLifecycleKnown = true;
                launcherResumed = false;
                if (glass != null) glass.setLauncherState(true, false);
                logger.accept("[DC] launcher focus rejected: external task still foreground pkg="
                        + topPackage + " reason=" + reason);
            } else {
                logger.accept("[DC] launcher focus pending: top task unavailable reason=" + reason);
            }
            return false;
        }
        launcherLifecycleKnown = true;
        launcherResumed = true;
        if (glass != null) {
            glass.onAuthoritativeHomeConfirmed();
            glass.setLauncherState(true, true);
            glass.onLauncherFocused();
        }
        logger.accept("[DC] launcher focus confirmed HOME reason=" + reason);
        return true;
    }

    private void scheduleLauncherHomeFocusRecheck(Object launcher, DockLiquidGlassView glass) {
        if (!(launcher instanceof Activity) || glass == null) return;
        View decor = ((Activity) launcher).getWindow().getDecorView();
        for (long delay : new long[]{120L, 420L}) {
            decor.postDelayed(() -> {
                if (glassProvider.get() != glass || launcherResumed
                        || workstationModeProvider.getAsBoolean()
                        || glass.isAllAppsActive() || glass.isOverviewActive()
                        || !decor.hasWindowFocus()) return;
                confirmLauncherHomeFocus(launcher, glass, "focus-recheck-" + delay);
            }, delay);
        }
    }

    /** Gesture objects are prearm hints, not proof that navigation completed. */
    private void prearmGestureCaptureTarget(DockLiquidGlassView glass, String target) {
        if (glass == null || workstationModeProvider.getAsBoolean()) return;
        if ("HOME".equals(target)) {
            String topPackage = foregroundTaskResolver.resolveTopPackage(glass.getContext());
            boolean externalConfirmed = topPackage != null && !"com.miui.home".equals(topPackage);
            boolean homeUnconfirmed = topPackage == null && !launcherResumed;
            if (externalConfirmed || homeUnconfirmed) {
                glass.prearmAppBackdrop("gesture-home-unconfirmed");
                glass.requestCapture("gesture-home-live-prearm");
                logger.accept("[DC] gesture HOME kept live while external task foreground/unconfirmed pkg="
                        + topPackage);
                return;
            }
        }
        glass.setGestureCaptureTarget(target);
    }

    private void hookDockGestureTarget(ClassLoader cl, String eventName, String target) {
        try {
            Class<?> eventClass = Class.forName("com.miui.home.launcher.dock.v3." + eventName, false, cl);
            for (Constructor<?> ctor : eventClass.getDeclaredConstructors()) {
                HookUtil.hook(ctor, chain -> {
                    Object r = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    DockLiquidGlassView glass = glassProvider.get();
                    if (glass != null && !workstationModeProvider.getAsBoolean())
                        prearmGestureCaptureTarget(glass, target);
                    if (!workstationModeProvider.getAsBoolean())
                        logger.accept("[DC] liquid gesture prearm=" + target);
                    return r;
                });
            }
        } catch (Throwable e) {
            logger.accept("[DC] " + eventName + " capture hook unavailable: " + e);
        }
    }
}
