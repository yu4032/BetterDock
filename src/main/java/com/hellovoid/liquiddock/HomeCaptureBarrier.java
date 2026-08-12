package com.hellovoid.liquiddock;

import android.os.SystemClock;

import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * Prevents HyperOS' Wallpaper BBQ wrapper from being sampled while Dock icons are
 * flying back into the HOME Dock.  The old implementation merely installed another
 * clean frame 500 ms later; that still allowed an icon-containing frame to be shown
 * and cached first, which appears as a one-frame icon flash when the clean frame wins.
 *
 * This barrier leaves the last known-good glass frame on screen during the short HOME
 * transition, then requests exactly one fresh HOME frame after the animation settles.
 */
final class HomeCaptureBarrier {
    private static final long HOME_SETTLE_MS = 650L;

    private static volatile long blockUntilUptime;
    private static final WeakHashMap<DockLiquidGlassView, Runnable> pending =
            new WeakHashMap<>();

    private HomeCaptureBarrier() {}

    static void install() {
        try {
            XposedHelpers.findAndHookMethod(DockLiquidGlassView.class,
                    "setGestureCaptureTarget", String.class, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            String target = String.valueOf(param.args[0]);
                            if ("HOME".equals(target)) {
                                blockUntilUptime = SystemClock.uptimeMillis() + HOME_SETTLE_MS;
                                MainHook.log("[DC] HOME capture barrier opened "
                                        + HOME_SETTLE_MS + "ms");
                            } else {
                                blockUntilUptime = 0L;
                                cancelPending((DockLiquidGlassView) param.thisObject);
                            }
                        }
                    });

            XposedHelpers.findAndHookMethod(DockLiquidGlassView.class,
                    "startCapture", new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            long remaining = blockUntilUptime - SystemClock.uptimeMillis();
                            if (remaining <= 0L) return;

                            DockLiquidGlassView glass = (DockLiquidGlassView) param.thisObject;
                            // Suppress the whole start attempt before it can arm the watchdog,
                            // touch the wallpaper cache, or call SurfaceFlinger mode 2.
                            param.setResult(null);
                            scheduleSettledCapture(glass, remaining);
                            MainHook.log("[DC] HOME capture deferred " + remaining + "ms");
                        }
                    });
            MainHook.log("[DC] HOME icon-ghost capture barrier installed");
        } catch (Throwable error) {
            Api101Bridge.log("[DC] HOME capture barrier install failed", error);
        }
    }

    private static void scheduleSettledCapture(DockLiquidGlassView glass, long remaining) {
        synchronized (pending) {
            if (pending.containsKey(glass)) return;
            Runnable task = new Runnable() {
                @Override public void run() {
                    synchronized (pending) { pending.remove(glass); }
                    long left = blockUntilUptime - SystemClock.uptimeMillis();
                    if (left > 0L) {
                        scheduleSettledCapture(glass, left);
                        return;
                    }
                    if (!glass.isAttachedToWindow()) return;
                    glass.requestCapture("home-settled-barrier");
                }
            };
            pending.put(glass, task);
            glass.postDelayed(task, Math.max(1L, remaining));
        }
    }

    private static void cancelPending(DockLiquidGlassView glass) {
        synchronized (pending) {
            Runnable task = pending.remove(glass);
            if (task != null) glass.removeCallbacks(task);
        }
    }
}
