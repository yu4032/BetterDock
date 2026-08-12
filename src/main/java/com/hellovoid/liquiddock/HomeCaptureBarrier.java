package com.hellovoid.liquiddock;

import android.app.WallpaperManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;

import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * Prevents HyperOS' Wallpaper BBQ wrapper from being sampled while Dock icons are
 * flying back into the HOME Dock.
 *
 * The barrier is deliberately narrow:
 *  - it is opened only for a real APP/RECENTS -> HOME scene transition;
 *  - while active, HOME cache recrops are still allowed so Dock motion/rebound remains live;
 *  - only a new mode-2 SurfaceFlinger capture is deferred until the icon animation settles.
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
                            DockLiquidGlassView glass = (DockLiquidGlassView) param.thisObject;
                            String target = String.valueOf(param.args[0]);

                            if (!"HOME".equals(target)) {
                                blockUntilUptime = 0L;
                                cancelPending(glass);
                                return;
                            }

                            // Read the scene BEFORE setGestureCaptureTarget() mutates it.
                            // HyperOS also emits GestureToHome during small HOME-screen Dock
                            // pulls/rebounds; those are not app-return transitions and must keep
                            // the normal observation/cache capture path fully live.
                            CaptureScene previous = currentScene(glass);
                            if (previous == CaptureScene.HOME) {
                                if (blockUntilUptime <= SystemClock.uptimeMillis()) {
                                    cancelPending(glass);
                                }
                                MainHook.log("[DC] HOME barrier bypass: already HOME");
                                return;
                            }

                            blockUntilUptime = SystemClock.uptimeMillis() + HOME_SETTLE_MS;
                            MainHook.log("[DC] HOME capture barrier opened from " + previous
                                    + " for " + HOME_SETTLE_MS + "ms");
                        }
                    });

            XposedHelpers.findAndHookMethod(DockLiquidGlassView.class,
                    "startCapture", new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            long remaining = blockUntilUptime - SystemClock.uptimeMillis();
                            if (remaining <= 0L) return;

                            DockLiquidGlassView glass = (DockLiquidGlassView) param.thisObject;
                            if (currentScene(glass) != CaptureScene.HOME) return;

                            // IMPORTANT: do not freeze Dock geometry during the barrier.
                            // If the existing clean wallpaper cache can satisfy this exact
                            // geometry, let the original startCapture() run: it will hit
                            // tryServeWallpaperFromCache() and recrop/reinstall immediately,
                            // with no new SurfaceFlinger sampling and therefore no icon ghost.
                            if (canServeCurrentHomeRequestFromCache(glass)) {
                                MainHook.log("[DC] HOME barrier cache-pass " + remaining + "ms");
                                return;
                            }

                            // Cache miss means the original path would reach a fresh mode-2
                            // capture of Wallpaper BBQ wrapper while icons may still be flying.
                            // Defer only that case and request one clean frame at settle.
                            param.setResult(null);
                            scheduleSettledCapture(glass, remaining);
                            MainHook.log("[DC] HOME SF capture deferred " + remaining + "ms");
                        }
                    });
            MainHook.log("[DC] HOME icon-ghost capture barrier installed (scene/cache aware)");
        } catch (Throwable error) {
            Api101Bridge.log("[DC] HOME capture barrier install failed", error);
        }
    }

    private static CaptureScene currentScene(DockLiquidGlassView glass) {
        try {
            CaptureSceneState state = (CaptureSceneState)
                    XposedHelpers.getObjectField(glass, "sceneState");
            return state != null ? state.desired() : CaptureScene.APP;
        } catch (Throwable error) {
            MainHook.log("[DC] HOME barrier scene read failed: " + error);
            return CaptureScene.APP;
        }
    }

    /** Mirrors DockLiquidGlassView.tryServeWallpaperFromCache()'s cheap validity checks.
     *  We only need to know whether it is safe to let startCapture() continue. */
    private static boolean canServeCurrentHomeRequestFromCache(DockLiquidGlassView glass) {
        try {
            if (!XposedHelpers.getBooleanField(glass, "wallpaperCacheReady")) return false;
            Bitmap cache = (Bitmap) XposedHelpers.getObjectField(glass, "wallpaperStripCache");
            Rect cacheRect = (Rect) XposedHelpers.getObjectField(glass, "cacheStripRect");
            if (cache == null || cache.isRecycled() || cacheRect == null) return false;
            if (XposedHelpers.getLongField(glass, "rotationStabilizeUntilNanos") != 0L) {
                return false;
            }

            Object req = XposedHelpers.callMethod(glass, "makeCaptureRequest");
            if (req == null) return false;
            Rect reqStrip = (Rect) XposedHelpers.getObjectField(req, "stripRect");
            if (reqStrip == null || !cacheRect.contains(reqStrip)) return false;

            int reqRotation = XposedHelpers.getIntField(req, "rotation");
            int reqDisplayWidth = XposedHelpers.getIntField(req, "displayWidth");
            int reqDisplayHeight = XposedHelpers.getIntField(req, "displayHeight");
            if (reqRotation != XposedHelpers.getIntField(glass, "cacheRotation")
                    || reqDisplayWidth != XposedHelpers.getIntField(glass, "cacheDisplayWidth")
                    || reqDisplayHeight != XposedHelpers.getIntField(glass, "cacheDisplayHeight")) {
                return false;
            }

            int cachedWallpaperId = XposedHelpers.getIntField(glass, "cacheWallpaperId");
            try {
                int currentWallpaperId = WallpaperManager.getInstance(glass.getContext())
                        .getWallpaperId(WallpaperManager.FLAG_SYSTEM);
                if (currentWallpaperId != cachedWallpaperId) return false;
            } catch (Throwable ignored) {
                // Match the renderer's behavior: inability to query wallpaper ID does not
                // invalidate an otherwise geometrically valid cache entry.
            }
            return true;
        } catch (Throwable error) {
            MainHook.log("[DC] HOME barrier cache probe failed: " + error);
            return false;
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
