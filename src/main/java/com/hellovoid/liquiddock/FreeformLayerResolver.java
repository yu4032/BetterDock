package com.hellovoid.liquiddock;

import android.app.ActivityManager;
import android.content.Context;
import android.view.Display;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Compatibility facade for DockLiquidGlassView's existing freeform safety preflight.
 * It never resolves or returns freeform layer names. Actual exclusions are WMShell task
 * SurfaceControl leashes applied by FreeformCaptureLeashHook immediately before capture.
 */
final class FreeformLayerResolver {
    private static final int MAX_RUNNING_TASKS = 32;
    private static final long CACHE_NANOS = 100_000_000L;

    private final Context context;
    private long cacheUntilNanos;
    private boolean cachedVisibleFreeform;
    private boolean cachedScanSucceeded;

    FreeformLayerResolver(Context context, SurfaceLayerNameResolver ignoredLegacyResolver) {
        Context app = context.getApplicationContext();
        this.context = app != null ? app : context;
        FreeformLeashRuntime.install(new FreeformTaskLeashResolver(this.context));
    }

    synchronized void invalidate() {
        cacheUntilNanos = 0L;
    }

    /**
     * DockLiquidGlassView interprets true + no resolved layers as "full display unsafe".
     * Preserve that fail-closed boundary only while a visible/possible freeform task exists
     * and the task-leash capture gate is not ready. Once the gate is ready, the final capture
     * submission performs a fresh task scan and owns the all-or-nothing safety decision.
     */
    synchronized boolean hasVisibleFreeformTasks() {
        refreshPresence();
        if (!cachedScanSucceeded) return true;
        if (!cachedVisibleFreeform) {
            FreeformLeashRuntime.demandProvider(false);
            return false;
        }
        FreeformLeashRuntime.demandProvider(true);
        return !FreeformLeashRuntime.isProviderReady();
    }

    synchronized Collection<String> resolveVisibleLayerNames() {
        // Freeform exclusion is exclusively SurfaceControl-based. This method remains only
        // because DockLiquidGlassView's existing preflight interface has not been widened.
        return Collections.emptyList();
    }

    private void refreshPresence() {
        long now = System.nanoTime();
        if (now < cacheUntilNanos) return;
        boolean visible = false;
        boolean succeeded = false;
        int displayId = displayId();
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningTaskInfo> tasks = am != null
                    ? am.getRunningTasks(MAX_RUNNING_TASKS) : null;
            if (tasks != null) {
                for (ActivityManager.RunningTaskInfo task : tasks) {
                    if (task == null || task.displayId != displayId) continue;
                    if (FreeformCapturePolicy.shouldExclude(windowingMode(task), isVisible(task))) {
                        visible = true;
                        break;
                    }
                }
                succeeded = true;
            }
        } catch (Throwable ignored) {
            succeeded = false;
        }
        cachedVisibleFreeform = visible;
        cachedScanSucceeded = succeeded;
        cacheUntilNanos = now + CACHE_NANOS;
    }

    private int displayId() {
        try {
            Display display = context.getDisplay();
            return display != null ? display.getDisplayId() : Display.DEFAULT_DISPLAY;
        } catch (Throwable ignored) {
            return Display.DEFAULT_DISPLAY;
        }
    }

    private static int windowingMode(ActivityManager.RunningTaskInfo task) {
        try {
            Object value = HookUtil.invoke(task, "getWindowingMode");
            return value instanceof Integer ? (Integer) value : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static boolean isVisible(ActivityManager.RunningTaskInfo task) {
        try {
            java.lang.reflect.Field field = task.getClass().getField("isVisible");
            Object value = field.get(task);
            if (value instanceof Boolean) return (Boolean) value;
        } catch (Throwable ignored) {}
        try {
            Object value = HookUtil.invoke(task, "isVisible");
            if (value instanceof Boolean) return (Boolean) value;
        } catch (Throwable ignored) {}
        return true;
    }
}
