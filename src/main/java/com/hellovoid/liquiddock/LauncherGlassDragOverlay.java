package com.hellovoid.liquiddock;

import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/**
 * One persistent launcher drag-glass carrier per stable Launcher root.
 *
 * <p>The carrier is placed immediately below MIUI's DragContainer and owns exactly one
 * LauncherGlassSinkView. Folder, widget and icon adapters only feed source geometry into this
 * object; they never create their own drag TextureView or PassBlur producer.</p>
 */
final class LauncherGlassDragOverlay {
    private static final String TAG = "[DC][DragGlass]";
    private static final WeakHashMap<View, LauncherGlassDragOverlay> BY_ROOT = new WeakHashMap<>();

    private final WeakReference<View> rootRef;
    private final LiquidDockConfig.Glass glassConfig;
    private final LauncherGlassDragCoordinator coordinator = new LauncherGlassDragCoordinator();
    private final View carrier;
    private WeakReference<View> sourceRef = new WeakReference<>(null);
    private LauncherGlassSinkView sink;
    private WeakReference<ViewGroup> hostRef = new WeakReference<>(null);
    private float activeCornerRadiusPx;
    private boolean tracking;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) {
            if (!tracking) return;
            syncFromSource();
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private LauncherGlassDragOverlay(View root, LiquidDockConfig.Glass glassConfig) {
        rootRef = new WeakReference<>(root);
        this.glassConfig = glassConfig;
        carrier = new View(root.getContext());
        carrier.setClickable(false);
        carrier.setFocusable(false);
        carrier.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        carrier.setVisibility(View.INVISIBLE);
    }

    static boolean begin(
            View source,
            LiquidDockConfig.Glass glassConfig,
            Object token,
            LauncherGlassDragState.Kind kind,
            float cornerRadiusPx) {
        LauncherGlassDragOverlay overlay = acquire(source, glassConfig);
        return overlay != null && overlay.beginInternal(token, kind, source, cornerRadiusPx);
    }

    static void end(View source, Object token) {
        LauncherGlassDragOverlay overlay = find(source, token);
        if (overlay != null) overlay.endInternal(token);
    }

    private static LauncherGlassDragOverlay acquire(
            View source, LiquidDockConfig.Glass glassConfig) {
        View root = LauncherGlassSessionRegistry.resolveStableRoot(source);
        if (root == null) return null;
        synchronized (BY_ROOT) {
            LauncherGlassDragOverlay current = BY_ROOT.get(root);
            if (current != null) return current;
            LauncherGlassDragOverlay created = new LauncherGlassDragOverlay(root, glassConfig);
            BY_ROOT.put(root, created);
            return created;
        }
    }

    private static LauncherGlassDragOverlay find(View source, Object token) {
        View root = source != null ? source.getRootView() : null;
        synchronized (BY_ROOT) {
            LauncherGlassDragOverlay direct = root != null ? BY_ROOT.get(root) : null;
            if (direct != null && direct.owns(token)) return direct;
            for (LauncherGlassDragOverlay overlay : BY_ROOT.values()) {
                if (overlay != null && overlay.owns(token)) return overlay;
            }
        }
        return null;
    }

    private boolean beginInternal(
            Object token,
            LauncherGlassDragState.Kind kind,
            View source,
            float cornerRadiusPx) {
        if (token == null || source == null) return false;
        LauncherGlassDragState.Bounds bounds = readRootBounds(source);
        if (bounds == null) return false;
        activeCornerRadiusPx = Math.max(0f,
                Float.isFinite(cornerRadiusPx) ? cornerRadiusPx : 0f);
        sourceRef = new WeakReference<>(source);
        if (!coordinator.begin(token, kind, bounds, activeCornerRadiusPx)) return false;
        tracking = true;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        Choreographer.getInstance().postFrameCallback(frameCallback);
        syncFromSource();
        MainHook.log(TAG + " begin kind=" + kind + " source="
                + source.getClass().getSimpleName());
        return true;
    }

    private void endInternal(Object token) {
        if (!coordinator.end(token)) return;
        tracking = false;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        sourceRef = new WeakReference<>(null);
        carrier.setVisibility(View.INVISIBLE);
        if (sink != null) {
            sink.setVisibility(View.GONE);
            sink.requestLifecycleRefresh();
        }
        MainHook.log(TAG + " end");
    }

    private boolean owns(Object token) {
        LauncherGlassDragState state = coordinator.current();
        return state != null && state.token == token;
    }

    private void syncFromSource() {
        if (!tracking) return;
        View source = sourceRef.get();
        View root = rootRef.get();
        if (source == null || root == null || !root.isAttachedToWindow()) return;
        LauncherGlassDragState state = coordinator.current();
        if (state == null) return;
        LauncherGlassDragState.Bounds bounds = readRootBounds(source);
        if (bounds == null) return;
        coordinator.update(state.token, bounds,
                source.getScaleX(), source.getRotation(), 1f);
        if (!ensureCarrier(source)) return;
        applyCarrierGeometry(source);
    }

    private boolean ensureCarrier(View source) {
        if (carrier.getParent() == null) {
            View dragContainer = findDragContainerAncestor(source);
            if (dragContainer == null || !(dragContainer.getParent() instanceof ViewGroup)) {
                return false;
            }
            ViewGroup host = (ViewGroup) dragContainer.getParent();
            int index = Math.max(0, host.indexOfChild(dragContainer));
            int width = Math.max(1, source.getWidth());
            int height = Math.max(1, source.getHeight());
            host.addView(carrier, index, new ViewGroup.LayoutParams(width, height));
            carrier.layout(0, 0, width, height);
            hostRef = new WeakReference<>(host);
            MainHook.log(TAG + " attached below " + dragContainer.getClass().getSimpleName());
        }
        if (sink == null) {
            if (!carrier.isAttachedToWindow() || carrier.getWidth() <= 0 || carrier.getHeight() <= 0) {
                return false;
            }
            sink = LauncherGlassSinkView.attachToMaterial(
                    carrier, activeCornerRadiusPx, glassConfig);
            if (sink == null) return false;
        }
        sink.setNativeCornerRadiusPx(activeCornerRadiusPx);
        return true;
    }

    private void applyCarrierGeometry(View source) {
        ViewGroup host = hostRef.get();
        if (host == null || carrier.getParent() != host) return;
        int width = Math.max(1, source.getWidth());
        int height = Math.max(1, source.getHeight());
        ViewGroup.LayoutParams lp = carrier.getLayoutParams();
        if (lp != null && (lp.width != width || lp.height != height)) {
            lp.width = width;
            lp.height = height;
            carrier.setLayoutParams(lp);
        }

        int[] sourceScreen = new int[2];
        int[] hostScreen = new int[2];
        source.getLocationOnScreen(sourceScreen);
        host.getLocationOnScreen(hostScreen);
        carrier.setX(sourceScreen[0] - hostScreen[0]);
        carrier.setY(sourceScreen[1] - hostScreen[1]);
        carrier.setPivotX(source.getPivotX());
        carrier.setPivotY(source.getPivotY());
        carrier.setScaleX(source.getScaleX());
        carrier.setScaleY(source.getScaleY());
        carrier.setRotation(source.getRotation());
        carrier.setAlpha(1f);
        carrier.setVisibility(View.VISIBLE);
        sink.setNativeCornerRadiusPx(activeCornerRadiusPx);
    }

    private LauncherGlassDragState.Bounds readRootBounds(View source) {
        View root = rootRef.get();
        if (root == null || source == null || !source.isAttachedToWindow()
                || source.getWidth() <= 0 || source.getHeight() <= 0) return null;
        int[] sourceScreen = new int[2];
        int[] rootScreen = new int[2];
        source.getLocationOnScreen(sourceScreen);
        root.getLocationOnScreen(rootScreen);
        float left = sourceScreen[0] - rootScreen[0];
        float top = sourceScreen[1] - rootScreen[1];
        return new LauncherGlassDragState.Bounds(
                left, top, left + source.getWidth(), top + source.getHeight());
    }

    private static View findDragContainerAncestor(View source) {
        Object cursor = source;
        while (cursor instanceof View) {
            ViewParent parent = ((View) cursor).getParent();
            if (parent == null) return null;
            if (parent instanceof View
                    && parent.getClass().getName().contains("DragContainer")) {
                return (View) parent;
            }
            cursor = parent;
        }
        return null;
    }
}
