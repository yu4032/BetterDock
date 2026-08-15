package com.hellovoid.liquiddock;

import android.app.ActivityManager;
import android.content.Context;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/** Resolves visible freeform task owners to SurfaceFlinger layer names with a short cache. */
final class FreeformLayerResolver {
    private static final int MAX_RUNNING_TASKS = 32;
    private static final long CACHE_NANOS = 250_000_000L;

    private final Context context;
    private final SurfaceLayerNameResolver surfaceLayers;

    private long taskCacheUntilNanos;
    private long layerCacheUntilNanos;
    private List<Integer> cachedOwnerUids = Collections.emptyList();
    private List<String> cachedLayerNames = Collections.emptyList();

    FreeformLayerResolver(Context context, SurfaceLayerNameResolver surfaceLayers) {
        Context app = context.getApplicationContext();
        this.context = app != null ? app : context;
        this.surfaceLayers = surfaceLayers;
    }

    synchronized void invalidate() {
        taskCacheUntilNanos = 0L;
        layerCacheUntilNanos = 0L;
    }

    synchronized boolean hasVisibleFreeformTasks() {
        refreshTaskOwners();
        return !cachedOwnerUids.isEmpty();
    }

    synchronized Collection<String> resolveVisibleLayerNames() {
        refreshTaskOwners();
        if (cachedOwnerUids.isEmpty()) return Collections.emptyList();
        long now = System.nanoTime();
        if (now < layerCacheUntilNanos) return cachedLayerNames;
        try {
            Collection<String> resolved = surfaceLayers.resolveAllByOwnerUids(cachedOwnerUids);
            cachedLayerNames = Collections.unmodifiableList(new ArrayList<>(resolved));
        } catch (Throwable ignored) {
            cachedLayerNames = Collections.emptyList();
        }
        layerCacheUntilNanos = now + CACHE_NANOS;
        return cachedLayerNames;
    }

    private void refreshTaskOwners() {
        long now = System.nanoTime();
        if (now < taskCacheUntilNanos) return;

        LinkedHashSet<Integer> ownerUids = new LinkedHashSet<>();
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningTaskInfo> tasks = am != null
                    ? am.getRunningTasks(MAX_RUNNING_TASKS) : null;
            if (tasks != null) {
                for (ActivityManager.RunningTaskInfo task : tasks) {
                    if (task == null) continue;
                    int mode = windowingMode(task);
                    if (!FreeformCapturePolicy.shouldExclude(mode, isVisible(task))) continue;
                    if (task.topActivity == null) continue;
                    String pkg = task.topActivity.getPackageName();
                    if (pkg == null || "com.miui.home".equals(pkg)) continue;
                    try {
                        ownerUids.add(context.getPackageManager().getPackageUid(pkg, 0));
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        List<Integer> next = Collections.unmodifiableList(new ArrayList<>(ownerUids));
        if (!next.equals(cachedOwnerUids)) {
            cachedOwnerUids = next;
            cachedLayerNames = Collections.emptyList();
            layerCacheUntilNanos = 0L;
        }
        taskCacheUntilNanos = now + CACHE_NANOS;
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
        } catch (Throwable ignored) {
        }
        try {
            Object value = HookUtil.invoke(task, "isVisible");
            if (value instanceof Boolean) return (Boolean) value;
        } catch (Throwable ignored) {
        }
        // Older vendor builds do not expose task visibility. RunningTaskInfo is still
        // ordered by current relevance, so prefer exclusion to leaking a freeform surface.
        return true;
    }
}
