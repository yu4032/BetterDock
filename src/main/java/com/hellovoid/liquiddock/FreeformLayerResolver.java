package com.hellovoid.liquiddock;

import android.app.ActivityManager;
import android.content.ComponentName;
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
    // Diagnostic-only fact. Production freeform detection still depends on cachedOwnerUids
    // exactly as before; this latch only lets the report run if package-UID lookup itself fails.
    private boolean diagnosticVisibleFreeformTask;

    static final class DiagnosticTask {
        final int taskId;
        final int displayId;
        final int windowingMode;
        final boolean visible;
        final String topActivity;
        final String baseActivity;
        final String bounds;
        final String packageName;
        final Integer packageUid;
        final boolean visibleFreeform;
        final String error;

        DiagnosticTask(int taskId, int displayId, int windowingMode, boolean visible,
                       String topActivity, String baseActivity, String bounds,
                       String packageName, Integer packageUid, boolean visibleFreeform,
                       String error) {
            this.taskId = taskId;
            this.displayId = displayId;
            this.windowingMode = windowingMode;
            this.visible = visible;
            this.topActivity = topActivity;
            this.baseActivity = baseActivity;
            this.bounds = bounds;
            this.packageName = packageName;
            this.packageUid = packageUid;
            this.visibleFreeform = visibleFreeform;
            this.error = error;
        }
    }

    static final class DiagnosticSnapshot {
        final List<DiagnosticTask> tasks;
        final List<Integer> freeformOwnerUids;
        final List<String> freeformKeywords;
        final boolean visibleFreeformDetected;
        final String error;

        DiagnosticSnapshot(List<DiagnosticTask> tasks, List<Integer> freeformOwnerUids,
                           List<String> freeformKeywords, boolean visibleFreeformDetected,
                           String error) {
            this.tasks = tasks;
            this.freeformOwnerUids = freeformOwnerUids;
            this.freeformKeywords = freeformKeywords;
            this.visibleFreeformDetected = visibleFreeformDetected;
            this.error = error;
        }

        boolean hasVisibleFreeformTask() {
            return visibleFreeformDetected;
        }
    }

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
        if (cachedOwnerUids.isEmpty()) {
            runDiagnosticIfNeeded(null);
            return Collections.emptyList();
        }
        long now = System.nanoTime();
        if (now < layerCacheUntilNanos) {
            runDiagnosticIfNeeded(null);
            return cachedLayerNames;
        }
        Throwable resolutionError = null;
        try {
            Collection<String> resolved = surfaceLayers.resolveAllByOwnerUids(cachedOwnerUids);
            cachedLayerNames = Collections.unmodifiableList(new ArrayList<>(resolved));
        } catch (Throwable error) {
            resolutionError = error;
            cachedLayerNames = Collections.emptyList();
        }
        layerCacheUntilNanos = now + CACHE_NANOS;
        runDiagnosticIfNeeded(resolutionError);
        return cachedLayerNames;
    }

    /**
     * Independent task snapshot for the temporary one-shot diagnostic. This deliberately does
     * not read or mutate the production task/layer caches above, so running the diagnostic cannot
     * make a later capture look safer (or less safe) than it otherwise would.
     */
    DiagnosticSnapshot snapshotForDiagnostics() {
        ArrayList<DiagnosticTask> records = new ArrayList<>();
        LinkedHashSet<Integer> ownerUids = new LinkedHashSet<>();
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        boolean visibleFreeformDetected = false;
        String snapshotError = null;

        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) {
                return new DiagnosticSnapshot(Collections.emptyList(), Collections.emptyList(),
                        Collections.emptyList(), false,
                        "ActivityManager unavailable");
            }
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(MAX_RUNNING_TASKS);
            if (tasks == null) {
                return new DiagnosticSnapshot(Collections.emptyList(), Collections.emptyList(),
                        Collections.emptyList(), false,
                        "ActivityManager.getRunningTasks returned null");
            }

            for (ActivityManager.RunningTaskInfo task : tasks) {
                if (task == null) continue;
                int mode = windowingMode(task);
                boolean visible = isVisible(task);
                ComponentName top = task.topActivity;
                ComponentName base = task.baseActivity;
                String pkg = top != null ? top.getPackageName() : null;
                boolean visibleFreeform = FreeformCapturePolicy.shouldExclude(mode, visible)
                        && pkg != null && !"com.miui.home".equals(pkg);
                if (visibleFreeform) visibleFreeformDetected = true;

                Integer uid = null;
                String taskError = null;
                if (pkg != null && !"com.miui.home".equals(pkg)) {
                    try {
                        uid = context.getPackageManager().getPackageUid(pkg, 0);
                    } catch (Throwable error) {
                        taskError = "packageUid=" + diagnosticError(error);
                    }
                }
                if (visibleFreeform) {
                    if (uid != null) ownerUids.add(uid);
                    addKeyword(keywords, pkg);
                    if (top != null) addKeyword(keywords, top.getClassName());
                    if (base != null) addKeyword(keywords, base.getClassName());
                }

                records.add(new DiagnosticTask(
                        task.taskId,
                        task.displayId,
                        mode,
                        visible,
                        component(top),
                        component(base),
                        diagnosticBounds(task),
                        pkg,
                        uid,
                        visibleFreeform,
                        taskError));
            }
        } catch (Throwable error) {
            snapshotError = diagnosticError(error);
        }

        return new DiagnosticSnapshot(
                Collections.unmodifiableList(records),
                Collections.unmodifiableList(new ArrayList<>(ownerUids)),
                Collections.unmodifiableList(new ArrayList<>(keywords)),
                visibleFreeformDetected,
                snapshotError);
    }

    private void runDiagnosticIfNeeded(Throwable productionResolutionError) {
        if (FreeformCaptureDiagnostic.hasAttempted()) return;
        if (!diagnosticVisibleFreeformTask && cachedOwnerUids.isEmpty()) return;
        DiagnosticSnapshot snapshot;
        try {
            snapshot = snapshotForDiagnostics();
        } catch (Throwable error) {
            snapshot = new DiagnosticSnapshot(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    diagnosticVisibleFreeformTask,
                    diagnosticError(error));
        }
        FreeformCaptureDiagnostic.runOnce(
                context,
                snapshot,
                surfaceLayers,
                cachedOwnerUids,
                cachedLayerNames,
                productionResolutionError);
    }

    private void refreshTaskOwners() {
        long now = System.nanoTime();
        if (now < taskCacheUntilNanos) return;

        LinkedHashSet<Integer> ownerUids = new LinkedHashSet<>();
        boolean visibleFreeformDetected = false;
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
                    visibleFreeformDetected = true;
                    try {
                        ownerUids.add(context.getPackageManager().getPackageUid(pkg, 0));
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        diagnosticVisibleFreeformTask = visibleFreeformDetected;

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

    private static String component(ComponentName component) {
        return component != null ? component.flattenToShortString() : "-";
    }

    private static void addKeyword(LinkedHashSet<String> keywords, String value) {
        if (value != null && !value.isEmpty()) keywords.add(value);
    }

    private static String diagnosticBounds(ActivityManager.RunningTaskInfo task) {
        try {
            Object configuration = HookUtil.getField(task, "configuration");
            if (configuration == null) return "-";
            Object windowConfiguration = HookUtil.getField(configuration, "windowConfiguration");
            if (windowConfiguration == null) return "-";
            Object bounds = HookUtil.invoke(windowConfiguration, "getBounds");
            return bounds != null ? String.valueOf(bounds) : "-";
        } catch (Throwable error) {
            return "unavailable(" + diagnosticError(error) + ")";
        }
    }

    private static String diagnosticError(Throwable error) {
        Throwable cause = error;
        if (error instanceof java.lang.reflect.InvocationTargetException
                && ((java.lang.reflect.InvocationTargetException) error).getTargetException() != null) {
            cause = ((java.lang.reflect.InvocationTargetException) error).getTargetException();
        }
        return cause.getClass().getName() + ":" + String.valueOf(cause.getMessage());
    }
}
