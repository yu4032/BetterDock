package com.hellovoid.liquiddock;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Hook coordinator for HyperOS 3.0.307+ HotSeats material backgrounds.
 *
 * HyperOS can switch the live HotSeats background implementation when an icon theme is applied.
 * Keep the vendor background installed as the backdrop-blur/gradient owner and place LiquidDock's
 * existing Prismal glass stack directly above whichever supported implementation is active.
 */
final class Miuix307MaterialPipeline {
    static final String BACKGROUND_CLASS =
            "com.miui.home.launcher.hotseats.HotSeatsListContentMiuiXBlurBackground";
    static final String THEMED_BACKGROUND_CLASS =
            "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2";

    // Construct lazily at the first real hierarchy-detach boundary. Eager construction during
    // class initialization breaks host-side JVM contract tests where Android's Looper is absent.
    private static Handler MAIN_HANDLER;

    private static boolean installed;
    private static View workspaceRef;
    private static WeakReference<Object> launcherRef = new WeakReference<>(null);
    private static WeakReference<Object> hotSeatsRef = new WeakReference<>(null);
    private static View observedBackground;
    private static View observedHost;
    private static View.OnAttachStateChangeListener hierarchyListener;
    private static boolean hierarchyRebindPosted;
    private static ViewTreeObserver hierarchyRecoveryObserver;
    private static ViewTreeObserver.OnGlobalLayoutListener hierarchyRecoveryListener;

    private Miuix307MaterialPipeline() {}

    static boolean isInstalled() {
        return installed;
    }

    static boolean install(ClassLoader classLoader, LiquidDockConfig config) {
        if (installed) return true;
        final Class<?> backgroundClass = loadOptionalClass(classLoader, BACKGROUND_CLASS);
        final Class<?> themedBackgroundClass = loadOptionalClass(classLoader, THEMED_BACKGROUND_CLASS);
        if (backgroundClass == null && themedBackgroundClass == null) {
            MainHook.log("[DC] MiuiX 307 material unavailable: supported background classes missing");
            return false;
        }

        try {
            // Restore only the old DragController -> drag-surface exclusion behavior that the
            // specialized 307 early-return would otherwise bypass. This intentionally does NOT
            // install MainHook's complete legacy capture/gesture lifecycle.
            Miuix307DragCaptureHook.install(classLoader);
            installHomeGesturePrearm(classLoader);

            HookUtil.hookMethod(classLoader,
                    "com.miui.home.launcher.Launcher", "setupViews",
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (MainHook.isWorkstationMode()) return result;
                        try {
                            Object launcher = chain.getThisObject();
                            launcherRef = new WeakReference<>(launcher);
                            Object hotSeats = HookUtil.getField(launcher, "mHotSeats");
                            hotSeatsRef = new WeakReference<>(hotSeats);
                            View background = resolveBackground(hotSeats);
                            if (background == null) {
                                MainHook.log("[DC] MiuiX 307 supported background not found in setupViews");
                                return result;
                            }

                            View workspace = null;
                            try {
                                Object value = HookUtil.getField(launcher, "mWorkspace");
                                if (value instanceof View) workspace = (View) value;
                            } catch (Throwable ignored) {}
                            if (workspace != null) workspaceRef = workspace;

                            if (!ensureGlassBound(background, config, classLoader)) {
                                MainHook.log("[DC] MiuiX 307 real glass install returned false");
                            }
                        } catch (Throwable error) {
                            MainHook.log("[DC] MiuiX 307 real glass bind failed: " + error);
                        }
                        return result;
                    });

            if (backgroundClass != null) {
                installMiuixGeometryHooks(backgroundClass, config, classLoader);
            }
            if (themedBackgroundClass != null) {
                installThemedBackgroundHooks(themedBackgroundClass, config, classLoader);
            }

            // Decompiled 307 Launcher emits StateNotifyUtils.sendStateBroadcast(..., "toHome",
            // ...) before the APP->HOME icon-flight animation. Feed only that native boundary
            // into DockLiquidGlassView's existing HOME gesture target so the old APP capture is
            // scene-stale before animation pixels can be installed. This is optional/fail-open:
            // a vendor signature change must not disable the entire 307 material pipeline.
            try {
                HookUtil.hookMethod(classLoader,
                        "com.miui.home.recents.util.StateNotifyUtils", "sendStateBroadcast",
                        chain -> {
                            Object[] args = chain.getArgs().toArray(new Object[0]);
                            for (Object arg : args) {
                                if ("toHome".equals(arg)) {
                                    MiuixGlassHook.onHomeTransitionStart();
                                    break;
                                }
                            }
                            return chain.proceed(args);
                        }, android.content.Context.class,
                        String.class, String.class, String.class);
                MainHook.log("[DC] MiuiX 307 native toHome backdrop hook installed");
            } catch (Throwable error) {
                MainHook.log("[DC] MiuiX 307 native toHome hook unavailable: " + error);
            }

            installed = true;
            MainHook.log("[DC] MiuiX 307 real glass pipeline hooks installed");
            return true;
        } catch (Throwable error) {
            MainHook.log("[DC] MiuiX 307 material hook install failed: " + error);
            return false;
        }
    }

    private static Class<?> loadOptionalClass(ClassLoader classLoader, String name) {
        try {
            return Class.forName(name, false, classLoader);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Native MiuiX implementation exposes explicit width/height/radius setters. */
    private static void installMiuixGeometryHooks(
            Class<?> backgroundClass, LiquidDockConfig config, ClassLoader classLoader) {
        HookUtil.hookMethod(backgroundClass, "setBackgroundWidth",
                new Class<?>[]{int.class}, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    View background = (View) chain.getThisObject();
                    ensureGlassBound(background, config, classLoader);
                    MiuixGlassHook.syncSize(background);
                    return result;
                });
        HookUtil.hookMethod(backgroundClass, "setBackgroundHeight",
                new Class<?>[]{int.class}, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    View background = (View) chain.getThisObject();
                    ensureGlassBound(background, config, classLoader);
                    MiuixGlassHook.syncSize(background);
                    return result;
                });
        HookUtil.hookMethod(backgroundClass, "setBackgroundRadius",
                new Class<?>[]{float.class}, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    View background = (View) chain.getThisObject();
                    ensureGlassBound(background, config, classLoader);
                    MiuixGlassHook.syncGeometry(background, config);
                    return result;
                });
    }

    /**
     * Third-party icon themes switch 307 to HotSeatsListContentBlurBackground2. Device logs show
     * that implementation drives geometry through triggerMeasure rather than the MiuiX setters,
     * so hook every runtime overload by Method identity and reuse the same Prismal installer.
     */
    private static void installThemedBackgroundHooks(
            Class<?> backgroundClass, LiquidDockConfig config, ClassLoader classLoader) {
        int hooked = 0;
        Class<?> cursor = backgroundClass;
        while (cursor != null && cursor != Object.class) {
            for (Method method : cursor.getDeclaredMethods()) {
                if (!"triggerMeasure".equals(method.getName())
                        || Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                HookUtil.hook(method, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    Object owner = chain.getThisObject();
                    if (owner instanceof View) {
                        View background = (View) owner;
                        ensureGlassBound(background, config, classLoader);
                        MiuixGlassHook.syncSize(background);
                        MiuixGlassHook.syncGeometry(background, config);
                    }
                    return result;
                });
                hooked++;
            }
            cursor = cursor.getSuperclass();
        }
        MainHook.log("[DC] MiuiX 307 themed background geometry hooks installed count=" + hooked);
    }

    /**
     * 307's specialized early return intentionally skips LauncherSceneController. Restore only
     * the native side-swipe HOME event that the legacy controller used to observe, and converge
     * it on the same wallpaper prearm as the existing StateNotifyUtils("toHome") signal.
     */
    private static void installHomeGesturePrearm(ClassLoader classLoader) {
        try {
            Class<?> eventClass = Class.forName(
                    "com.miui.home.launcher.dock.v3.GestureToHome", false, classLoader);
            int hooked = 0;
            for (java.lang.reflect.Constructor<?> ctor : eventClass.getDeclaredConstructors()) {
                HookUtil.hook(ctor, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    MiuixGlassHook.onHomeTransitionStart();
                    return result;
                });
                hooked++;
            }
            MainHook.log("[DC] MiuiX 307 GestureToHome wallpaper prearm installed constructors="
                    + hooked);
        } catch (Throwable error) {
            MainHook.log("[DC] MiuiX 307 GestureToHome prearm unavailable: " + error);
        }
    }

    /**
     * Self-heal when HyperOS replaces the active HotSeats material background. setupViews is not
     * a reliable per-instance boundary on 307, so each supported geometry callback can rebind.
     */
    private static boolean ensureGlassBound(
            View background, LiquidDockConfig config, ClassLoader classLoader) {
        if (background == null || !isSupportedBackground(background)) return false;
        if (MiuixGlassHook.isBoundTo(background)) {
            Miuix307DragCaptureHook.bind(background);
            observeBoundHierarchy(background, config, classLoader);
            return true;
        }

        // Remove observers before MiuixGlassHook replaces an old host so our own controlled
        // rebind cannot be mistaken for an external theme/hierarchy invalidation.
        clearHierarchyObservation();

        // Do not leave a detached previous hierarchy as the drag target during an instance swap.
        Miuix307DragCaptureHook.bind(null);
        MainHook.log("[DC] MiuiX 307 background instance changed; rebinding Prismal glass"
                + " class=" + background.getClass().getSimpleName()
                + " instance=" + Integer.toHexString(System.identityHashCode(background)));
        boolean installedNow = MiuixGlassHook.install(
                background, workspaceRef, config, null, classLoader);
        if (!installedNow) {
            // Geometry may arrive before the new background is parented. The matching geometry
            // callback or hierarchy recovery will retry naturally; never poll with a fixed delay.
            MainHook.log("[DC] MiuiX 307 background rebind deferred; parent not ready");
        } else {
            Miuix307DragCaptureHook.bind(background);
            observeBoundHierarchy(background, config, classLoader);
        }
        return installedNow;
    }

    /** Observe both pieces that define a valid binding: vendor background and injected host. */
    private static void observeBoundHierarchy(
            View background, LiquidDockConfig config, ClassLoader classLoader) {
        if (background == null) return;
        View host = resolveBoundHost(background);
        if (host == null) {
            MainHook.log("[DC] MiuiX 307 bound host not found for hierarchy observation");
            return;
        }
        if (observedBackground == background && observedHost == host
                && hierarchyListener != null) {
            return;
        }

        clearHierarchyObservation();
        final View watchedBackground = background;
        final View watchedHost = host;
        View.OnAttachStateChangeListener listener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}

            @Override public void onViewDetachedFromWindow(View v) {
                if (v != watchedBackground && v != watchedHost) return;
                MainHook.log("[DC] MiuiX 307 hierarchy invalidated; rebind scheduled source="
                        + (v == watchedHost ? "host" : "background"));
                scheduleHierarchyRebind(config, classLoader);
            }
        };
        background.addOnAttachStateChangeListener(listener);
        host.addOnAttachStateChangeListener(listener);
        observedBackground = background;
        observedHost = host;
        hierarchyListener = listener;
    }

    private static void clearHierarchyObservation() {
        View background = observedBackground;
        View host = observedHost;
        View.OnAttachStateChangeListener listener = hierarchyListener;
        observedBackground = null;
        observedHost = null;
        hierarchyListener = null;
        if (listener == null) return;
        try {
            if (background != null) background.removeOnAttachStateChangeListener(listener);
        } catch (Throwable ignored) {}
        try {
            if (host != null) host.removeOnAttachStateChangeListener(listener);
        } catch (Throwable ignored) {}
    }

    /**
     * Coalesce a theme/hierarchy burst into one next-main-turn repair. Theme/icon changes can
     * leave the old background discoverable with a parent while it is already detached, so a
     * parent check alone is not authoritative. If the new hierarchy is not attached yet, wait
     * for a real global-layout event instead of polling with an arbitrary delay.
     */
    private static void scheduleHierarchyRebind(
            LiquidDockConfig config, ClassLoader classLoader) {
        if (hierarchyRebindPosted) return;
        hierarchyRebindPosted = true;
        if (MAIN_HANDLER == null) {
            MAIN_HANDLER = new Handler(Looper.getMainLooper());
        }
        MAIN_HANDLER.post(() -> {
            hierarchyRebindPosted = false;
            if (tryHierarchyRebind(config, classLoader)) {
                clearHierarchyLayoutRecovery();
                return;
            }
            armHierarchyLayoutRecovery(config, classLoader);
        });
    }

    private static boolean tryHierarchyRebind(
            LiquidDockConfig config, ClassLoader classLoader) {
        Object hotSeats = resolveCurrentHotSeats();
        if (hotSeats == null) {
            MainHook.log("[DC] MiuiX 307 hierarchy rebind deferred; HotSeats owner gone");
            return false;
        }
        View currentBackground = resolveBackground(hotSeats);
        if (currentBackground == null || !currentBackground.isAttachedToWindow()
                || !(currentBackground.getParent() instanceof ViewGroup)) {
            MainHook.log("[DC] MiuiX 307 hierarchy rebind deferred; background not attached");
            return false;
        }
        if (!ensureGlassBound(currentBackground, config, classLoader)) {
            MainHook.log("[DC] MiuiX 307 hierarchy rebind deferred; install not ready");
            return false;
        }
        View host = resolveBoundHost(currentBackground);
        if (host == null || !host.isAttachedToWindow()) {
            MainHook.log("[DC] MiuiX 307 hierarchy rebind deferred; host not attached");
            return false;
        }
        MainHook.log("[DC] MiuiX 307 hierarchy rebind complete after theme/layout change");
        return true;
    }

    private static void armHierarchyLayoutRecovery(
            LiquidDockConfig config, ClassLoader classLoader) {
        Object hotSeats = resolveCurrentHotSeats();
        View owner = workspaceRef != null && workspaceRef.isAttachedToWindow()
                ? workspaceRef : hotSeats instanceof View ? (View) hotSeats : null;
        if (owner == null) return;
        View root = owner.getRootView();
        ViewTreeObserver observer = (root != null ? root : owner).getViewTreeObserver();
        if (observer == null || !observer.isAlive()) return;
        if (hierarchyRecoveryObserver == observer && hierarchyRecoveryListener != null) return;

        clearHierarchyLayoutRecovery();
        ViewTreeObserver.OnGlobalLayoutListener listener = () -> {
            if (tryHierarchyRebind(config, classLoader)) {
                clearHierarchyLayoutRecovery();
            }
        };
        observer.addOnGlobalLayoutListener(listener);
        hierarchyRecoveryObserver = observer;
        hierarchyRecoveryListener = listener;
        MainHook.log("[DC] MiuiX 307 hierarchy recovery armed for next real layout");
    }

    private static void clearHierarchyLayoutRecovery() {
        ViewTreeObserver observer = hierarchyRecoveryObserver;
        ViewTreeObserver.OnGlobalLayoutListener listener = hierarchyRecoveryListener;
        hierarchyRecoveryObserver = null;
        hierarchyRecoveryListener = null;
        if (observer == null || listener == null) return;
        try {
            if (observer.isAlive()) observer.removeOnGlobalLayoutListener(listener);
        } catch (Throwable ignored) {}
    }

    /** Resolve only the host injected beside this exact background in its current parent. */
    private static View resolveBoundHost(View background) {
        if (background == null || !(background.getParent() instanceof ViewGroup)) return null;
        ViewGroup parent = (ViewGroup) background.getParent();
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof DockLiquidGlassHostView) return child;
        }
        return null;
    }

    /** Re-read the current HotSeats from Launcher because theme changes may replace it. */
    private static Object resolveCurrentHotSeats() {
        Object launcher = launcherRef.get();
        if (launcher != null) {
            try {
                Object current = HookUtil.getField(launcher, "mHotSeats");
                if (current != null) {
                    hotSeatsRef = new WeakReference<>(current);
                    return current;
                }
            } catch (Throwable ignored) {}
        }
        return hotSeatsRef.get();
    }

    private static View resolveBackground(Object hotSeats) {
        if (hotSeats == null) return null;

        // New Launcher exposes whichever material background is currently active. Theme packs can
        // switch between the MiuiX implementation and BlurBackground2 without restarting Launcher.
        try {
            Object value = HookUtil.invoke(hotSeats, "getHotSeatsBackground");
            if (value instanceof View && isSupportedBackground((View) value)) {
                MainHook.log("[DC] getHotSeatsBackground returned " + value.getClass().getName());
                return (View) value;
            }
        } catch (Throwable ignored) {}

        return hotSeats instanceof View ? findBackground((View) hotSeats) : null;
    }

    private static boolean isSupportedBackground(View view) {
        if (view == null) return false;
        String name = view.getClass().getName();
        return BACKGROUND_CLASS.equals(name) || THEMED_BACKGROUND_CLASS.equals(name);
    }

    private static View findBackground(View root) {
        if (root == null) return null;
        if (isSupportedBackground(root)) return root;
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findBackground(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }
}
