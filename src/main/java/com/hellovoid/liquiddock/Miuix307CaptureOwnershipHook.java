package com.hellovoid.liquiddock;

import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Capture-state compatibility for the specialized MiuiX 307 pipeline.
 *
 * MainHook deliberately returns early once the 307 material pipeline is installed. That keeps the
 * old capture/gesture hooks from fighting the native material owner, but it also skips two pieces
 * of state that are still authoritative: SystemUI panel expansion and workstation mode. This
 * bridge restores only those ownership signals and resolves the workstation Dock's own window
 * Surface before a workstation mode-1 capture is allowed to start.
 */
final class Miuix307CaptureOwnershipHook {
    private static final String TAG = "[DC][MG]";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private static volatile boolean systemUiPanelExpanded;
    private static WeakReference<DockLiquidGlassView> workstationInjectedGlass =
            new WeakReference<>(null);

    private Miuix307CaptureOwnershipHook() {}

    static void install(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        installSystemUiPanelBridge(classLoader);
        installWorkstationModeBridge();
        installGlassBindBridge();
        installWorkstationCaptureGate();
        MainHook.log(TAG + " 307 capture ownership bridge installed");
    }

    /** Device-proven Launcher state for both Control Center and notification shade expansion. */
    private static void installSystemUiPanelBridge(ClassLoader classLoader) {
        try {
            Class<?> deviceConfig = Class.forName(
                    "com.miui.home.launcher.DeviceConfig", false, classLoader);
            HookUtil.hookMethod(deviceConfig, "setControlPanelExpanded",
                    new Class<?>[]{boolean.class}, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (!Miuix307MaterialPipeline.isInstalled()) return result;

                        boolean expanded = Boolean.TRUE.equals(chain.getArgs().get(0));
                        systemUiPanelExpanded = expanded;
                        DockLiquidGlassView glass = boundGlass();
                        if (glass != null) glass.setSystemUiPanelExpanded(expanded);
                        MainHook.log(TAG + " SystemUI panel expanded=" + expanded);
                        return result;
                    });
            MainHook.log(TAG + " SystemUI panel capture gate installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " SystemUI panel capture gate unavailable: " + error);
        }
    }

    /**
     * MainHook remains the authoritative workstation transition source on 307. The legacy
     * DockLiquidGlassView.setWorkstationMode(true) API is deliberately NOT forwarded: that API
     * means "hide the normal glass because another workstation background owns the Dock". The
     * MiuiX 307 material is itself that visible glass, so only request a new capture here. Its
     * workstation source policy is scoped to startCapture() below and never becomes lifecycle
     * state on the View.
     */
    private static void installWorkstationModeBridge() {
        try {
            HookUtil.hookMethod(MainHook.class, "setWorkstationMode",
                    new Class<?>[]{boolean.class}, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (!Miuix307MaterialPipeline.isInstalled()) return result;

                        boolean enabled = Boolean.TRUE.equals(chain.getArgs().get(0));
                        DockLiquidGlassView glass = boundGlass();
                        if (glass != null) {
                            if (!enabled) clearWorkstationExclusion(glass);
                            glass.requestCapture(enabled
                                    ? "miuix307-workstation-enter"
                                    : "miuix307-workstation-exit");
                        }
                        MainHook.log(TAG + " workstation capture ownership=" + enabled);
                        return result;
                    });
            MainHook.log(TAG + " workstation mode bridge installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " workstation mode bridge unavailable: " + error);
        }
    }

    /** A newly rebound themed/native 307 glass must inherit state that may predate that View. */
    private static void installGlassBindBridge() {
        try {
            HookUtil.hookMethod(MiuixGlassHook.class, "install",
                    new Class<?>[]{View.class, View.class, LiquidDockConfig.class,
                            Object.class, ClassLoader.class}, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (Boolean.TRUE.equals(result)) syncBoundGlassState();
                        return result;
                    });
            MainHook.log(TAG + " glass ownership resync hook installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " glass ownership resync hook unavailable: " + error);
        }
    }

    private static void syncBoundGlassState() {
        if (!Miuix307MaterialPipeline.isInstalled()) return;
        DockLiquidGlassView glass = boundGlass();
        if (glass == null) return;

        glass.setSystemUiPanelExpanded(systemUiPanelExpanded);
        boolean workstation = MainHook.isWorkstationMode();
        if (!workstation) clearWorkstationExclusion(glass);
        glass.requestCapture(workstation
                ? "miuix307-workstation-bind"
                : "miuix307-normal-bind");
    }

    /**
     * Workstation live capture is safe only after its separate Dock window has been resolved.
     * Never substitute the ordinary type-2997 Floating Dock. If the workstation Surface is not
     * present yet, keep the previously installed/native safe frame and refuse this capture turn.
     *
     * DockLiquidGlassView already has the correct workstation mode-1 source/exclusion policy,
     * but its persistent workstationMode lifecycle deliberately hides the legacy normal glass.
     * Enter that policy only for the synchronous startCapture decision and restore the original
     * fields before returning. The async request keeps the selected source/exclusion values in its
     * locals, while the visible 307 glass never enters legacy workstation suspension.
     */
    private static void installWorkstationCaptureGate() {
        try {
            HookUtil.hookMethod(DockLiquidGlassView.class, "startCapture", new Class<?>[0],
                    chain -> {
                        if (!Miuix307MaterialPipeline.isInstalled()
                                || !MainHook.isWorkstationMode()) {
                            return chain.proceed(chain.getArgs().toArray(new Object[0]));
                        }
                        Object owner = chain.getThisObject();
                        if (!(owner instanceof DockLiquidGlassView)) {
                            return chain.proceed(chain.getArgs().toArray(new Object[0]));
                        }

                        DockLiquidGlassView glass = (DockLiquidGlassView) owner;
                        WorkstationDockTarget target = resolveWorkstationDockTarget();
                        if (target == null) {
                            MainHook.log(TAG
                                    + " workstation Dock surface unresolved; capture remains frozen");
                            return null;
                        }

                        HookUtil.setField(glass, "dockWindowSurface", target.surface);
                        HookUtil.setField(glass, "dockWindowLayerName", target.layerName);
                        workstationInjectedGlass = new WeakReference<>(glass);

                        boolean originalWorkstationMode = Boolean.TRUE.equals(
                                HookUtil.getField(glass, "workstationMode"));
                        boolean originalFullscreenCapture = Boolean.TRUE.equals(
                                HookUtil.getField(glass, "fullscreenCapture"));
                        HookUtil.setField(glass, "workstationMode", true);
                        HookUtil.setField(glass, "fullscreenCapture", true);
                        try {
                            return chain.proceed(chain.getArgs().toArray(new Object[0]));
                        } finally {
                            HookUtil.setField(glass, "workstationMode", originalWorkstationMode);
                            HookUtil.setField(glass, "fullscreenCapture", originalFullscreenCapture);
                        }
                    });
            MainHook.log(TAG + " workstation mode-1 Dock exclusion gate installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " workstation mode-1 Dock exclusion gate unavailable: " + error);
        }
    }

    /** Resolve only the separate workstation Laptop overlay / DockContainerView window. */
    private static WorkstationDockTarget resolveWorkstationDockTarget() {
        try {
            Class<?> globalClass = Class.forName("android.view.WindowManagerGlobal");
            Method getInstance = globalClass.getDeclaredMethod("getInstance");
            getInstance.setAccessible(true);
            Object global = getInstance.invoke(null);
            Object rootsValue = HookUtil.getField(global, "mRoots");
            if (!(rootsValue instanceof List)) return null;

            List<?> roots = (List<?>) rootsValue;
            for (Object root : roots) {
                if (root == null) continue;
                try {
                    Object attrsValue = HookUtil.getField(root, "mWindowAttributes");
                    if (!(attrsValue instanceof WindowManager.LayoutParams)) continue;
                    WindowManager.LayoutParams lp = (WindowManager.LayoutParams) attrsValue;
                    CharSequence title = lp.getTitle();
                    boolean workstationTitle = isWorkstationWindowTitle(title);

                    // The device-proven workstation window is titled "Laptop overlay". Accept
                    // that identity before applying the generic type-2997 rejection: HyperOS is
                    // free to reuse private window types across releases. The ordinary Floating
                    // Dock remains explicitly forbidden by title and, absent the workstation
                    // identity, by type.
                    if (title != null && "Floating Dock".contentEquals(title)) continue;
                    if (!workstationTitle && lp.type == 2997) continue;

                    Object rootViewValue = HookUtil.getField(root, "mView");
                    if (!(rootViewValue instanceof View)) continue;
                    View rootView = (View) rootViewValue;
                    boolean dockTree = containsDockContainerView(rootView);
                    if (!workstationTitle && !dockTree) continue;

                    SurfaceControl surface = readRootSurfaceControl(root);
                    if (!isValidSurface(surface)) continue;
                    String layerName = readSurfaceLayerName(surface);
                    if ((layerName == null || layerName.isEmpty()) && title != null) {
                        layerName = title.toString();
                    }

                    MainHook.log(TAG + " workstation Dock target type=" + lp.type
                            + " title=" + title
                            + " root=" + rootView.getClass().getName()
                            + " dockTree=" + dockTree
                            + " layer=" + layerName);
                    return new WorkstationDockTarget(surface, layerName);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable error) {
            MainHook.log(TAG + " workstation Dock resolver failed: " + error);
        }
        return null;
    }

    private static boolean isWorkstationWindowTitle(CharSequence title) {
        return title != null && "Laptop overlay".contentEquals(title);
    }

    private static boolean containsDockContainerView(View view) {
        if (view == null) return false;
        if (view.getClass().getName().contains("DockContainerView")) return true;
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (containsDockContainerView(group.getChildAt(i))) return true;
        }
        return false;
    }

    private static SurfaceControl readRootSurfaceControl(Object root) {
        if (root == null) return null;
        try {
            Method method = root.getClass().getDeclaredMethod("getSurfaceControl");
            method.setAccessible(true);
            Object value = method.invoke(root);
            return value instanceof SurfaceControl ? (SurfaceControl) value : null;
        } catch (Throwable ignored) {
        }
        try {
            Object value = HookUtil.getField(root, "mSurfaceControl");
            return value instanceof SurfaceControl ? (SurfaceControl) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isValidSurface(SurfaceControl surface) {
        if (surface == null) return false;
        try {
            return surface.isValid();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String readSurfaceLayerName(SurfaceControl surface) {
        if (surface == null) return null;
        try {
            Field field = surface.getClass().getDeclaredField("mName");
            field.setAccessible(true);
            Object value = field.get(surface);
            if (value instanceof String && !((String) value).isEmpty()) return (String) value;
        } catch (Throwable ignored) {
        }
        String value = String.valueOf(surface);
        int start = value.indexOf("name=");
        int end = value.indexOf(')', start);
        return start >= 0 && end > start ? value.substring(start + 5, end) : null;
    }

    private static DockLiquidGlassView boundGlass() {
        try {
            Field field = MiuixGlassHook.class.getDeclaredField("glassRef");
            field.setAccessible(true);
            Object value = field.get(null);
            return value instanceof DockLiquidGlassView ? (DockLiquidGlassView) value : null;
        } catch (Throwable error) {
            MainHook.log(TAG + " bound 307 glass unavailable: " + error);
            return null;
        }
    }

    private static void clearWorkstationExclusion(DockLiquidGlassView glass) {
        if (glass == null) return;
        if (workstationInjectedGlass.get() == glass) {
            HookUtil.setField(glass, "dockWindowSurface", null);
            HookUtil.setField(glass, "dockWindowLayerName", null);
            workstationInjectedGlass = new WeakReference<>(null);
        }
    }

    private static final class WorkstationDockTarget {
        final SurfaceControl surface;
        final String layerName;

        WorkstationDockTarget(SurfaceControl surface, String layerName) {
            this.surface = surface;
            this.layerName = layerName;
        }
    }
}
