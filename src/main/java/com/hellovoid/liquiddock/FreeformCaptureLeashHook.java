package com.hellovoid.liquiddock;

import android.graphics.Rect;
import android.view.SurfaceControl;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Final fail-closed gate immediately before LiquidDock submits a mode-1 display capture. */
final class FreeformCaptureLeashHook {
    private static final AtomicBoolean INSTALL_ATTEMPTED = new AtomicBoolean();
    // Dynamic APP capture can submit many mode-1 requests per second. Diagnostics are useful
    // only at a semantic boundary, so emit one line when this final gate's state changes rather
    // than one line per frame.
    private static final AtomicReference<String> LAST_GATE_LOG_SIGNATURE =
            new AtomicReference<>("");

    private FreeformCaptureLeashHook() {}

    static void install() {
        if (!INSTALL_ATTEMPTED.compareAndSet(false, true)) return;
        FreeformLeashRuntime.setCaptureGateInstalled(false);
        try {
            Method method = LiveScreenCapture.class.getDeclaredMethod(
                    "captureScreenAsync",
                    Rect.class,
                    float.class,
                    int.class,
                    SurfaceControl[].class,
                    String[].class,
                    int.class,
                    LiveScreenCapture.CaptureCallback.class);
            HookUtil.hook(method, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (!(args[5] instanceof Integer) || ((Integer) args[5]) != 1) {
                    return chain.proceed(args);
                }

                FreeformTaskLeashResolver.Resolution resolution = null;
                int displayId = args[2] instanceof Integer ? (Integer) args[2] : -1;
                try {
                    try {
                        resolution = FreeformLeashRuntime.resolveForCapture(displayId);
                        boolean visibleFreeform = resolution.hasVisibleFreeformTasks();
                        boolean safe = resolution.isSafe();
                        int remoteLeashCount = 0;
                        String action = "PASS_THROUGH";

                        if (visibleFreeform) {
                            SurfaceControl[] remote = resolution.borrowedRemoteLeashes();
                            remoteLeashCount = remote != null ? remote.length : 0;
                            if (!safe || !allValid(remote)) {
                                args[3] = null;
                                args[4] = null;
                                args[5] = 2;
                                action = "WALLPAPER_FAIL_CLOSED";
                            } else {
                                SurfaceControl[] existing = args[3] instanceof SurfaceControl[]
                                        ? (SurfaceControl[]) args[3] : null;
                                args[3] = merge(existing, remote);
                                action = "EXCLUDE_TASK_LEASHES";
                            }
                        }
                        logGateStateIfChanged(displayId, visibleFreeform, safe,
                                remoteLeashCount, action);
                    } catch (Throwable gateError) {
                        logGateStateIfChanged(displayId, true, false, 0,
                                "ERROR_WALLPAPER_" + gateError.getClass().getSimpleName());
                        args[3] = null;
                        args[4] = null;
                        args[5] = 2;
                    }
                    // The original LiquidDock capture method is invoked exactly once. Errors
                    // from the capture implementation itself are not mistaken for gate errors.
                    return chain.proceed(args);
                } finally {
                    if (resolution != null) {
                        try { resolution.close(); } catch (Throwable ignored) {}
                    }
                }
            });
            FreeformLeashRuntime.setCaptureGateInstalled(true);
            Api101Bridge.log("[DC] freeform task-leash capture gate installed");
        } catch (Throwable error) {
            FreeformLeashRuntime.setCaptureGateInstalled(false);
            Api101Bridge.log("[DC] freeform task-leash capture gate unavailable", error);
        }
    }

    private static void logGateStateIfChanged(int displayId, boolean visibleFreeform,
                                              boolean safe, int remoteLeashes,
                                              String action) {
        String signature = "display=" + displayId
                + " visibleFreeform=" + visibleFreeform
                + " safe=" + safe
                + " remoteLeashes=" + remoteLeashes
                + " action=" + action
                + " miuix307=" + Miuix307MaterialPipeline.isInstalled();
        while (true) {
            String previous = LAST_GATE_LOG_SIGNATURE.get();
            if (signature.equals(previous)) return;
            if (LAST_GATE_LOG_SIGNATURE.compareAndSet(previous, signature)) {
                Api101Bridge.log("[DC] freeform capture gate " + signature);
                return;
            }
        }
    }

    private static boolean allValid(SurfaceControl[] surfaces) {
        if (surfaces == null || surfaces.length == 0) return false;
        for (SurfaceControl surface : surfaces) {
            if (surface == null || !surface.isValid()) return false;
        }
        return true;
    }

    private static SurfaceControl[] merge(SurfaceControl[] existing, SurfaceControl[] remote) {
        ArrayList<SurfaceControl> result = new ArrayList<>();
        if (existing != null) {
            for (SurfaceControl surface : existing) {
                if (surface != null && !result.contains(surface)) result.add(surface);
            }
        }
        if (remote != null) {
            for (SurfaceControl surface : remote) {
                if (surface != null && !result.contains(surface)) result.add(surface);
            }
        }
        return result.isEmpty() ? null : result.toArray(new SurfaceControl[0]);
    }
}
