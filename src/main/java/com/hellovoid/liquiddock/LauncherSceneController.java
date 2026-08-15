package com.hellovoid.liquiddock;

import android.app.Activity;
import android.content.Context;
import android.view.View;

import java.lang.reflect.Constructor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owns Launcher scene arbitration and stock scene hooks. */
final class LauncherSceneController {
    private static final String MODULE_PACKAGE = "com.hellovoid.liquiddock";
    private final Supplier<DockLiquidGlassView> glassProvider;
    private final BooleanSupplier workstationModeProvider;
    private final Consumer<String> logger;
    private final ForegroundTaskResolver foregroundTaskResolver;
    private final RecentsStateHooks recentsStateHooks;
    private final AllAppsStateHooks allAppsStateHooks;
    private final ForegroundAuthorityGate foregroundAuthorityGate =
            new ForegroundAuthorityGate();
    private volatile ForegroundOwnership foregroundOwnership =
            ForegroundOwnership.UNKNOWN;

    private volatile boolean launcherResumed;
    private volatile boolean launcherLifecycleKnown;
    private volatile boolean launcherAwayObserved;
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

    private void updateModuleSettingsForeground(
            ForegroundTaskResolver.Observation observation,
            DockLiquidGlassView glass,
            String reason) {
        if (glass == null || observation == null || observation.packageName == null) return;
        boolean foreground = MODULE_PACKAGE.equals(observation.packageName);
        glass.setModuleSettingsForeground(foreground);
        if (foreground) {
            logger.accept("[DC] module settings foreground; FULL_DISPLAY capture paused reason="
                    + reason);
        }
    }

    /** Apply one task snapshot through the evidence gate.  The task list is only a
     * sample: callers must explicitly grant the boundary that is allowed to change authority. */
    private ForegroundOwnership applyForegroundObservation(
            ForegroundTaskResolver.Observation observation,
            DockLiquidGlassView glass,
            String reason,
            boolean allowHomeCommit,
            boolean allowExternalCommit) {
        ForegroundOwnership observed = observation == null
                ? ForegroundOwnership.UNKNOWN : observation.ownership;
        updateModuleSettingsForeground(observation, glass, reason);
        boolean returningFromExternal = launcherAwayObserved
                || foregroundOwnership == ForegroundOwnership.EXTERNAL
                || (glass != null && glass.hasExternalForegroundAuthority());
        ForegroundOwnership filtered = foregroundAuthorityGate.filter(
                foregroundOwnership, observed, allowHomeCommit, allowExternalCommit,
                returningFromExternal, System.nanoTime());

        if (filtered == foregroundOwnership) {
            if (observed == ForegroundOwnership.UNKNOWN) {
                if (glass != null && launcherAwayObserved) glass.setLauncherAwayHint(true);
                logger.accept("[DC] foreground authority UNKNOWN; leave authority unchanged reason="
                        + reason + " away=" + launcherAwayObserved);
            } else if (observed != filtered) {
                logger.accept("[DC] foreground observation suppressed observed=" + observed
                        + " committed=" + foregroundOwnership + " reason=" + reason
                        + " allowHome=" + allowHomeCommit
                        + " allowExternal=" + allowExternalCommit);
            }
            return filtered;
        }

        foregroundOwnership = filtered;
        launcherLifecycleKnown = true;
        launcherResumed = filtered == ForegroundOwnership.HOME;
        launcherAwayObserved = filtered == ForegroundOwnership.EXTERNAL;
        if (glass != null) {
            if (filtered == ForegroundOwnership.HOME) {
                glass.onAuthoritativeHomeConfirmed();
            } else if (filtered == ForegroundOwnership.EXTERNAL) {
                glass.setForegroundOwnership(ForegroundOwnership.EXTERNAL);
            }
            glass.setLauncherState(true, launcherResumed);
        }
        logger.accept("[DC] foreground authority=" + filtered
                + " observed=" + observed
                + " pkg=" + (observation == null ? null : observation.packageName)
                + " reason=" + reason);
        return filtered;
    }

    private ForegroundOwnership observeForegroundOwnership(Context context,
                                                            DockLiquidGlassView glass,
                                                            String reason,
                                                            boolean allowHomeCommit,
                                                            boolean allowExternalCommit) {
        ForegroundTaskResolver.Observation observation = foregroundTaskResolver.resolve(context);
        return applyForegroundObservation(observation, glass, reason,
                allowHomeCommit, allowExternalCommit);
    }

    /** Compatibility/default path: observations alone may not flip persistent authority. */
    ForegroundOwnership observeForegroundOwnership(Context context,
                                                   DockLiquidGlassView glass,
                                                   String reason) {
        return observeForegroundOwnership(context, glass, reason, false, false);
    }

    boolean isExternalAppForeground(Context context) {
        DockLiquidGlassView glass = glassProvider.get();
        ForegroundOwnership observed = observeForegroundOwnership(
                context, glass, "dock-interaction", false, true);
        return observed == ForegroundOwnership.EXTERNAL
                || (observed == ForegroundOwnership.UNKNOWN
                    && glass != null && glass.hasExternalForegroundAuthority());
    }

    void seed(Object launcher) {
        if (launcher == null) return;
        DockLiquidGlassView glass = glassProvider.get();
        ForegroundTaskResolver.Observation observation = launcher instanceof Context
                ? foregroundTaskResolver.resolve((Context) launcher)
                : new ForegroundTaskResolver.Observation(ForegroundOwnership.UNKNOWN, null, -1);
        try {
            Object paused = HookUtil.invoke(launcher, "isPause");
            Object visible = HookUtil.invoke(launcher, "isVisible");
            Object focused = HookUtil.invoke(launcher, "isWindowFocus");
            if (paused instanceof Boolean) {
                launcherLifecycleKnown = true;
                launcherResumed = LauncherSceneOwnershipPolicy.launcherOwnsScene(
                        !((Boolean) paused), observation.windowingMode);
                if (launcherResumed) launcherAwayObserved = false;
            }
            logger.accept("[DC] liquid lifecycle seed: known=" + launcherLifecycleKnown
                    + " resumed=" + launcherResumed + " paused=" + paused
                    + " visible=" + visible + " focus=" + focused
                    + " windowingMode=" + observation.windowingMode);
        } catch (Throwable e) {
            logger.accept("[DC] liquid lifecycle seed unavailable; using window gate: " + e);
        }
        if (glass != null && launcherAwayObserved) glass.setLauncherAwayHint(true);
        if (launcher instanceof Context) {
            boolean allowHomeCommit = launcherLifecycleKnown && launcherResumed
                    && !launcherAwayObserved;
            boolean allowExternalCommit = launcherLifecycleKnown && !launcherResumed;
            applyForegroundObservation(observation, glass, "seed",
                    allowHomeCommit, allowExternalCommit);
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
                        ForegroundTaskResolver.Observation observation =
                                chain.getThisObject() instanceof Context
                                        ? foregroundTaskResolver.resolve((Context) chain.getThisObject())
                                        : new ForegroundTaskResolver.Observation(
                                                ForegroundOwnership.UNKNOWN, null, -1);
                        updateModuleSettingsForeground(observation, glass,
                                hasFocus ? "focus-gain" : "focus-loss");
                        if (!hasFocus && LauncherSceneOwnershipPolicy.launcherOwnsScene(
                                false, observation.windowingMode)) {
                            launcherLifecycleKnown = true;
                            launcherResumed = true;
                            launcherAwayObserved = false;
                            foregroundOwnership = ForegroundOwnership.HOME;
                            foregroundAuthorityGate.resetHomeCandidate();
                            if (glass != null) {
                                glass.onAuthoritativeHomeConfirmed();
                                glass.setLauncherState(true, true);
                            }
                            logger.accept("[DC] liquid focus freeform-owned windowingMode="
                                    + observation.windowingMode);
                        } else if (!hasFocus) {
                            launcherLifecycleKnown = true;
                            launcherResumed = false;
                            launcherAwayObserved = true;
                            foregroundOwnership = ForegroundOwnership.EXTERNAL;
                            foregroundAuthorityGate.resetHomeCandidate();
                            if (glass != null) {
                                glass.onLauncherFocusLost();
                                glass.setForegroundOwnership(ForegroundOwnership.EXTERNAL);
                                glass.setLauncherAwayHint(true);
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
            HookUtil.hookMethod(launcherClass, "onResume", new Class<?>[0], chain -> {
                Object r = chain.proceed(chain.getArgs().toArray(new Object[0]));
                logger.accept("[DC] liquid lifecycle: onResume (focus decides)");
                return r;
            });
            HookUtil.hookMethod(launcherClass, "onPause", new Class<?>[0], chain -> {
                logger.accept("[DC] liquid lifecycle: onPause (focus decides)");
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            HookUtil.hookMethod(launcherClass, "onStart", new Class<?>[0], chain -> {
                Object r = chain.proceed(chain.getArgs().toArray(new Object[0]));
                logger.accept("[DC] liquid lifecycle: onStart (visibility decides)");
                return r;
            });
            HookUtil.hookMethod(launcherClass, "onStop", new Class<?>[0], chain -> {
                logger.accept("[DC] liquid lifecycle: onStop (visibility decides)");
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            directLifecycleHooked = true;
        } catch (Throwable directError) {
            logger.accept("[DC] Launcher lifecycle direct hook unavailable: " + directError);
        }

        if (!directLifecycleHooked) {
            try {
                HookUtil.hookMethod(Activity.class, "onResume", new Class<?>[0], chain -> {
                    Object r = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    if (launcherClass.isInstance(chain.getThisObject())) {
                        DockLiquidGlassView glass = glassProvider.get();
                        if (chain.getThisObject() instanceof Context) {
                            ForegroundOwnership ownership = observeForegroundOwnership(
                                    (Context) chain.getThisObject(), glass, "fallback-resume");
                            if (ownership == ForegroundOwnership.UNKNOWN) {
                                // Unknown is not proof of HOME; preserve the last authority.
                                logger.accept("[DC] lifecycle fallback resume unresolved");
                            }
                        }
                    }
                    return r;
                });
                HookUtil.hookMethod(Activity.class, "onPause", new Class<?>[0], chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    if (launcherClass.isInstance(chain.getThisObject())) {
                        DockLiquidGlassView glass = glassProvider.get();
                        ForegroundTaskResolver.Observation observation =
                                foregroundTaskResolver.resolve((Context) chain.getThisObject());
                        launcherLifecycleKnown = true;
                        if (LauncherSceneOwnershipPolicy.launcherOwnsScene(
                                false, observation.windowingMode)) {
                            launcherResumed = true;
                            launcherAwayObserved = false;
                            foregroundOwnership = ForegroundOwnership.HOME;
                            foregroundAuthorityGate.resetHomeCandidate();
                            if (glass != null) {
                                glass.onAuthoritativeHomeConfirmed();
                                glass.setLauncherState(true, true);
                            }
                            logger.accept("[DC] liquid lifecycle fallback: onPause freeform "
                                    + "windowingMode=" + observation.windowingMode);
                        } else {
                            launcherResumed = false;
                            launcherAwayObserved = true;
                            foregroundOwnership = ForegroundOwnership.EXTERNAL;
                            foregroundAuthorityGate.resetHomeCandidate();
                            logger.accept("[DC] liquid lifecycle fallback: onPause external "
                                    + "windowingMode=" + observation.windowingMode);
                            if (glass != null) {
                                glass.onLauncherFocusLost();
                                glass.setForegroundOwnership(ForegroundOwnership.EXTERNAL);
                                glass.setLauncherAwayHint(true);
                                glass.setLauncherState(true, false);
                            }
                        }
                    }
                    return result;
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
        ForegroundTaskResolver.Observation observation = foregroundTaskResolver.resolve(context);
        if (observation.ownership == ForegroundOwnership.UNKNOWN) {
            foregroundAuthorityGate.resetHomeCandidate();
            logger.accept("[DC] launcher focus pending: top task unavailable reason=" + reason);
            return false;
        }
        if (observation.ownership != ForegroundOwnership.HOME) {
            applyForegroundObservation(observation, glass,
                    "focus-home-confirm-" + reason, false, false);
            logger.accept("[DC] launcher focus rejected: observed=" + observation.ownership
                    + " pkg=" + observation.packageName + " reason=" + reason);
            return false;
        }
        ForegroundOwnership ownership = applyForegroundObservation(
                observation, glass, "focus-home-confirm-" + reason, true, false);
        if (ownership != ForegroundOwnership.HOME) {
            logger.accept("[DC] launcher HOME candidate waiting for stable confirmation reason="
                    + reason);
            return false;
        }
        if (glass != null) glass.onLauncherFocused();
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
        ForegroundTaskResolver.Observation observation =
                foregroundTaskResolver.resolve(glass.getContext());
        ForegroundOwnership ownership = observation.ownership == ForegroundOwnership.UNKNOWN
                ? foregroundOwnership
                : applyForegroundObservation(observation, glass, "gesture-" + target,
                        false, false);
        if ("HOME".equals(target)) {
            boolean externalConfirmed = ownership == ForegroundOwnership.EXTERNAL
                    || (ownership == ForegroundOwnership.UNKNOWN
                        && glass.hasExternalForegroundAuthority());
            boolean homeUnconfirmed = ownership == ForegroundOwnership.UNKNOWN && !launcherResumed;
            if (externalConfirmed || homeUnconfirmed) {
                glass.prearmAppBackdrop("gesture-home-unconfirmed");
                glass.requestCapture("gesture-home-live-prearm");
                logger.accept("[DC] gesture HOME kept live while external task foreground/unconfirmed pkg="
                        + observation.packageName);
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
