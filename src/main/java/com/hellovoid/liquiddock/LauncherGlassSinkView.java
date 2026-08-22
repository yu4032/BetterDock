package com.hellovoid.liquiddock;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import com.hellovoid.prismal.PrismalInteractionState;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Output-only TextureView for one folder/widget material rendered by a shared launcher session. */
final class LauncherGlassSinkView extends TextureView implements TextureView.SurfaceTextureListener {
    private static final Map<View, WeakReference<LauncherGlassSinkView>> BY_MATERIAL =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final long PRESS_IN_DURATION_MS = 90L;
    private static final long PRESS_OUT_DURATION_MS = 160L;

    private final WeakReference<View> materialRef;
    private volatile LauncherGlassSession session;
    private final LiquidDockConfig.Glass glassConfig;
    private volatile float nativeCornerRadiusPx;
    private volatile boolean disposed;
    private volatile boolean suppressedByFolderOpen;
    private volatile boolean suppressedByDrag;
    private boolean pressTarget;
    private float pressProgress;
    private float glowCenterX = 0.5f;
    private float glowCenterY = 0.5f;
    private ValueAnimator pressAnimator;
    private boolean parentRecoveryPosted;
    private Surface outputSurface;
    private LauncherGlassSession outputSession;
    private final View.OnAttachStateChangeListener materialAttachListener;

    private LauncherGlassSinkView(
            Context context, View materialHost, LauncherGlassSession session, float cornerRadiusPx,
            LiquidDockConfig.Glass glassConfig) {
        super(context);
        this.materialRef = new WeakReference<>(materialHost);
        this.session = session;
        this.glassConfig = glassConfig;
        this.nativeCornerRadiusPx = Math.max(0f, cornerRadiusPx);
        this.materialAttachListener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                scheduleParentRecovery("material-attached");
                requestLifecycleRefresh();
            }

            @Override public void onViewDetachedFromWindow(View v) {
                // The material may be moving through DragContainer while this sibling remains in
                // its previous parent. Capture the lifecycle edge and recover on the next attach.
                requestLifecycleRefresh();
            }
        };
        materialHost.addOnAttachStateChangeListener(materialAttachListener);
        setOpaque(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setSurfaceTextureListener(this);
    }

    static LauncherGlassSinkView attachToMaterial(
            View materialHost, float cornerRadiusPx, LiquidDockConfig.Glass glassConfig) {
        if (materialHost == null || !(materialHost.getParent() instanceof ViewGroup)) return null;
        ViewGroup parent = (ViewGroup) materialHost.getParent();
        if (parent.getClass().getName().endsWith(".CellLayout")) return null;

        WeakReference<LauncherGlassSinkView> reference = BY_MATERIAL.get(materialHost);
        LauncherGlassSinkView existing = reference != null ? reference.get() : null;
        if (existing != null && !existing.disposed) {
            if (existing.getParent() != parent) {
                existing.scheduleParentRecovery("attach-existing");
            }
            existing.syncFromMaterial();
            return existing;
        }

        LauncherGlassSession shared = LauncherGlassSessionRegistry.acquire(materialHost, glassConfig);
        if (shared == null) return null;
        LauncherGlassSinkView sink = new LauncherGlassSinkView(
                materialHost.getContext(), materialHost, shared, cornerRadiusPx, glassConfig);
        int index = Math.max(0, parent.indexOfChild(materialHost));
        parent.addView(sink, index, new ViewGroup.LayoutParams(
                Math.max(1, materialHost.getWidth()), Math.max(1, materialHost.getHeight())));
        BY_MATERIAL.put(materialHost, new WeakReference<>(sink));
        sink.syncFromMaterial();
        shared.registerSink(sink);
        return sink;
    }

    View materialHost() {
        return materialRef.get();
    }

    void requestLifecycleRefresh() {
        LauncherGlassSession live = ensureLiveSession();
        if (!disposed && live != null) live.requestLifecycleRefresh();
    }

    void setSuppressedByFolderOpen(boolean suppressed) {
        if (disposed) return;
        if (suppressed) resetPressInteraction(false);
        if (suppressedByFolderOpen == suppressed) return;
        suppressedByFolderOpen = suppressed;
        syncFromMaterial();
        requestLifecycleRefresh();
    }

    void setSuppressedByDrag(boolean suppressed) {
        if (disposed || suppressedByDrag == suppressed) return;
        suppressedByDrag = suppressed;
        if (suppressed) resetPressInteraction(false);
        syncFromMaterial();
        requestLifecycleRefresh();
    }

    void setNativeCornerRadiusPx(float cornerRadiusPx) {
        if (disposed || !Float.isFinite(cornerRadiusPx)) return;
        float next = Math.max(0f, cornerRadiusPx);
        if (Math.abs(nativeCornerRadiusPx - next) < 0.01f) return;
        nativeCornerRadiusPx = next;
        requestLifecycleRefresh();
    }

    void setPressInteraction(boolean pressed, float normalizedX, float normalizedY) {
        if (disposed) return;
        float nextX = clamp01(normalizedX);
        float nextY = clamp01(normalizedY);
        boolean centerChanged = glowCenterX != nextX || glowCenterY != nextY;
        glowCenterX = nextX;
        glowCenterY = nextY;
        if (pressTarget != pressed) {
            pressTarget = pressed;
            animatePressTo(pressed ? 1f : 0f);
        } else if (centerChanged) {
            publishInteraction();
        }
    }

    void resetPressInteraction(boolean animated) {
        if (disposed) return;
        pressTarget = false;
        if (animated && pressProgress > 0f) {
            animatePressTo(0f);
            return;
        }
        if (pressAnimator != null) {
            pressAnimator.cancel();
            pressAnimator = null;
        }
        pressProgress = 0f;
        glowCenterX = 0.5f;
        glowCenterY = 0.5f;
        publishInteraction();
    }

    private void animatePressTo(float target) {
        if (pressAnimator != null) pressAnimator.cancel();
        float start = pressProgress;
        if (Math.abs(start - target) < 0.001f) {
            pressProgress = target;
            publishInteraction();
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(start, target);
        pressAnimator = animator;
        animator.setDuration(target > start ? PRESS_IN_DURATION_MS : PRESS_OUT_DURATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(valueAnimator -> {
            if (pressAnimator != valueAnimator || disposed) return;
            pressProgress = (Float) valueAnimator.getAnimatedValue();
            publishInteraction();
        });
        animator.start();
    }

    private void publishInteraction() {
        LauncherGlassSession live = ensureLiveSession();
        if (disposed || live == null) return;
        live.updateInteraction(this,
                new PrismalInteractionState(pressProgress, glowCenterX, glowCenterY));
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0.5f;
        return Math.max(0f, Math.min(1f, value));
    }

    boolean syncFromMaterial() {
        View material = materialRef.get();
        if (disposed || material == null) return false;
        Object materialParent = material.getParent();
        Object sinkParent = getParent();
        if (!(materialParent instanceof ViewGroup)) return false;
        if (materialParent != sinkParent) {
            if (suppressedByDrag) {
                if (getVisibility() != View.GONE) setVisibility(View.GONE);
                return true;
            }
            scheduleParentRecovery("parent-mismatch");
            return true;
        }
        boolean changed = false;
        int width = Math.max(1, material.getWidth());
        int height = Math.max(1, material.getHeight());
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp != null && (lp.width != width || lp.height != height)) {
            lp.width = width;
            lp.height = height;
            setLayoutParams(lp);
            changed = true;
        }
        changed |= setFloatIfChanged(this::getX, this::setX, material.getX());
        changed |= setFloatIfChanged(this::getY, this::setY, material.getY());
        if (getPivotX() != material.getPivotX()) { setPivotX(material.getPivotX()); changed = true; }
        if (getPivotY() != material.getPivotY()) { setPivotY(material.getPivotY()); changed = true; }
        if (getScaleX() != material.getScaleX()) { setScaleX(material.getScaleX()); changed = true; }
        if (getScaleY() != material.getScaleY()) { setScaleY(material.getScaleY()); changed = true; }
        if (getRotation() != material.getRotation()) { setRotation(material.getRotation()); changed = true; }
        if (getAlpha() != material.getAlpha()) { setAlpha(material.getAlpha()); changed = true; }
        int visibility = suppressedByFolderOpen || suppressedByDrag
                ? View.GONE : material.getVisibility();
        if (getVisibility() != visibility) { setVisibility(visibility); changed = true; }
        return changed;
    }

    LauncherGlassGeometry.Snapshot captureGeometry(View root) {
        if (disposed || root == null || getVisibility() != View.VISIBLE || getAlpha() <= 0f
                || getWidth() <= 0 || getHeight() <= 0) return null;
        Rect sinkRect = new Rect();
        Rect rootRect = new Rect();
        if (!getGlobalVisibleRect(sinkRect) || !root.getGlobalVisibleRect(rootRect)) return null;
        float left = sinkRect.left - rootRect.left;
        float top = sinkRect.top - rootRect.top;
        float right = sinkRect.right - rootRect.left;
        float bottom = sinkRect.bottom - rootRect.top;
        float scale = Math.min(
                sinkRect.width() / (float) Math.max(1, getWidth()),
                sinkRect.height() / (float) Math.max(1, getHeight()));
        return LauncherGlassGeometry.resolve(
                root.getWidth(), root.getHeight(), left, top, right, bottom,
                nativeCornerRadiusPx * Math.max(0.01f, scale));
    }

    void dispose() {
        if (disposed) return;
        resetPressInteraction(false);
        disposed = true;
        View material = materialRef.get();
        if (material != null) {
            material.removeOnAttachStateChangeListener(materialAttachListener);
            WeakReference<LauncherGlassSinkView> ref = BY_MATERIAL.get(material);
            if (ref != null && ref.get() == this) BY_MATERIAL.remove(material);
        }
        LauncherGlassSession live = session;
        if (live != null) live.unregisterSink(this);
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
    }

    private void scheduleParentRecovery(String reason) {
        if (disposed || parentRecoveryPosted) return;
        View material = materialRef.get();
        if (material == null || !material.isAttachedToWindow()) return;
        parentRecoveryPosted = true;
        material.postOnAnimation(() -> {
            parentRecoveryPosted = false;
            recoverParentNow(reason);
        });
    }

    private void recoverParentNow(String reason) {
        if (disposed) return;
        View material = materialRef.get();
        if (material == null || !(material.getParent() instanceof ViewGroup)) return;
        ViewGroup target = (ViewGroup) material.getParent();
        if (target.getClass().getName().endsWith(".CellLayout")) return;

        Object current = getParent();
        if (current != target) {
            if (current instanceof ViewGroup) ((ViewGroup) current).removeView(this);
            int index = Math.max(0, target.indexOfChild(material));
            target.addView(this, index, new ViewGroup.LayoutParams(
                    Math.max(1, material.getWidth()), Math.max(1, material.getHeight())));
            MainHook.log("[DC][LauncherGlass] sink parent recovered reason=" + reason
                    + " material=" + material.getClass().getSimpleName()
                    + " parent=" + target.getClass().getSimpleName());
        }
        syncFromMaterial();
        LauncherGlassSession live = ensureLiveSession();
        if (live != null) {
            live.registerSink(this);
            live.requestLifecycleRefresh();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!disposed) {
            LauncherGlassSession live = ensureLiveSession();
            if (live != null) live.registerSink(this);
            syncFromMaterial();
            scheduleParentRecovery("sink-attached");
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        resetPressInteraction(false);
        LauncherGlassSession live = session;
        if (live != null) live.unregisterSink(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        if (disposed || surfaceTexture == null) return;
        Surface surface = new Surface(surfaceTexture);
        Surface stale = outputSurface;
        LauncherGlassSession staleSession = outputSession;
        outputSurface = surface;
        outputSession = null;
        LauncherGlassSession live = ensureLiveSession();
        if (stale != null) {
            if (staleSession != null) staleSession.detachOutput(this, stale);
            else stale.release();
        }
        if (live != null) {
            outputSession = live;
            live.attachOutput(this, surface, Math.max(1, width), Math.max(1, height));
        } else {
            surface.release();
            outputSurface = null;
            scheduleParentRecovery("surface-await-session");
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        if (!disposed && surface != null) {
            LauncherGlassSession live = ensureLiveSession();
            if (live != null) live.resizeOutput(this, Math.max(1, width), Math.max(1, height));
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Surface current = outputSurface;
        LauncherGlassSession owner = outputSession;
        outputSurface = null;
        outputSession = null;
        if (owner != null) owner.detachOutput(this, current);
        else if (current != null) current.release();
        return true;
    }

    @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}

    private LauncherGlassSession ensureLiveSession() {
        if (disposed) return null;
        View material = materialRef.get();
        LauncherGlassSession current = session;
        View stableRoot = LauncherGlassSessionRegistry.resolveStableRoot(material);
        if (stableRoot == null) return null;
        if (current != null && !current.isShutdown() && current.ownsRoot(stableRoot)) return current;
        if (current != null) current.unregisterSink(this);
        LauncherGlassSession replacement = LauncherGlassSessionRegistry.acquire(material, glassConfig);
        session = replacement;
        if (replacement != null) {
            if (outputSurface != null && outputSession == current) {
                Surface stale = outputSurface;
                outputSurface = null;
                outputSession = null;
                if (current != null) current.detachOutput(this, stale);
                else stale.release();
                SurfaceTexture texture = getSurfaceTexture();
                if (texture != null && isAvailable()) {
                    Surface rebound = new Surface(texture);
                    outputSurface = rebound;
                    outputSession = replacement;
                    replacement.attachOutput(this, rebound,
                            Math.max(1, getWidth()), Math.max(1, getHeight()));
                }
            }
            MainHook.log("[DC][LauncherGlass] sink rebound material="
                    + material.getClass().getSimpleName() + " " + replacement.debugLabel());
        }
        return replacement;
    }

    private interface FloatGetter { float get(); }
    private interface FloatSetter { void set(float value); }

    private static boolean setFloatIfChanged(FloatGetter getter, FloatSetter setter, float value) {
        if (Math.abs(getter.get() - value) < 0.01f) return false;
        setter.set(value);
        return true;
    }
}
