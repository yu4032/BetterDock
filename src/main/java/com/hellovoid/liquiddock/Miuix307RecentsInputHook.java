package com.hellovoid.liquiddock;

import android.view.MotionEvent;
import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Input/Overview compatibility for the specialized MiuiX 307 material pipeline.
 *
 * MainHook deliberately skips its generic Launcher gesture lifecycle when the 307 pipeline owns
 * the Dock. That is correct for ownership, but it also removes the input stream that kept Recents
 * backdrop captures moving with the user's finger. Restore only observation: the Floating Dock
 * root gets the same non-consuming touch observer used by the legacy pipeline, while Launcher
 * dispatch remains a fallback and keeps later Overview-card gestures live.
 *
 * Do not feed pointer motion into DockLiquidGlassView's legacy Recents prearm state machine.
 * Unconfirmed RECENTS is intentionally wallpaper-backed by CaptureSourcePolicy; a 307 Dock swipe
 * must therefore remain APP-backed until the exact EnterOverviewStateEvent confirms Recents.
 */
final class Miuix307RecentsInputHook {
    private static final String TAG = "[DC][MG]";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private static volatile boolean gestureActive;
    private static volatile boolean overviewActive;
    private static WeakReference<View> dockRootRef = new WeakReference<>(null);

    private Miuix307RecentsInputHook() {}

    static void install(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        installGlassBindBridge();
        installLauncherInput(classLoader);
        installOverviewBoundary(classLoader, "EnterOverviewStateEvent", true);
        installOverviewBoundary(classLoader, "ExitOverviewStateEvent", false);
        bindExistingDockRoot();
        MainHook.log(TAG + " 307 Recents input bridge installed");
    }

    /**
     * The 307 Dock lives in its own Floating Dock window. Observe that window root directly after
     * every native/themed Prismal bind; Launcher Activity dispatch alone cannot be assumed to own
     * the pointer stream of a separate overlay window.
     */
    private static void installGlassBindBridge() {
        try {
            HookUtil.hookMethod(MiuixGlassHook.class, "install",
                    new Class<?>[]{View.class, View.class, LiquidDockConfig.class,
                            Object.class, ClassLoader.class}, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object result = chain.proceed(args);
                        if (Boolean.TRUE.equals(result) && args.length > 0 && args[0] instanceof View) {
                            bindDockRoot((View) args[0]);
                        }
                        return result;
                    });
            MainHook.log(TAG + " Floating Dock input rebind hook installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " Floating Dock input rebind hook unavailable: " + error);
        }
    }

    private static void bindExistingDockRoot() {
        View background = boundBackground();
        if (background != null) bindDockRoot(background);
    }

    private static void bindDockRoot(View dockBackground) {
        if (dockBackground == null) return;
        View root = dockBackground.getRootView();
        if (root == null || root == dockRootRef.get()) return;

        root.setOnTouchListener((view, event) -> {
            if (event == null || !Miuix307MaterialPipeline.isInstalled()) return false;
            onInputMotion(event.getActionMasked(), event.getRawX(), event.getRawY(), true);
            // Observation only. Never consume or replace MIUI's gesture handling.
            return false;
        });
        dockRootRef = new WeakReference<>(root);
        MainHook.log(TAG + " Floating Dock root touch observer bound class="
                + root.getClass().getName());
    }

    /**
     * Launcher dispatch is a fallback for builds that route the initial gesture through Launcher,
     * and it also keeps pointer-driven capture alive for later gestures inside exact Overview.
     */
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
                        if (!Miuix307MaterialPipeline.isInstalled()) return result;
                        onInputMotion(action, rawX, rawY, false);
                        return result;
                    });
            MainHook.log(TAG + " Launcher dispatchTouchEvent Recents capture observer installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " Launcher input observer unavailable: " + error);
        }
    }

    private static void onInputMotion(int action, float rawX, float rawY, boolean dockWindow) {
        DockLiquidGlassView glass = boundGlass();
        if (glass == null) {
            gestureActive = false;
            return;
        }

        if (action == MotionEvent.ACTION_DOWN) {
            // The Floating Dock root is itself authoritative. Launcher fallback must prove that
            // its DOWN began in/near the Dock before it can own the rest of that pointer stream.
            gestureActive = dockWindow || glass.isTouchInDockArea(rawX, rawY);
            if (gestureActive) {
                notePointerInteraction(glass);
                // Keep the first hidden/collapsed-Dock frame live without invoking the legacy
                // Recents distance prearm. This private helper only grants a short APP visibility
                // bypass and leaves CaptureScene unchanged.
                HookUtil.invoke(glass, "armAppBackdropForGestureDown");
                glass.onDockTouchEvent();
            } else if (overviewActive) {
                notePointerInteraction(glass);
                glass.requestCapture("miuix307-overview-touch-down");
            }
            return;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            if (gestureActive) {
                // Do not re-check Dock bounds here. After a valid DOWN, the finger itself owns
                // capture cadence for the entire swipe. Crucially, do not call onDockGestureMotion:
                // that legacy method prearms RECENTS by distance and would switch source to
                // wallpaper before exact Overview exists.
                notePointerInteraction(glass);
                glass.onDockTouchEvent();
            } else if (overviewActive) {
                notePointerInteraction(glass);
                glass.requestCapture("miuix307-overview-touch-move");
            }
            return;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            clearPointerInteraction(glass);
            gestureActive = false;
        }
    }

    private static void notePointerInteraction(DockLiquidGlassView glass) {
        try {
            Object value = HookUtil.getField(glass, "captureCadence");
            if (value instanceof CaptureCadence) {
                ((CaptureCadence) value).noteInteraction(System.nanoTime());
            }
        } catch (Throwable error) {
            MainHook.log(TAG + " pointer cadence note unavailable: " + error);
        }
    }

    private static void clearPointerInteraction(DockLiquidGlassView glass) {
        try {
            Object value = HookUtil.getField(glass, "captureCadence");
            if (value instanceof CaptureCadence) {
                ((CaptureCadence) value).clearInteraction();
            }
        } catch (Throwable error) {
            MainHook.log(TAG + " pointer cadence clear unavailable: " + error);
        }
    }

    private static void installOverviewBoundary(
            ClassLoader classLoader, String simpleName, boolean active) {
        try {
            Class<?> eventClass = Class.forName(
                    "com.miui.home.recents.event." + simpleName, false, classLoader);
            int hooked = 0;
            for (Constructor<?> ctor : eventClass.getDeclaredConstructors()) {
                HookUtil.hook(ctor, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    if (!Miuix307MaterialPipeline.isInstalled()) return result;
                    overviewActive = active;
                    DockLiquidGlassView glass = boundGlass();
                    if (glass != null) {
                        if (!active) clearPointerInteraction(glass);
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

    private static View boundBackground() {
        try {
            Field field = MiuixGlassHook.class.getDeclaredField("backgroundRef");
            field.setAccessible(true);
            Object value = field.get(null);
            return value instanceof View ? (View) value : null;
        } catch (Throwable error) {
            MainHook.log(TAG + " 307 Dock background unavailable: " + error);
            return null;
        }
    }
}
