package com.hellovoid.liquiddock;

import android.app.ActivityManager;
import android.content.Context;
import android.view.Display;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Compatibility facade for DockLiquidGlassView's existing freeform safety preflight.
 * It no longer resolves SurfaceFlinger layer names. Actual freeform exclusions are
 * SurfaceControl task leashes supplied by FreeformTaskLeashResolver at capture submit time.
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

    synchronized boolean hasVisibleFreeformTasks() {
        refreshPresence();
        // Enumeration failure is treated as unsafe/possibly-active rather than leaking a
        // window into full-display capture.
        return !cachedScanSucceeded || cachedVisibleFreeform;
    }

    synchronized Collection<String> resolveVisibleLayerNames() {
        refreshPresence();
        if (cachedScanSucceeded && !cachedVisibleFreeform) {
            FreeformLeashRuntime.demandProvider(false);
            return Collections.emptyList();
        }
        FreeformLeashRuntime.demandProvider(true);
        if (!cachedScanSucceeded || !FreeformLeashRuntime.isProviderReady()) {
            return Collections.emptyList();
        }
        // DockLiquidGlassView currently uses collection emptiness only as its preflight
        // safety signal. Reuse the already-existing Dock name so no new/freeform layer-name
        // selector is introduced; direct task-leash exclusion happens at capture submission.
        return Collections.singletonList("Floating Dock");
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
