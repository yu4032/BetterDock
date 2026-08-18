package com.hellovoid.liquiddock;

import android.os.IBinder;
import android.os.Parcel;
import android.view.SurfaceControl;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Always-visible Launcher-side device diagnostics.
 *
 * MainHook.log() intentionally obeys the user debugLog switch, which made the first diagnostic
 * build silent on the target device. These probes bypass that switch and write through
 * Api101Bridge.log(). They are observation-only: no capture, hold, scene, or drag state is changed.
 */
final class AlwaysOnDiagnosticTrace {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private AlwaysOnDiagnosticTrace() {}

    static void installLauncher(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        installBinderReceiveTrace();
        installTransitionTrace();
        installDragTrace(classLoader);
        Api101Bridge.log("[DC][TR] launcher always-on diagnostics installed");
    }

    private static void installBinderReceiveTrace() {
        try {
            Field field = SystemUiTransitionRuntime.class.getDeclaredField("callback");
            field.setAccessible(true);
            Object callback = field.get(null);
            if (callback == null) throw new IllegalStateException("transition callback is null");
            HookUtil.hookMethod(callback.getClass(), "onTransact",
                    new Class<?>[]{int.class, Parcel.class, Parcel.class, int.class}, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Api101Bridge.log("[DC][TR-RX] onTransact code=" + arg(args, 0)
                                + " expected=" + SystemUiTransitionProtocol.TRANSACTION_EVENT
                                + " flags=" + arg(args, 3));
                        Object result = chain.proceed(args);
                        Api101Bridge.log("[DC][TR-RX] onTransact result=" + result
                                + " code=" + arg(args, 0));
                        return result;
                    });
        } catch (Throwable error) {
            Api101Bridge.log("[DC][TR-RX] binder receive diagnostic unavailable", error);
        }
    }

    private static void installTransitionTrace() {
        try {
            HookUtil.hookMethod(SystemUiTransitionRuntime.class, "onProviderChanged",
                    new Class<?>[]{IBinder.class}, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        IBinder binder = arg(args, 0) instanceof IBinder ? (IBinder) arg(args, 0) : null;
                        Api101Bridge.log("[DC][TR] provider changed connected=" + (binder != null)
                                + " binder=" + binderState(binder));
                        return chain.proceed(args);
                    });

            HookUtil.hookMethod(SystemUiTransitionRuntime.class, "registerCallback",
                    new Class<?>[]{IBinder.class}, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        IBinder binder = arg(args, 0) instanceof IBinder ? (IBinder) arg(args, 0) : null;
                        Api101Bridge.log("[DC][TR] callback registration send provider="
                                + binderState(binder));
                        Object result = chain.proceed(args);
                        Api101Bridge.log("[DC][TR] callback registration returned provider="
                                + binderState(binder));
                        return result;
                    });

            HookUtil.hookMethod(SystemUiTransitionRuntime.class, "dispatch",
                    new Class<?>[]{int.class, long.class, long.class, long.class, int.class},
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Api101Bridge.log("[DC][TR] callback received type=" + arg(args, 0)
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
                        Api101Bridge.log("[DC][TR] hold begin enter generation=" + arg(args, 0)
                                + " token=" + arg(args, 1) + " display=" + arg(args, 2)
                                + " before=" + runtimeState());
                        Object result = chain.proceed(args);
                        Api101Bridge.log("[DC][TR] hold begin returned after=" + runtimeState());
                        return result;
                    });

            HookUtil.hookMethod(SystemUiTransitionRuntime.class,
                    "finishAppToLauncherVisualHold",
                    new Class<?>[]{long.class, long.class, boolean.class}, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Api101Bridge.log("[DC][TR] hold finish enter generation=" + arg(args, 0)
                                + " token=" + arg(args, 1) + " aborted=" + arg(args, 2)
                                + " before=" + runtimeState());
                        Object result = chain.proceed(args);
                        Api101Bridge.log("[DC][TR] hold finish returned after=" + runtimeState());
                        return result;
                    });
        } catch (Throwable error) {
            Api101Bridge.log("[DC][TR] always-on transition diagnostic unavailable", error);
        }
    }

    private static void installDragTrace(ClassLoader classLoader) {
        try {
            Class<?> dragObjectClass = Class.forName(
                    "com.miui.home.launcher.DragObject", false, classLoader);
            HookUtil.hookMethod(dragObjectClass, "onDropAnimationFinished", new Class<?>[0],
                    chain -> {
                        Object dragObject = chain.getThisObject();
                        Api101Bridge.log("[DC][DRAG] vendor finish enter object=" + objectId(dragObject)
                                + " counter=" + instanceField(dragObject, "mDropAnimationCounter")
                                + " hook=" + dragState());
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        Api101Bridge.log("[DC][DRAG] vendor finish exit object=" + objectId(dragObject)
                                + " counter=" + instanceField(dragObject, "mDropAnimationCounter")
                                + " hook=" + dragState());
                        return result;
                    });
        } catch (Throwable error) {
            Api101Bridge.log("[DC][DRAG] always-on vendor diagnostic unavailable", error);
        }

        try {
            HookUtil.hookMethod(Miuix307DragCaptureHook.class, "finishDropSettling",
                    new Class<?>[]{String.class, boolean.class}, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Api101Bridge.log("[DC][DRAG] finish settling enter reason=" + arg(args, 0)
                                + " vendor=" + arg(args, 1) + " hook=" + dragState());
                        Object result = chain.proceed(args);
                        Api101Bridge.log("[DC][DRAG] finish settling returned hook=" + dragState());
                        return result;
                    });

            HookUtil.hookMethod(Miuix307DragCaptureHook.class, "finishDockDragCapture",
                    new Class<?>[]{String.class}, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object glass = invokeStatic(Miuix307DragCaptureHook.class, "currentGlass");
                        Api101Bridge.log("[DC][DRAG] finish capture glass=" + objectId(glass)
                                + " reason=" + arg(args, 0) + " before=" + glassState(glass));
                        Object result = chain.proceed(args);
                        Object afterGlass = invokeStatic(Miuix307DragCaptureHook.class, "currentGlass");
                        Api101Bridge.log("[DC][DRAG] finish capture returned glass="
                                + objectId(afterGlass) + " after=" + glassState(afterGlass));
                        return result;
                    });

            HookUtil.hookMethod(DockLiquidGlassView.class, "setDockDragging",
                    new Class<?>[]{boolean.class, String.class, SurfaceControl.class}, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object glass = chain.getThisObject();
                        Api101Bridge.log("[DC][DRAG] setDockDragging enter dragging=" + arg(args, 0)
                                + " before=" + glassState(glass));
                        Object result = chain.proceed(args);
                        Api101Bridge.log("[DC][DRAG] setDockDragging returned dragging=" + arg(args, 0)
                                + " after=" + glassState(glass));
                        return result;
                    });
        } catch (Throwable error) {
            Api101Bridge.log("[DC][DRAG] always-on release diagnostic unavailable", error);
        }
    }

    private static String runtimeState() {
        return "sourceGen=" + staticField(SystemUiTransitionRuntime.class, "sourceGeneration")
                + ",token=" + staticField(SystemUiTransitionRuntime.class, "activeTokenId")
                + ",display=" + staticField(SystemUiTransitionRuntime.class, "activeDisplayId")
                + ",hold=" + staticField(SystemUiTransitionRuntime.class, "visualHold")
                + ",view=" + objectId(weakRefValue(
                        staticField(SystemUiTransitionRuntime.class, "currentView")));
    }

    private static String dragState() {
        return "session=" + staticField(Miuix307DragCaptureHook.class, "dragSessionId")
                + ",active=" + staticField(Miuix307DragCaptureHook.class, "dragActive")
                + ",settling=" + staticField(Miuix307DragCaptureHook.class, "dropSettling")
                + ",releaseScheduled=" + staticField(
                        Miuix307DragCaptureHook.class, "dropReleaseScheduled")
                + ",callbacks=" + staticField(
                        Miuix307DragCaptureHook.class, "settlingDropCallbacksRemaining")
                + ",systemDrag=" + staticField(
                        Miuix307DragCaptureHook.class, "systemDockDragActive");
    }

    private static String glassState(Object glass) {
        if (glass == null) return "null";
        return "dockDragging=" + instanceField(glass, "dockDragging")
                + ",dragFrozen=" + instanceField(glass, "dockDragCaptureFrozen")
                + ",systemDrag=" + instanceField(glass, "systemDockDragActive")
                + ",capturing=" + instanceField(glass, "capturing")
                + ",kick=" + instanceField(glass, "captureKickScheduled")
                + ",dirty=" + instanceField(glass, "captureSourceDirty")
                + ",runtime=" + instanceField(glass, "runtimeGlassEnabled")
                + ",circuit=" + instanceField(glass, "captureCircuitOpen")
                + ",panel=" + instanceField(glass, "systemUiPanelExpanded")
                + ",attached=" + ((android.view.View) glass).isAttachedToWindow()
                + ",shown=" + ((android.view.View) glass).isShown();
    }

    private static Object weakRefValue(Object value) {
        return value instanceof java.lang.ref.WeakReference
                ? ((java.lang.ref.WeakReference<?>) value).get() : null;
    }

    private static Object invokeStatic(Class<?> owner, String methodName, Object... args) {
        try { return HookUtil.invokeStatic(owner, methodName, args); }
        catch (Throwable ignored) { return null; }
    }

    private static Object staticField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        } catch (Throwable error) {
            return "<field-error:" + name + ">";
        }
    }

    private static Object instanceField(Object owner, String name) {
        if (owner == null) return null;
        try {
            Field field = HookUtil.findField(owner.getClass(), name);
            return field.get(owner);
        } catch (Throwable error) {
            return "<field-error:" + name + ">";
        }
    }

    private static String binderState(IBinder binder) {
        if (binder == null) return "null";
        return objectId(binder) + "/alive=" + binder.isBinderAlive()
                + "/ping=" + binder.pingBinder();
    }

    private static String objectId(Object value) {
        if (value == null) return "null";
        return value.getClass().getSimpleName() + "@"
                + Integer.toHexString(System.identityHashCode(value));
    }

    private static Object arg(Object[] args, int index) {
        return args != null && index >= 0 && index < args.length ? args[index] : null;
    }
}
