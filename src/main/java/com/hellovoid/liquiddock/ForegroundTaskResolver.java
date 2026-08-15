package com.hellovoid.liquiddock;

import android.app.ActivityManager;
import android.content.Context;

import java.util.List;
import java.util.function.Consumer;

/** Resolves the task that physically owns the foreground; contains no scene mutation. */
final class ForegroundTaskResolver {
    private final Consumer<String> logger;

    ForegroundTaskResolver(Consumer<String> logger) {
        this.logger = logger;
    }

    String resolveTopPackage(Context context) {
        if (context == null) return null;
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return null;
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
            if (tasks == null || tasks.isEmpty() || tasks.get(0).topActivity == null) return null;
            return tasks.get(0).topActivity.getPackageName();
        } catch (Throwable e) {
            logger.accept("[DC] top task resolve unavailable: " + e);
            return null;
        }
    }
}
