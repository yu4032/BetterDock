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
 * Keep the live vendor background as the authoritative visual shell while LiquidDock composes
 * Prismal inside it; vendor compositor blur is explicitly suppressed.
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
    private static WeakReference<View> workspaceRef = new WeakReference<>(null);
    private static WeakReference<Object> launcherRef = new WeakReference<>(null);
    private static WeakReference<Object> hotSeatsRef = new WeakReference<>(null);
    private static WeakReference<View> observedBackgroundRef = new WeakReference<>(null);
    private static WeakReference<View> observedHostRef = new WeakReference<>(null);
    private static View.OnAttachStateChangeListener hierarchyListener;
    private static boolean hierarchyRebindPosted;
    private static WeakReference<ViewTreeObserver> hierarchyRecoveryObserverRef = new WeakReference<>(null);
    private static ViewTreeObserver.OnGlobalLayoutListener hierarchyRecoveryListener;
    // Log only once while one vendor instance is still in its startup placeholder geometry.
    private static WeakReference<View> geometryDeferredLoggedFor = new WeakReference<>(null);

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
            installCompatBackgroundBlurSuppression(classLoader);
            installDockCustomizationCompatibility(classLoader, config);
            installHotSeatsAttachRecovery(classLoader, config);
            installWorkstationResumeProducerRecovery(classLoader);

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
                            if (workspace != null) workspaceRef = new WeakReference<>(workspace);

                            if (!ensureGlassBound(background, config, classLoader)) {
                                MainHook.log("[DC] MiuiX 307 real glass handoff pending");
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

            installed = true;
            MainHook.log("[DC] MiuiX 307 real glass pipeline hooks installed");
            return true;
        } catch (Throwable error) {
            MainHook.log("[DC] MiuiX 307 material hook install failed: " + error);
            return false;
        }
    }

    /**
     * HotSeats is the stable lifecycle owner across both default MiuiX and themed material
     * implementations. Recover the active material at the concrete HotSeats attach boundary.
     */
    private static void installHotSeatsAttachRecovery(
            ClassLoader classLoader, LiquidDockConfig config) {
        try {
            Class<?> hotSeatsClass = Class.forName(
                    "com.miui.home.launcher.hotseats.HotSeats", false, classLoader);
            Method attach = hotSeatsClass.getDeclaredMethod("onAttachedToWindow");
            HookUtil.hook(attach, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (MainHook.isWorkstationMode()) return result;
                Object hotSeats = chain.getThisObject();
                hotSeatsRef = new WeakReference<>(hotSeats);
                View background = resolveBackground(hotSeats);
                if (background == null) {
                    MainHook.log("[DC] MiuiX 307 HotSeats attach recovery: background not ready");
                    return result;
                }
                if (!ensureGlassBound(background, config, classLoader)) {
                    MainHook.log("[DC] MiuiX 307 HotSeats attach recovery deferred");
                    return result;
                }
                MiuixGlassHook.syncSize(background);
                MainHook.syncDockShadow(background, config.dock);
                MiuixGlassHook.syncGeometry(background, config);
                MainHook.log("[DC] MiuiX 307 HotSeats attach recovery complete class="
                        + background.getClass().getSimpleName());
                return result;
            });
            MainHook.log("[DC] MiuiX 307 HotSeats attach recovery installed");
        } catch (Throwable error) {
            MainHook.log("[DC] MiuiX 307 HotSeats attach recovery unavailable: " + error);
        }
    }

    /**
     * A fullscreen workstation app can disconnect SurfaceFlinger's PassBlur producer while the
     * Java TextureView hierarchy remains attached. Launcher.onResume is the device-verified
     * recovery boundary: replace only the producer, preserving the current glass hierarchy.
     */
    private static void installWorkstationResumeProducerRecovery(ClassLoader classLoader) {
        try {
            Class<?> launcherClass = Class.forName(
                    "com.miui.home.launcher.Launcher", false, classLoader);
            Method resume = launcherClass.getDeclaredMethod("onResume");
            HookUtil.hook(resume, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (MainHook.isWorkstationMode()) {
                    Miuix307ZeroCopyRenderer.rebindProducer("workstation-launcher-resume");
                }
                return result;
            });
            MainHook.log("[DC] MiuiX 307 workstation resume producer recovery installed");
        } catch (Throwable error) {
            MainHook.log("[DC] MiuiX 307 workstation resume producer recovery unavailable: "
                    + error);
        }
    }

    private static Class<?> loadOptionalClass(ClassLoader classLoader, String name) {
        try {
            return Class.forName(name, false, classLoader);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * BlurBackground2.addBlur() delegates positive vendor blur through this exact utility before
     * reflection reaches hidden View APIs. On 307 that becomes a post-composition region blur on
     * the Floating Dock Surface, so the themed HotSeats radius must be zero while Prismal is the
     * visual owner. Other BlurUtilities consumers, disable calls and vendor arrays pass through.
     */
    private static void installCompatBackgroundBlurSuppression(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader,
                    "com.miui.home.launcher.common.BlurUtilities", "setBackgroundBlur",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (args.length >= 2 && args[0] instanceof View
                                && args[1] instanceof Integer) {
                            args[1] = MiuixGlassHook.suppressCompatBackgroundBlurRadius(
                                    (View) args[0], (Integer) args[1]);
                        }
                        return chain.proceed(args);
                    }, View.class, int.class, float[].class, int[][].class);
            MainHook.log("[DC] MiuiX 307 compat background blur suppression installed");
        } catch (Throwable error) {
            MainHook.log("[DC] MiuiX 307 compat background blur suppression unavailable: "
                    + error);
        }
    }

    /** Restore the non-glass Dock customization hooks skipped by MainHook's 307 early return. */
    private static void installDockCustomizationCompatibility(
            ClassLoader classLoader, LiquidDockConfig config) {
        LiquidDockConfig.Dock dock = config.dock;
        if (dock == null || !dock.enabled) return;
        float density = android.content.res.Resources.getSystem().getDisplayMetrics().density;
        float dimensionScale = dock.dimensionsDp ? density : 1f;
        int spacing = Math.round(dock.spacing * dimensionScale);

        if (spacing != 0) {
            try {
                Class<?> recyclerView = Class.forName(
                        "androidx.recyclerview.widget.RecyclerView", false, classLoader);
                Class<?> recyclerState = Class.forName(
                        "androidx.recyclerview.widget.RecyclerView$State", false, classLoader);
                HookUtil.hookMethod(classLoader,
                        "com.miui.home.launcher.hotseats.HotSeatsListContentLayoutManager$OffsetDecoration",
                        "getItemOffsets",
                        chain -> {
                            Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                            if (MainHook.isWorkstationMode()) return result;
                            android.graphics.Rect out = (android.graphics.Rect) chain.getArgs().get(0);
                            out.left += spacing;
                            out.right += spacing;
                            return result;
                        }, android.graphics.Rect.class, View.class, recyclerView, recyclerState);

                Class<?> layoutManager = Class.forName(
                        "com.miui.home.launcher.hotseats.HotSeatsListContentLayoutManager",
                        false, classLoader);
                HookUtil.hookMethod(layoutManager, "updateBackgroundView",
                        new Class<?>[]{android.widget.FrameLayout.class, int.class, int.class, float.class},
                        chain -> {
                            if (MainHook.isWorkstationMode()) {
                                return chain.proceed(chain.getArgs().toArray(new Object[0]));
                            }
                            int itemCount = (Integer) HookUtil.invoke(
                                    chain.getThisObject(), "getItemCount");
                            Object[] args = chain.getArgs().toArray(new Object[0]);
                            if (itemCount > 0) args[1] = (Integer) args[1] + spacing * 2 * itemCount;
                            return chain.proceed(args);
                        });
            } catch (Throwable error) {
                MainHook.log("[DC] MiuiX 307 spacing hook unavailable: " + error);
            }
        }
    }

    /** Native MiuiX implementation exposes explicit width/height/radius setters. */
    private static void installMiuixGeometryHooks(
            Class<?> backgroundClass, LiquidDockConfig config, ClassLoader classLoader) {
        LiquidDockConfig.Dock dock = config.dock;
        float density = android.content.res.Resources.getSystem().getDisplayMetrics().density;
        float dimensionScale = dock.dimensionsDp ? density : 1f;
        float cornerScale = dock.cornersDp ? density : 1f;
        int widthOffset = dock.enabled ? Math.round(dock.widthOffset * dimensionScale) : 0;
        int heightOffset = dock.enabled ? Math.round(dock.heightOffset * dimensionScale) : 0;
        float blurCornerOffset = dock.enabled ? dock.blurCornerOffset * cornerScale : 0f;

        HookUtil.hookMethod(backgroundClass, "setBackgroundWidth",
                new Class<?>[]{int.class}, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    if (!MainHook.isWorkstationMode() && widthOffset != 0) {
                        args[0] = (Integer) args[0] + widthOffset;
                    }
                    Object result = chain.proceed(args);
                    View background = (View) chain.getThisObject();
                    ensureGlassBound(background, config, classLoader);
                    MiuixGlassHook.syncSize(background);
                    MainHook.syncDockShadow(background, config.dock);
                    return result;
                });
        HookUtil.hookMethod(backgroundClass, "setBackgroundHeight",
                new Class<?>[]{int.class}, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    if (!MainHook.isWorkstationMode() && heightOffset != 0) {
                        args[0] = (Integer) args[0] + heightOffset;
                    }
                    Object result = chain.proceed(args);
                    View background = (View) chain.getThisObject();
                    ensureGlassBound(background, config, classLoader);
                    MiuixGlassHook.syncSize(background);
                    MainHook.syncDockShadow(background, config.dock);
                    return result;
                });
        HookUtil.hookMethod(backgroundClass, "setBackgroundRadius",
                new Class<?>[]{float.class}, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    if (!MainHook.isWorkstationMode() && blurCornerOffset != 0f) {
                        args[0] = Math.max(0f, (Float) args[0] + blurCornerOffset);
                    }
                    Object result = chain.proceed(args);
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
        LiquidDockConfig.Dock dock = config.dock;
        float density = android.content.res.Resources.getSystem().getDisplayMetrics().density;
        float dimensionScale = dock.dimensionsDp ? density : 1f;
        float cornerScale = dock.cornersDp ? density : 1f;
        int widthOffset = dock.enabled ? Math.round(dock.widthOffset * dimensionScale) : 0;
        int heightOffset = dock.enabled ? Math.round(dock.heightOffset * dimensionScale) : 0;
        float blurCornerOffset = dock.enabled ? dock.blurCornerOffset * cornerScale : 0f;

        HookUtil.hookMethod(backgroundClass, "onAttachedToWindow", new Class<?>[0], chain -> {
            Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
            View background = (View) chain.getThisObject();
            ensureGlassBound(background, config, classLoader);
            MiuixGlassHook.syncGeometry(background, config);
            return result;
        });
        HookUtil.hookMethod(backgroundClass, "setBackgroundWidth",
                new Class<?>[]{int.class}, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    if (!MainHook.isWorkstationMode() && widthOffset != 0) {
                        args[0] = (Integer) args[0] + widthOffset;
                    }
                    Object result = chain.proceed(args);
                    View background = (View) chain.getThisObject();
                    ensureGlassBound(background, config, classLoader);
                    MiuixGlassHook.syncSize(background);
                    MainHook.syncDockShadow(background, config.dock);
                    return result;
                });
        HookUtil.hookMethod(backgroundClass, "setBackgroundHeight",
                new Class<?>[]{int.class}, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    if (!MainHook.isWorkstationMode() && heightOffset != 0) {
                        args[0] = (Integer) args[0] + heightOffset;
                    }
                    Object result = chain.proceed(args);
                    View background = (View) chain.getThisObject();
                    ensureGlassBound(background, config, classLoader);
                    MiuixGlassHook.syncSize(background);
                    MainHook.syncDockShadow(background, config.dock);
                    return result;
                });
        HookUtil.hookMethod(backgroundClass, "setBackgroundRadius",
                new Class<?>[]{float.class}, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    if (!MainHook.isWorkstationMode() && blurCornerOffset != 0f) {
                        args[0] = Math.max(0f, (Float) args[0] + blurCornerOffset);
                    }
                    Object result = chain.proceed(args);
                    View background = (View) chain.getThisObject();
                    ensureGlassBound(background, config, classLoader);
                    MiuixGlassHook.syncGeometry(background, config);
                    return result;
                });

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
                        MainHook.syncDockShadow(background, config.dock);
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
     * Self-heal when HyperOS replaces the active HotSeats material background. setupViews is not
     * a reliable per-instance boundary on 307, so each supported geometry callback can rebind.
     */
    private static boolean ensureGlassBound(
            View background, LiquidDockConfig config, ClassLoader classLoader) {
        if (background == null || !isSupportedBackground(background)) return false;
        if (MiuixGlassHook.isBoundTo(background)) {
            geometryDeferredLoggedFor = new WeakReference<>(null);
            observeBoundHierarchy(background, config, classLoader);
            return true;
        }

        // setupViews/onAttachedToWindow run before BlurBackground2 has committed its real radius.
        // Preserve the untouched vendor material during that placeholder phase. Existing vendor
        // setBackgroundRadius/triggerMeasure callbacks naturally retry this method once geometry
        // is valid, so no fixed-delay polling is needed.
        if (!MiuixGlassHook.hasReadyNativeGeometry(background)) {
            if (geometryDeferredLoggedFor.get() != background) {
                geometryDeferredLoggedFor = new WeakReference<>(background);
                MainHook.log("[DC] MiuiX 307 Prismal handoff deferred; native geometry not ready"
                        + " class=" + background.getClass().getSimpleName()
                        + " size=" + background.getWidth() + "x" + background.getHeight()
                        + " radius=" + MiuixGlassHook.readNativeOpticsRadius(background));
            }
            return false;
        }
        if (geometryDeferredLoggedFor.get() == background) {
            MainHook.log("[DC] MiuiX 307 native geometry ready; committing Prismal handoff"
                    + " size=" + background.getWidth() + "x" + background.getHeight()
                    + " radius=" + MiuixGlassHook.readNativeOpticsRadius(background));
            geometryDeferredLoggedFor = new WeakReference<>(null);
        }

        // Remove observers before MiuixGlassHook replaces an old host so our own controlled
        // rebind cannot be mistaken for an external theme/hierarchy invalidation.
        clearHierarchyObservation();

        // Do not leave a detached previous hierarchy as the drag target during an instance swap.
        MainHook.log("[DC] MiuiX 307 background instance changed; rebinding Prismal glass"
                + " class=" + background.getClass().getSimpleName()
                + " instance=" + Integer.toHexString(System.identityHashCode(background)));
        boolean installedNow = MiuixGlassHook.install(background, config);
        if (!installedNow) {
            // Geometry may arrive before the new background is parented. The matching geometry
            // callback or hierarchy recovery will retry naturally; never poll with a fixed delay.
            MainHook.log("[DC] MiuiX 307 background rebind deferred; parent not ready");
        } else {
            MainHook.syncDockShadow(background, config.dock);
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
        if (observedBackgroundRef.get() == background && observedHostRef.get() == host
                && hierarchyListener != null) {
            return;
        }

        clearHierarchyObservation();
        final WeakReference<View> watchedBackgroundRef = new WeakReference<>(background);
        final WeakReference<View> watchedHostRef = new WeakReference<>(host);
        View.OnAttachStateChangeListener listener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}

            @Override public void onViewDetachedFromWindow(View v) {
                View watchedBackground = watchedBackgroundRef.get();
                View watchedHost = watchedHostRef.get();
                if (v != watchedBackground && v != watchedHost) return;
                MainHook.log("[DC] MiuiX 307 hierarchy invalidated; rebind scheduled source="
                        + (v == watchedHost ? "host" : "background"));
                clearHierarchyObservation();
                clearHierarchyLayoutRecovery();
                scheduleHierarchyRebind(config, classLoader);
            }
        };
        background.addOnAttachStateChangeListener(listener);
        host.addOnAttachStateChangeListener(listener);
        observedBackgroundRef = new WeakReference<>(background);
        observedHostRef = new WeakReference<>(host);
        hierarchyListener = listener;
    }

    private static void clearHierarchyObservation() {
        View background = observedBackgroundRef.get();
        View host = observedHostRef.get();
        View.OnAttachStateChangeListener listener = hierarchyListener;
        observedBackgroundRef = new WeakReference<>(null);
        observedHostRef = new WeakReference<>(null);
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
        MainHook.syncDockShadow(currentBackground, config.dock);
        MainHook.log("[DC] MiuiX 307 hierarchy rebind complete after theme/layout change");
        return true;
    }

    private static void armHierarchyLayoutRecovery(
            LiquidDockConfig config, ClassLoader classLoader) {
        Object hotSeats = resolveCurrentHotSeats();
        View workspace = workspaceRef.get();
        View owner = workspace != null && workspace.isAttachedToWindow()
                ? workspace : hotSeats instanceof View ? (View) hotSeats : null;
        if (owner == null) return;
        View root = owner.getRootView();
        ViewTreeObserver observer = (root != null ? root : owner).getViewTreeObserver();
        if (observer == null || !observer.isAlive()) return;
        if (hierarchyRecoveryObserverRef.get() == observer && hierarchyRecoveryListener != null) return;

        clearHierarchyLayoutRecovery();
        ViewTreeObserver.OnGlobalLayoutListener listener = () -> {
            if (tryHierarchyRebind(config, classLoader)) {
                clearHierarchyLayoutRecovery();
            }
        };
        observer.addOnGlobalLayoutListener(listener);
        hierarchyRecoveryObserverRef = new WeakReference<>(observer);
        hierarchyRecoveryListener = listener;
        MainHook.log("[DC] MiuiX 307 hierarchy recovery armed for next real layout");
    }

    private static void clearHierarchyLayoutRecovery() {
        ViewTreeObserver observer = hierarchyRecoveryObserverRef.get();
        ViewTreeObserver.OnGlobalLayoutListener listener = hierarchyRecoveryListener;
        hierarchyRecoveryObserverRef = new WeakReference<>(null);
        hierarchyRecoveryListener = null;
        if (observer == null || listener == null) return;
        try {
            if (observer.isAlive()) observer.removeOnGlobalLayoutListener(listener);
        } catch (Throwable ignored) {}
    }

    /** Resolve only the host injected beside this exact background in its current parent. */
    private static View resolveBoundHost(View background) {
        if (background == null) return null;
        // New in-place architecture: the LiquidDock host is a child of this exact material View.
        if (background instanceof ViewGroup) {
            ViewGroup material = (ViewGroup) background;
            for (int i = 0; i < material.getChildCount(); i++) {
                View child = material.getChildAt(i);
                if (child instanceof DockLiquidGlassHostView) return child;
            }
        }
        // Transitional fallback for a stale sibling host while an older hierarchy is detaching.
        if (!(background.getParent() instanceof ViewGroup)) return null;
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
