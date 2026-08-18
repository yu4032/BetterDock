package com.hellovoid.liquiddock;

import android.view.MotionEvent;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Restores the one capture signal the specialized MiuiX 307 material path intentionally loses
 * when MainHook returns early: native finger motion for Dock -> Recents gestures.
 *
 * GestureTouchEventTracker is the launcher-owned per-MOVE state machine that already drives the
 * task/follow-tail animation. Reusing that exact boundary avoids guessing from Dock geometry:
 * a slow finger can keep moving while the Floating Dock itself is stationary. Dynamic APP pixel
 * probing remains APP-only and must not control this interaction cadence.
 */
final class Miuix307GestureCaptureHook {
    private static final String TAG = "[DC][MG]";
    private static final String GESTURE_TRACKER =
            "com.miui.home.recents.GestureTouchEventTracker";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    // A real user gesture may recover an already-open capture breaker once. Do not reset the
    // breaker on every MOVE: repeated resets could create a new worker for every timed-out frame.
    private static boolean circuitRecoveryUsed;

    private Miuix307GestureCaptureHook() {}

    static void install(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> tracker = Class.forName(GESTURE_TRACKER, false, classLoader);
            HookUtil.hookMethod(tracker, "onTouchEvent",
                    new Class<?>[]{MotionEvent.class, boolean.class}, chain -> {
                        Object eventValue = chain.getArgs().get(0);
                        if (eventValue instanceof MotionEvent
                                && Miuix307MaterialPipeline.isInstalled()
                                && !MainHook.isWorkstationMode()) {
                            dispatchGesture((MotionEvent) eventValue);
                        }
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });
            MainHook.log(TAG + " native GestureTouchEventTracker capture bridge installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " native gesture capture bridge unavailable: " + error);
        }
    }

    private static void dispatchGesture(MotionEvent event) {
        DockLiquidGlassView glass = boundGlass();
        if (glass == null || event == null) return;

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            circuitRecoveryUsed = false;
            // Existing API owns APP prearm, scene transition and one initial capture request.
            glass.onDockGestureMotion(action, event.getRawY());
            return;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            // This forwards every real finger sample. The first sufficiently large upward move
            // also prearms RECENTS through the existing scene-state API.
            glass.onDockGestureMotion(action, event.getRawY());

            if (!circuitRecoveryUsed && isCaptureCircuitOpen(glass)) {
                circuitRecoveryUsed = true;
                // onDockTouchEvent is deliberately used only for this recovery edge because it
                // resets an opened circuit and rebuilds its worker. Normal MOVE frames below do
                // not touch breaker state.
                glass.onDockTouchEvent();
                MainHook.log(TAG + " gesture MOVE recovered opened capture circuit");
            } else {
                glass.requestCapture("miuix307-gesture-move");
            }
            return;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            glass.onDockGestureMotion(action, event.getRawY());
            glass.requestCapture("miuix307-gesture-end");
            circuitRecoveryUsed = false;
        }
    }

    private static boolean isCaptureCircuitOpen(DockLiquidGlassView glass) {
        try {
            return Boolean.TRUE.equals(HookUtil.getField(glass, "captureCircuitOpen"));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static DockLiquidGlassView boundGlass() {
        try {
            Field field = MiuixGlassHook.class.getDeclaredField("glassRef");
            field.setAccessible(true);
            Object value = field.get(null);
            return value instanceof DockLiquidGlassView ? (DockLiquidGlassView) value : null;
        } catch (Throwable error) {
            MainHook.log(TAG + " gesture bridge glass unavailable: " + error);
            return null;
        }
    }
}
