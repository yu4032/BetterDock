package com.hellovoid.liquiddock;

import android.app.ActivityManager;
import android.content.Context;

import java.util.List;
import java.util.function.Consumer;

/** Samples the top task as noisy foreground evidence; contains no scene mutation. */
final class ForegroundTaskResolver {
    static final class Observation {
        final ForegroundOwnership ownership;
        final String packageName;
        final int windowingMode;

        Observation(ForegroundOwnership ownership, String packageName, int windowingMode) {
            this.ownership = ownership;
            this.packageName = packageName;
            this.windowingMode = windowingMode;
        }
    }

    private final Consumer<String> logger;

    ForegroundTaskResolver(Consumer<String> logger) { this.logger = logger; }

    Observation resolve(Context context) {
        if (context == null) return unknown();
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return unknown();
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
            if (tasks == null || tasks.isEmpty() || tasks.get(0).topActivity == null) return unknown();
            ActivityManager.RunningTaskInfo top = tasks.get(0);
            String pkg = top.topActivity.getPackageName();
            if (pkg == null || pkg.isEmpty()) return unknown();
            int mode = windowingMode(top);
            boolean launcherOwned = "com.miui.home".equals(pkg)
                    || LauncherSceneOwnershipPolicy.launcherOwnsScene(false, mode);
            return new Observation(launcherOwned
                    ? ForegroundOwnership.HOME : ForegroundOwnership.EXTERNAL, pkg, mode);
        } catch (Throwable e) {
            logger.accept("[DC] top task resolve unavailable: " + e);
            return unknown();
        }
    }

    /** Compatibility probe retained for source contracts; scene code uses resolve(). */
    String resolveTopPackage(Context context) { return resolve(context).packageName; }

    private static Observation unknown() {
        return new Observation(ForegroundOwnership.UNKNOWN, null, -1);
    }

    private static int windowingMode(ActivityManager.RunningTaskInfo task) {
        try {
            Object value = HookUtil.invoke(task, "getWindowingMode");
            return value instanceof Integer ? (Integer) value : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }
}
