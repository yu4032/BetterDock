package com.hellovoid.liquiddock;

import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

/** Restarts MIUI Home after a configuration boundary that requires fresh process-start hooks. */
final class LauncherRestart {
    private static final AtomicBoolean PENDING = new AtomicBoolean(false);

    private LauncherRestart() {}

    static void restartAsync(String reason) {
        if (!PENDING.compareAndSet(false, true)) return;
        Thread worker = new Thread(() -> {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{
                        "su", "-c",
                        "am force-stop com.miui.home; sleep 1; "
                                + "am start -a android.intent.action.MAIN -c android.intent.category.HOME"
                });
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    Log.w("LiquidDock", "Launcher restart failed code=" + exitCode
                            + " reason=" + reason);
                }
            } catch (Throwable error) {
                Log.w("LiquidDock", "Launcher restart failed reason=" + reason, error);
            } finally {
                PENDING.set(false);
            }
        }, "LiquidDock-launcher-restart");
        worker.setDaemon(true);
        worker.start();
    }
}
