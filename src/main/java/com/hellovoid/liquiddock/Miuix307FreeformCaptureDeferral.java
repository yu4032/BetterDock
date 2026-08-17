package com.hellovoid.liquiddock;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MiuiX 307 safety bridge for a temporarily unknown SystemUI freeform snapshot.
 *
 * A mode-1 APP request has already reserved DockLiquidGlassView's active capture attempt when
 * the final task-leash gate runs. If SystemUI cannot answer authoritatively, switching that
 * request to mode-2 paints wallpaper behind an app; proceeding with mode-1 risks sampling a
 * freeform task. Retire only this request, keep the already-installed APP backdrop untouched,
 * and retry the authoritative snapshot with short bounded backoff.
 */
final class Miuix307FreeformCaptureDeferral {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean RETRY_PENDING = new AtomicBoolean();
    private static final AtomicInteger UNKNOWN_STREAK = new AtomicInteger();
    private static final int MAX_AUTONOMOUS_RETRIES = 4;
    private static final long[] RETRY_DELAYS_MS = {120L, 180L, 280L, 420L};

    private Miuix307FreeformCaptureDeferral() {}

    /** Returns true when the current 307 glass owns a capture attempt that can be deferred. */
    static boolean defer() {
        final DockLiquidGlassView glass = Miuix307DragCaptureHook.currentGlass();
        if (glass == null) return false;

        MAIN.post(() -> {
            // FreeformCaptureLeashHook runs inside LiveScreenCapture.captureScreenAsync after
            // startCapture() has set activeCaptureAttempt/capturing and armed its watchdog.
            // Retiring here is equivalent to a completed request with no new frame installed.
            long attempt;
            try {
                attempt = HookUtil.getLongField(glass, "activeCaptureAttempt");
            } catch (Throwable error) {
                MainHook.log("[DC][MG] freeform deferral could not read active attempt: " + error);
                return;
            }
            if (attempt == 0L) return;

            Object retired = HookUtil.invoke(glass, "retireCaptureAttempt", attempt);
            if (!(retired instanceof Boolean) || !((Boolean) retired)) return;

            try {
                HookUtil.setField(glass, "sourceDirty", true);
            } catch (Throwable error) {
                MainHook.log("[DC][MG] freeform deferral could not preserve dirty state: " + error);
            }

            int retryIndex = UNKNOWN_STREAK.getAndIncrement();
            if (retryIndex == 0) {
                MainHook.log("[DC][MG] freeform snapshot unknown; keeping current APP backdrop");
            }
            if (retryIndex >= MAX_AUTONOMOUS_RETRIES) return;
            if (!RETRY_PENDING.compareAndSet(false, true)) return;

            long delay = RETRY_DELAYS_MS[Math.min(retryIndex, RETRY_DELAYS_MS.length - 1)];
            MAIN.postDelayed(() -> {
                RETRY_PENDING.set(false);
                if (Miuix307DragCaptureHook.currentGlass() != glass) return;
                glass.requestCapture("freeform-snapshot-retry");
            }, delay);
        });
        return true;
    }

    /** A real SystemUI answer ends the uncertainty streak; later transitions may retry anew. */
    static void onSnapshotKnown() {
        UNKNOWN_STREAK.set(0);
    }
}
