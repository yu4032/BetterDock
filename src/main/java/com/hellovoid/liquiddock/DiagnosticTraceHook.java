package com.hellovoid.liquiddock;

import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Display;
import android.view.SurfaceControl;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Behavior-neutral diagnostic probes for the two device-only failures currently under study:
 * SystemUI -> Launcher transition delivery and 307 Dock drag-freeze release.
 *
 * This class only observes existing method boundaries and private state. It does not request a
 * capture, mutate transition ownership, release a hold, or alter any drag/capture gate.
 */
final class DiagnosticTraceHook {
    private static final AtomicBoolean SYSTEM_UI_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean LAUNCHER_INSTALLED = new AtomicBoolean();
    private static Handler mainHandler;

    private DiagnosticTraceHook() {}

    static void installSystemUi(ClassLoader classLoader) {
        if (!SYSTEM_UI_INSTALLED.compareAndSet(false, true)) return;
        installSystemUiObserverTrace();
        installSystemUiClassifierTrace();
        installSystemUiCallbackTrace();
        Api101Bridge.log("[DC][TR-SRC] diagnostic probes installed");
    }

    static void installLauncher(ClassLoader classLoader) {
        if (!LAUNCHER_INSTALLED.compareAndSet(false, true)) return;
        installLauncherTransitionTrace();
        installDragTrace(classLoader);
        MainHook.log("[DC][TR] launcher diagnostic probes installed");
    }

    private static void installSystemUiObserverTrace() {
        try {
            HookUtil.hookMethod(SystemUiTransitionSource.class, "observeTransitions",
                    new Class<?>[]{Object.class, Class.class}, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object result = chain.proceed(args);
                        Api101Bridge.log("[DC][TR-SRC] observer registered transitions="
                                + objectId(arg(args, 0)) + " observer="
                                + objectId(staticField(SystemUiTransitionSource.class,
                                        "observerProxy")));
                        return result;
                    });

            HookUtil.hookMethod(SystemUiTransitionSource.class, "onTransitionReady",
                    new Class<?>[]{Object[].class}, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object[] callbackArgs = arg(args, 0) instanceof Object[]
                                ? (Object[]) arg(args, 0) : null;
                        IBinder token = callbackArgs != null && callbackArgs.length > 0
                                && callbackArgs[0] instanceof IBinder
                                ? (IBinder) callbackArgs[0] : null;
                        Api101Bridge.log("[DC][TR-SRC] ready token=" + objectId(token)
                                + " rawArgs=" + (callbackArgs != null ? callbackArgs.length : -1)
                                + " callback=" + binderState((IBinder) staticField(
                                        SystemUiTransitionSource.class, "launcherCallback")));
                        return chain.proceed(args);
                    });
        } catch (Throwable error) {
            Api101Bridge.log("[DC][TR-SRC] observer diagnostic hook unavailable", error);
        }
    }

    private static void installSystemUiClassifierTrace() {
        try {
            HookUtil.hookMethod(SystemUiTransitionPolicy.class, "classify",
                    new Class<?>[]{List.class}, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object result = chain.proceed(args);
                        List<?> changes = arg(args, 0) instanceof List ? (List<?>) arg(args, 0) : null;
                        Api101Bridge.log("[DC][TR-SRC] classify normalized="
                                + (changes != null ? changes.size() : -1)
                                + " kind=" + String.valueOf(result));
                        return result;
                    });
        } catch (Throwable error) {
            Api101Bridge.log("[DC][TR-SRC] classifier diagnostic hook unavailable", error);
        }
    }

    private static void installSystemUiCallbackTrace() {
        try {
            HookUtil.hookMethod(SystemUiTransitionSource.class, "setLauncherCallback",
                    new Class<?>[]{IBinder.class}, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object result = chain.proceed(args);
                        IBinder callback = arg(args, 0) instanceof IBinder
                                ? (IBinder) arg(args, 0) : null;
                        Api101Bridge.log("[DC][TR-SRC] callback registered binder="
                                + binderState(callback));
                        return result;
                    });

            HookUtil.hookMethod(SystemUiTransitionSource.class, "pushTransitionEvent",
                    new Class<?>[]{int.class, long.class, long.class, int.class}, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        IBinder callback = (IBinder) staticField(
                                SystemUiTransitionSource.class, "launcherCallback");
                        Api101Bridge.log("[DC][TR-SRC] push type=" + arg(args, 0)
                                + " token=" + arg(args, 1)
                                + " other=" + arg(args, 2)
                                + " display=" + arg(args, 3)
                                + " callback=" + binderState(callback));
                        Object result = chain.proceed(args);
                        IBinder after = (IBinder) staticField(
                                SystemUiTransitionSource.class, "launcherCallback");
                        if (callback != null && after == null) {
                            Api101Bridge.log("[DC][TR-SRC] push cleared dead callback type="
                                    + arg(args, 0));
                        }
                        return result;
                    });
        } catch (Throwable error) {
            Api101Bridge.log("[DC][TR-SRC] callback diagnostic hook unavailable", error);
        }
    }

    private static void installLauncherTransitionTrace() {
        try {
            HookUtil.hookMethod(SystemUiTransitionRuntime.class, "onProviderChanged",
                    new Class<?>[]{IBinder.class}, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        IBinder next = arg(args, 0) instanceof IBinder ? (IBinder) arg(args, 0) : null;
                        MainHook.log("[DC][TR] provider changed connected=" + (next != null)
                                + " binder=" + binderState(next));
                        return chain.proceed(args);
                    });

            HookUtil.hookMethod(SystemUiTransitionRuntime.class, "registerCallback",
                    new Class<?>[]{IBinder.class}, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        IBinder target = arg(args, 0) instanceof IBinder
                                ? (IBinder) arg(args, 0) : null;
                        Object result = chain.proceed(args);
                        // Remote acceptance is authoritatively confirmed by the matching
                        // [TR-SRC] callback-registered line in SystemUI.
                        MainHook.log("[DC][TR] callback registration accepted=await-source-confirmation"
                                + " provider=" + binderState(target));
                        return result;
                    });

            HookUtil.hookMethod(SystemUiTransitionRuntime.class, "dispatch",
                    new Class<?>[]{int.class, long.class, long.class, long.class, int.class},
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        MainHook.log("[DC][TR] callback received type=" + arg(args, 0)
                                + " generation=" + arg(args, 1)
                                + " token=" + arg(args, 2)
                                + " other=" + arg(args, 3)
                                + " display=" + arg(args, 4));
                        return chain.proceed(args);
                    });

            HookUtil.hookMethod(SystemUiTransitionRuntime.class,
                    "beginAppToLauncherVisualHold",
                    new Class<?>[]{long.class, long.class, int.class}, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        MainHook.log("[DC][TR] hold request generation=" + arg(args, 0)
                                + " token=" + arg(args, 1)
                                + " display=" + arg(args, 2)
                                + " state=" + transitionRuntimeState());
                        Object result = chain.proceed(args);
                        postMain(() -> MainHook.log("[DC][TR] hold resolved active="
                                + staticBoolean(SystemUiTransitionRuntime.class, "visualHold")
                                + " state=" + transitionRuntimeState()));
                        return result;
                    });

            HookUtil.hookMethod(SystemUiTransitionRuntime.class, "acceptGeneration",
                    new Class<?>[]{long.class}, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object result = chain.proceed(args);
                        MainHook.log("[DC][TR] generation check input=" + arg(args, 0)
                                + " accepted=" + result
                                + " source=" + staticField(SystemUiTransitionRuntime.class,
                                        "sourceGeneration"));
                        return result;
                    });

            HookUtil.hookMethod(SystemUiTransitionRuntime.class, "matchesDisplay",
                    new Class<?>[]{DockLiquidGlassView.class, int.class}, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object result = chain.proceed(args);
                        DockLiquidGlassView glass = arg(args, 0) instanceof DockLiquidGlassView
                                ? (DockLiquidGlassView) arg(args, 0) : null;
                        MainHook.log("[DC][TR] display check requested=" + arg(args, 1)
                                + " actual=" + displayId(glass) + " matched=" + result);
                        return result;
                    });
        } catch (Throwable error) {
            MainHook.log("[DC][TR] launcher transition diagnostic hook unavailable: " + error);
        }
    }

    private static void installDragTrace(ClassLoader classLoader) {
        try {
            Class<?> dragObjectClass = Class.forName(
                    "com.miui.home.launcher.DragObject", false, classLoader);
            HookUtil.hookMethod(dragObjectClass, "onDropAnimationFinished", new Class<?>[0],
                    chain -> {
                        Object dragObject = chain.getThisObject();
                        MainHook.log("[DC][DRAG] vendor drop-finish callback enter object="
                                + objectId(dragObject) + " counter="
                                + instanceField(dragObject, "mDropAnimationCounter"));
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        MainHook.log("[DC][DRAG] vendor drop-finish callback exit object="
                                + objectId(dragObject) + " counter="
                                + instanceField(dragObject, "mDropAnimationCounter")
                                + " hookState=" + dragHookState());
                        return result;
                    });
        } catch (Throwable error) {
            MainHook.log("[DC][DRAG] vendor finish diagnostic hook unavailable: " + error);
        }

        try {
            HookUtil.hookMethod(Miuix307DragCaptureHook.class, "finishDropSettling",
                    new Class<?>[]{String.class, boolean.class}, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        MainHook.log("[DC][DRAG] finishDropSettling enter reason=" + arg(args, 0)
                                + " vendorFinished=" + arg(args, 1)
                                + " state=" + dragHookState());
                        Object result = chain.proceed(args);
                        MainHook.log("[DC][DRAG] finishDropSettling exit state="
                                + dragHookState());
                        return result;
                    });

            HookUtil.hookMethod(Miuix307DragCaptureHook.class, "finishDockDragCapture",
                    new Class<?>[]{String.class}, chain -> {
                        DockLiquidGlassView glass = currentGlass();
                        MainHook.log("[DC][DRAG] finish capture glass=" + objectId(glass)
                                + " reason=" + arg(chain.getArgs().toArray(new Object[0]), 0));
                        MainHook.log("[DC][DRAG] state before=" + glassState(glass));
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        MainHook.log("[DC][DRAG] state after=" + glassState(currentGlass()));
                        return result;
                    });

            HookUtil.hookMethod(DockLiquidGlassView.class, "setDockDragging",
                    new Class<?>[]{boolean.class, String.class, SurfaceControl.class}, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        DockLiquidGlassView glass = chain.getThisObject() instanceof DockLiquidGlassView
                                ? (DockLiquidGlassView) chain.getThisObject() : null;
                        MainHook.log("[DC][DRAG] setDockDragging before dragging=" + arg(args, 0)
                                + " state=" + glassState(glass));
                        Object result = chain.proceed(args);
                        MainHook.log("[DC][DRAG] setDockDragging after dragging=" + arg(args, 0)
                                + " state=" + glassState(glass));
                        return result;
                    });
        } catch (Throwable error) {
            MainHook.log("[DC][DRAG] LiquidDock release diagnostic hook unavailable: " + error);
        }
    }

    private static String transitionRuntimeState() {
        Object glass = null;
        Object ref = staticField(SystemUiTransitionRuntime.class, "currentView");
        if (ref instanceof java.lang.ref.WeakReference) {
            glass = ((java.lang.ref.WeakReference<?>) ref).get();
        }
        return "view=" + objectId(glass)
                + " viewDisplay=" + displayId(glass instanceof DockLiquidGlassView
                        ? (DockLiquidGlassView) glass : null)
                + " sourceGen=" + staticField(SystemUiTransitionRuntime.class, "sourceGeneration")
                + " activeToken=" + staticField(SystemUiTransitionRuntime.class, "activeTokenId")
                + " activeDisplay=" + staticField(SystemUiTransitionRuntime.class, "activeDisplayId")
                + " hold=" + staticField(SystemUiTransitionRuntime.class, "visualHold");
    }

    private static String dragHookState() {
        return "session=" + staticField(Miuix307DragCaptureHook.class, "dragSessionId")
                + " dragActive=" + staticField(Miuix307DragCaptureHook.class, "dragActive")
                + " settling=" + staticField(Miuix307DragCaptureHook.class, "dropSettling")
                + " releaseScheduled=" + staticField(
                        Miuix307DragCaptureHook.class, "dropReleaseScheduled")
                + " callbacks=" + staticField(
                        Miuix307DragCaptureHook.class, "settlingDropCallbacksRemaining")
                + " systemDrag=" + staticField(
                        Miuix307DragCaptureHook.class, "systemDockDragActive");
    }

    private static DockLiquidGlassView currentGlass() {
        Object value = HookUtil.invokeStatic(Miuix307DragCaptureHook.class, "currentGlass");
        return value instanceof DockLiquidGlassView ? (DockLiquidGlassView) value : null;
    }

    private static String glassState(DockLiquidGlassView glass) {
        if (glass == null) return "null";
        boolean runtime = booleanField(glass, "runtimeGlassEnabled");
        boolean circuit = booleanField(glass, "captureCircuitOpen");
        boolean panel = booleanField(glass, "systemUiPanelExpanded");
        boolean systemDrag = booleanField(glass, "systemDockDragActive");
        boolean dragFrozen = booleanField(glass, "dockDragCaptureFrozen");
        boolean workstationSuspended = false;
        Object sceneState = instanceField(glass, "sceneState");
        if (sceneState != null) {
            Object value = HookUtil.invoke(sceneState, "workstationSuspended");
            workstationSuspended = Boolean.TRUE.equals(value);
        }
        boolean interactive = true;
        Object powerManager = instanceField(glass, "powerManager");
        if (powerManager instanceof android.os.PowerManager) {
            interactive = ((android.os.PowerManager) powerManager).isInteractive();
        }
        boolean baseAllowed = runtime && !circuit && !panel && !systemDrag
                && !dragFrozen && !workstationSuspended && interactive;
        return "dragging=" + booleanField(glass, "dockDragging")
                + " dragFrozen=" + dragFrozen
                + " systemDrag=" + systemDrag
                + " capturing=" + booleanField(glass, "capturing")
                + " kick=" + booleanField(glass, "kickScheduled")
                + " dirty=" + booleanField(glass, "sourceDirty")
                + " runtime=" + runtime
                + " circuit=" + circuit
                + " panel=" + panel
                + " workstationSuspended=" + workstationSuspended
                + " interactive=" + interactive
                + " attached=" + booleanField(glass, "attached")
                + " windowVisible=" + booleanField(glass, "windowVisible")
                + " shown=" + safeShown(glass)
                + " appPrearm=" + booleanField(glass, "appBackdropPrearmActive")
                + " overview=" + booleanField(glass, "overviewActive")
                + " allowed=" + (baseAllowed ? "base-yes" : "base-no");
    }

    private static int displayId(DockLiquidGlassView glass) {
        if (glass == null) return -1;
        try {
            Display display = glass.getDisplay();
            return display != null ? display.getDisplayId() : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static boolean safeShown(DockLiquidGlassView glass) {
        try { return glass != null && glass.isShown(); }
        catch (Throwable ignored) { return false; }
    }

    private static Object arg(Object[] args, int index) {
        return args != null && index >= 0 && index < args.length ? args[index] : null;
    }

    private static Object staticField(Class<?> type, String name) {
        try {
            Field field = HookUtil.findField(type, name);
            return field.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean staticBoolean(Class<?> type, String name) {
        Object value = staticField(type, name);
        return Boolean.TRUE.equals(value);
    }

    private static Object instanceField(Object owner, String name) {
        if (owner == null) return null;
        try { return HookUtil.getField(owner, name); }
        catch (Throwable ignored) { return null; }
    }

    private static boolean booleanField(Object owner, String name) {
        Object value = instanceField(owner, name);
        return Boolean.TRUE.equals(value);
    }

    private static String binderState(IBinder binder) {
        if (binder == null) return "null";
        boolean alive;
        try { alive = binder.isBinderAlive(); }
        catch (Throwable ignored) { alive = false; }
        return objectId(binder) + "/alive=" + alive;
    }

    private static String objectId(Object value) {
        if (value == null) return "null";
        return value.getClass().getSimpleName() + "@"
                + Integer.toHexString(System.identityHashCode(value));
    }

    private static void postMain(Runnable action) {
        Handler handler = mainHandler;
        if (handler == null) {
            synchronized (DiagnosticTraceHook.class) {
                handler = mainHandler;
                if (handler == null) {
                    handler = new Handler(Looper.getMainLooper());
                    mainHandler = handler;
                }
            }
        }
        handler.post(action);
    }
}
