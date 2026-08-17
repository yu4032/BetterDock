package com.hellovoid.liquiddock;

import android.graphics.Rect;
import android.view.SurfaceControl;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Final freeform visibility gate immediately before LiquidDock submits a mode-1 display capture. */
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
                            // A visible freeform task changes backdrop ownership for this Dock:
                            // do not sample either the fullscreen app or the freeform task into
                            // the liquid glass. Rewrite the pending mode-1 request to the vendor
                            // wallpaper-only capture immediately. This is independent of whether
                            // SystemUI can resolve a task leash and therefore also covers the
                            // normal provider-late race.
                            args[3] = null;
                            args[4] = null;
                            args[5] = 2;
                            action = "WALLPAPER_VISIBLE_FREEFORM";
                        }
                        logGateStateIfChanged(displayId, visibleFreeform, safe,
                                remoteLeashCount, action);
                    } catch (Throwable gateError) {
                        // Unexpected gate failures remain fail-closed to wallpaper.
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
            Api101Bridge.log("[DC] freeform wallpaper capture gate installed");
        } catch (Throwable error) {
            FreeformLeashRuntime.setCaptureGateInstalled(false);
            Api101Bridge.log("[DC] freeform wallpaper capture gate unavailable", error);
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
}
