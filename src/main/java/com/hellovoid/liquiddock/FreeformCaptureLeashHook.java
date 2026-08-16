package com.hellovoid.liquiddock;

import android.graphics.Rect;
import android.view.SurfaceControl;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** Final fail-closed gate immediately before LiquidDock submits a mode-1 display capture. */
final class FreeformCaptureLeashHook {
    private static final AtomicBoolean INSTALL_ATTEMPTED = new AtomicBoolean();

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
                try {
                    try {
                        int displayId = (Integer) args[2];
                        resolution = FreeformLeashRuntime.resolveForCapture(displayId);
                        if (resolution.hasVisibleFreeformTasks()) {
                            if (!resolution.isSafe()) {
                                args[3] = null;
                                args[4] = null;
                                args[5] = 2;
                            } else {
                                SurfaceControl[] existing = args[3] instanceof SurfaceControl[]
                                        ? (SurfaceControl[]) args[3] : null;
                                args[3] = merge(existing, resolution.borrowedRemoteLeashes());
                            }
                        }
                    } catch (Throwable gateError) {
                        Api101Bridge.log(
                                "[DC] freeform leash capture gate failed; wallpaper fallback",
                                gateError);
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
