package com.hellovoid.liquiddock;

import android.app.ActivityManager;
import android.content.Context;

import java.util.List;
import java.util.function.Consumer;

/** Samples the top task as noisy foreground evidence; contains no scene mutation.
 * LauncherSceneController cross-validates this with focus/lifecycle boundaries before
 * allowing it to change persistent HOME/EXTERNAL authority. */
final class ForegroundTaskResolver {
    static final class Observation {
        final ForegroundOwnership ownership;
        final String packageName;

        Observation(ForegroundOwnership ownership, String packageName) {
            this.ownership = ownership;
            this.packageName = packageName;
        }
    }

    private final Consumer<String> logger;

    ForegroundTaskResolver(Consumer<String> logger) {
        this.logger = logger;
    }

    Observation resolve(Context context) {
        if (context == null) return new Observation(ForegroundOwnership.UNKNOWN, null);
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return new Observation(ForegroundOwnership.UNKNOWN, null);
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
            if (tasks == null || tasks.isEmpty() || tasks.get(0).topActivity == null) {
                return new Observation(ForegroundOwnership.UNKNOWN, null);
            }
            String pkg = tasks.get(0).topActivity.getPackageName();
            if (pkg == null || pkg.isEmpty()) {
                return new Observation(ForegroundOwnership.UNKNOWN, null);
            }
            return new Observation("com.miui.home".equals(pkg)
                    ? ForegroundOwnership.HOME : ForegroundOwnership.EXTERNAL, pkg);
        } catch (Throwable e) {
            logger.accept("[DC] top task resolve unavailable: " + e);
            return new Observation(ForegroundOwnership.UNKNOWN, null);
        }
    }

    /** Compatibility probe retained for source contracts; scene code uses resolve(). */
    String resolveTopPackage(Context context) {
        return resolve(context).packageName;
    }
}
