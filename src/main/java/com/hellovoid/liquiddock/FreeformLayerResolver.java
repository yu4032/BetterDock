package com.hellovoid.liquiddock;

import android.app.ActivityManager;
import android.content.Context;
import android.view.Display;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Compatibility facade for DockLiquidGlassView's existing freeform safety preflight.
 * It never resolves freeform task layer names. Actual freeform exclusions are WMShell task
 * SurfaceControl leashes applied by FreeformCaptureLeashHook immediately before capture.
 */
final class FreeformLayerResolver {
    private static final int MAX_RUNNING_TASKS = 32;
    private static final long CACHE_NANOS = 100_000_000L;
    private static final String EXISTING_DOCK_EXCLUSION = "Floating Dock";

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

    /** Presence remains truthful because HOME live-backdrop selection also consumes it. */
    synchronized boolean hasVisibleFreeformTasks() {
        refreshPresence();
        if (!cachedScanSucceeded) return true;
        if (!cachedVisibleFreeform) {
            FreeformLeashRuntime.demandProvider(false);
            return false;
        }
        FreeformLeashRuntime.demandProvider(true);
        return true;
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
        /*
         * DockLiquidGlassView's legacy preflight equates a non-empty collection with
         * "FULL_DISPLAY exclusions are available". The only name returned here is the
         * Dock's existing exclusion, which mode 1 already excludes; it is NOT a freeform
         * layer guess. The final capture gate independently rescans current tasks and must
         * obtain every task leash before allowing FULL_DISPLAY.
         */
        return Collections.singletonList(EXISTING_DOCK_EXCLUSION);
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
