package com.hellovoid.liquiddock;

import android.graphics.Rect;
import android.view.SurfaceControl;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** Final fail-closed gate immediately before LiquidDock submits a mode-1 display capture. */
final class FreeformCaptureLeashHook {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private FreeformCaptureLeashHook() {}

    static void install() {
        if (!INSTALLED.compareAndSet(false, true)) return;
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
                    int displayId = (Integer) args[2];
                    resolution = FreeformLeashRuntime.resolveForCapture(displayId);
                    if (!resolution.hasVisibleFreeformTasks()) {
                        return chain.proceed(args);
                    }
                    if (!resolution.isSafe()) {
                        // Never submit a full-display capture when any visible freeform task
                        // lacks a trusted task leash. Preserve the historical safety fallback.
                        args[3] = null;
                        args[4] = null;
                        args[5] = 2;
                        return chain.proceed(args);
                    }

                    SurfaceControl[] existing = args[3] instanceof SurfaceControl[]
                            ? (SurfaceControl[]) args[3] : null;
                    args[3] = merge(existing, resolution.borrowedRemoteLeashes());
                    return chain.proceed(args);
                } catch (Throwable error) {
                    Api101Bridge.log("[DC] freeform leash capture gate failed; wallpaper fallback", error);
                    // The gate itself must fail closed and must never break Dock capture.
                    args[3] = null;
                    args[4] = null;
                    args[5] = 2;
                    return chain.proceed(args);
                } finally {
                    if (resolution != null) {
                        try { resolution.close(); } catch (Throwable ignored) {}
                    }
                }
            });
            Api101Bridge.log("[DC] freeform task-leash capture gate installed");
        } catch (Throwable error) {
            // If this hook cannot be installed, Dock's existing preflight remains fail-closed:
            // provider readiness never becomes a resolvable freeform exclusion.
            INSTALLED.set(true);
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
