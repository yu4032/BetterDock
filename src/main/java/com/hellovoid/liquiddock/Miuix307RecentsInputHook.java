package com.hellovoid.liquiddock;

import android.view.MotionEvent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Input/Overview compatibility for the specialized MiuiX 307 material pipeline.
 *
 * MainHook deliberately skips its generic Launcher gesture lifecycle when the 307 pipeline owns
 * the Dock. That is correct for ownership, but it also removed the input stream that kept Recents
 * backdrop captures moving with the user's finger. Observe Launcher dispatch without consuming or
 * replacing listeners: a gesture only becomes ours when ACTION_DOWN starts in/near the Dock, then
 * every MOVE remains authoritative until UP/CANCEL even after the finger has left Dock bounds.
 * Exact Overview events keep later task-card touches live as well.
 */
final class Miuix307RecentsInputHook {
    private static final String TAG = "[DC][MG]";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private static volatile boolean gestureActive;
    private static volatile boolean overviewActive;

    private Miuix307RecentsInputHook() {}

    static void install(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        installLauncherInput(classLoader);
        installGestureToRecent(classLoader);
        installOverviewBoundary(classLoader, "EnterOverviewStateEvent", true);
        installOverviewBoundary(classLoader, "ExitOverviewStateEvent", false);
        MainHook.log(TAG + " 307 Recents input bridge installed");
    }

    private static void installLauncherInput(ClassLoader classLoader) {
        try {
            Class<?> launcherClass = Class.forName(
                    "com.miui.home.launcher.Launcher", false, classLoader);
            HookUtil.hookMethod(launcherClass, "dispatchTouchEvent",
                    new Class<?>[]{MotionEvent.class}, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        MotionEvent event = args.length > 0 && args[0] instanceof MotionEvent
                                ? (MotionEvent) args[0] : null;
                        int action = event != null ? event.getActionMasked() : MotionEvent.ACTION_CANCEL;
                        float rawX = event != null ? event.getRawX() : Float.NaN;
                        float rawY = event != null ? event.getRawY() : Float.NaN;

                        Object result = chain.proceed(args);
                        if (!launcherClass.isInstance(chain.getThisObject())) return result;
                        if (!Miuix307MaterialPipeline.isInstalled()) {
                            gestureActive = false;
                            overviewActive = false;
                            return result;
                        }
                        onLauncherMotion(action, rawX, rawY);
                        return result;
                    });
            MainHook.log(TAG + " Launcher dispatchTouchEvent Recents capture observer installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " Launcher input observer unavailable: " + error);
        }
    }

    private static void onLauncherMotion(int action, float rawX, float rawY) {
        DockLiquidGlassView glass = boundGlass();
        if (glass == null) {
            gestureActive = false;
            return;
        }

        if (action == MotionEvent.ACTION_DOWN) {
            gestureActive = glass.isTouchInDockArea(rawX, rawY);
            if (gestureActive) {
                glass.onDockTouchEvent();
                glass.onDockGestureMotion(action, rawY);
            } else if (overviewActive) {
                // A later Recents-card gesture can start anywhere in Overview.
                glass.requestCapture("miuix307-overview-touch-down");
            }
            return;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            if (gestureActive) {
                // Do not re-check Dock bounds here. The finger itself owns capture cadence for the
                // entire swipe, including the slow region after it has moved above the Dock.
                glass.onDockTouchEvent();
                glass.onDockGestureMotion(action, rawY);
            } else if (overviewActive) {
                glass.requestCapture("miuix307-overview-touch-move");
            }
            return;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (gestureActive) glass.onDockGestureMotion(action, rawY);
            gestureActive = false;
        }
    }

    private static void installGestureToRecent(ClassLoader classLoader) {
        try {
            Class<?> eventClass = Class.forName(
                    "com.miui.home.launcher.dock.v3.GestureToRecent", false, classLoader);
            int hooked = 0;
            for (Constructor<?> ctor : eventClass.getDeclaredConstructors()) {
                HookUtil.hook(ctor, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    if (Miuix307MaterialPipeline.isInstalled()) {
                        DockLiquidGlassView glass = boundGlass();
                        if (glass != null) glass.setGestureCaptureTarget("RECENTS");
                    }
                    return result;
                });
                hooked++;
            }
            MainHook.log(TAG + " GestureToRecent target hook installed constructors=" + hooked);
        } catch (Throwable error) {
            MainHook.log(TAG + " GestureToRecent target hook unavailable: " + error);
        }
    }

    private static void installOverviewBoundary(
            ClassLoader classLoader, String simpleName, boolean active) {
        try {
            Class<?> eventClass = Class.forName(
                    "com.miui.home.launcher.dock.v3." + simpleName, false, classLoader);
            int hooked = 0;
            for (Constructor<?> ctor : eventClass.getDeclaredConstructors()) {
                HookUtil.hook(ctor, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    if (!Miuix307MaterialPipeline.isInstalled()) return result;
                    overviewActive = active;
                    DockLiquidGlassView glass = boundGlass();
                    if (glass != null) {
                        glass.setOverviewActive(active, "miuix307-" + simpleName);
                        glass.requestCapture("miuix307-" + simpleName);
                    }
                    return result;
                });
                hooked++;
            }
            MainHook.log(TAG + " " + simpleName + " hook installed constructors=" + hooked);
        } catch (Throwable error) {
            MainHook.log(TAG + " " + simpleName + " hook unavailable: " + error);
        }
    }

    private static DockLiquidGlassView boundGlass() {
        try {
            Field field = MiuixGlassHook.class.getDeclaredField("glassRef");
            field.setAccessible(true);
            Object value = field.get(null);
            return value instanceof DockLiquidGlassView ? (DockLiquidGlassView) value : null;
        } catch (Throwable error) {
            MainHook.log(TAG + " 307 Recents glass unavailable: " + error);
            return null;
        }
    }
}
