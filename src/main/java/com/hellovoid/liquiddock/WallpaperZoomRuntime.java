package com.hellovoid.liquiddock;

import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;

/** Launcher-process bridge for HyperOS's visual wallpaper scale. */
final class WallpaperZoomRuntime {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<DockLiquidGlassView> currentView = new WeakReference<>(null);

    private WallpaperZoomRuntime() {}

    static void bind(DockLiquidGlassView glass) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> bind(glass));
            return;
        }
        currentView = new WeakReference<>(glass);
    }

    static void onScale(float scale) {
        if (!Float.isFinite(scale)) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> onScale(scale));
            return;
        }
        DockLiquidGlassView glass = currentView.get();
        if (glass != null) {
            // Task 3 supplies the typed method. Keep this bridge independently buildable until
            // then; the final implementation replaces this reflective edge with a direct call.
            HookUtil.invoke(glass, "setLauncherWallpaperVisualScale", scale);
        }
    }
}
