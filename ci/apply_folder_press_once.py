from pathlib import Path


def replace_once(path: str, old: str, new: str, label: str) -> None:
    p = Path(path)
    text = p.read_text()
    if new in text:
        print(f"already applied: {label}")
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    p.write_text(text.replace(old, new, 1))
    print(f"applied: {label}")


# Prismal renderer: preserve all legacy entry points and add a per-draw interaction override.
replace_once(
    "prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java",
    '''    /** Append one glass node with a renderer-scoped highlight selection. */
    public void drawGlass(PrismalGeometry geometry, PrismalParams params, PrismalHighlightProfile highlightProfile) {
        if (geometry == null) throw new IllegalArgumentException("geometry == null");
        if (!glassFrameBegun) {
            throw new IllegalStateException("beginGlassFrame must be called before drawGlass");
        }
        if (geometry.framebufferWidth != width || geometry.framebufferHeight != height) {
            throw new IllegalArgumentException("geometry framebuffer does not match prepared backdrop");
        }
        if (params == null) params = PrismalParams.builder().build();
        if (highlightProfile == null) highlightProfile = PrismalHighlightProfile.ALL_ENABLED;
        renderGlassNode(geometry, params, highlightProfile, !legacySingleDraw || glassDrawCount > 0);
        glassDrawCount++;
    }
''',
    '''    /** Append one glass node with a renderer-scoped highlight selection. */
    public void drawGlass(PrismalGeometry geometry, PrismalParams params, PrismalHighlightProfile highlightProfile) {
        drawGlass(geometry, params, highlightProfile, null);
    }

    /** Append one glass node with an optional per-node touch interaction override. */
    public void drawGlass(PrismalGeometry geometry, PrismalParams params,
                          PrismalHighlightProfile highlightProfile,
                          PrismalInteractionState interactionState) {
        if (geometry == null) throw new IllegalArgumentException("geometry == null");
        if (!glassFrameBegun) {
            throw new IllegalStateException("beginGlassFrame must be called before drawGlass");
        }
        if (geometry.framebufferWidth != width || geometry.framebufferHeight != height) {
            throw new IllegalArgumentException("geometry framebuffer does not match prepared backdrop");
        }
        if (params == null) params = PrismalParams.builder().build();
        if (highlightProfile == null) highlightProfile = PrismalHighlightProfile.ALL_ENABLED;
        renderGlassNode(geometry, params, highlightProfile, interactionState,
                !legacySingleDraw || glassDrawCount > 0);
        glassDrawCount++;
    }
''',
    "renderer interaction overload",
)
replace_once(
    "prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java",
    '''    private void renderGlassNode(PrismalGeometry g, PrismalParams p,
                                 PrismalHighlightProfile highlights, boolean composite) {
''',
    '''    private void renderGlassNode(PrismalGeometry g, PrismalParams p,
                                 PrismalHighlightProfile highlights,
                                 PrismalInteractionState interactionState,
                                 boolean composite) {
''',
    "renderer node interaction parameter",
)
replace_once(
    "prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java",
    '''        uniform1f("u_pressProgress", p.pressProgress);
        uniform1f("u_backdropPinch", p.backdropPinch);
        uniform2f("u_glowCenter", p.glowCenterX, p.glowCenterY);
        uniform1f("u_glowStrength", p.glowStrength);
''',
    '''        float pressProgress = interactionState != null ? interactionState.pressProgress : p.pressProgress;
        float glowCenterX = interactionState != null ? interactionState.glowCenterX : p.glowCenterX;
        float glowCenterY = interactionState != null ? interactionState.glowCenterY : p.glowCenterY;
        uniform1f("u_pressProgress", pressProgress);
        uniform1f("u_backdropPinch", p.backdropPinch);
        uniform2f("u_glowCenter", glowCenterX, glowCenterY);
        uniform1f("u_glowStrength", p.glowStrength);
''',
    "renderer press uniforms",
)

# Launcher session: interaction belongs to NodeState, never to the shared session.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java",
    '''import com.hellovoid.prismal.PrismalHighlightProfile;
import com.hellovoid.prismal.PrismalParams;
''',
    '''import com.hellovoid.prismal.PrismalHighlightProfile;
import com.hellovoid.prismal.PrismalInteractionState;
import com.hellovoid.prismal.PrismalParams;
''',
    "session interaction import",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java",
    '''        final WeakReference<LauncherGlassSinkView> sinkRef;
        volatile LauncherGlassGeometry.Snapshot geometry;

        NodeState(LauncherGlassSinkView sink) {
''',
    '''        final WeakReference<LauncherGlassSinkView> sinkRef;
        volatile LauncherGlassGeometry.Snapshot geometry;
        volatile PrismalInteractionState interaction = PrismalInteractionState.IDLE;

        NodeState(LauncherGlassSinkView sink) {
''',
    "session per-node interaction",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java",
    '''    void unregisterSink(LauncherGlassSinkView sink) {
        if (sink == null) return;
        synchronized (nodes) { nodes.remove(sink); }
        requestLifecycleRefresh();
    }

    void requestLifecycleRefresh() {
''',
    '''    void unregisterSink(LauncherGlassSinkView sink) {
        if (sink == null) return;
        synchronized (nodes) { nodes.remove(sink); }
        requestLifecycleRefresh();
    }

    void updateInteraction(LauncherGlassSinkView sink, PrismalInteractionState interaction) {
        if (sink == null || shuttingDown) return;
        synchronized (nodes) {
            NodeState node = nodes.get(sink);
            if (node == null) return;
            node.interaction = interaction != null ? interaction : PrismalInteractionState.IDLE;
        }
        // Interaction redraws reuse the last consumed wallpaper texture. Never request producer refresh.
        requestFrame(false);
    }

    void requestLifecycleRefresh() {
''',
    "session interaction update",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java",
    '''            prismalRenderer.drawGlass(prismalGeometry, params, launcherHighlightProfile);
''',
    '''            prismalRenderer.drawGlass(
                    prismalGeometry, params, launcherHighlightProfile, node.interaction);
''',
    "session per-node draw",
)

# Sink: animate one node's press state and publish only to its owning session node.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/LauncherGlassSinkView.java",
    '''package com.hellovoid.liquiddock;

import android.content.Context;
''',
    '''package com.hellovoid.liquiddock;

import android.animation.ValueAnimator;
import android.content.Context;
''',
    "sink animator import",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/LauncherGlassSinkView.java",
    '''import android.view.ViewGroup;

import java.lang.ref.WeakReference;
''',
    '''import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import com.hellovoid.prismal.PrismalInteractionState;

import java.lang.ref.WeakReference;
''',
    "sink interaction imports",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/LauncherGlassSinkView.java",
    '''    private static final Map<View, WeakReference<LauncherGlassSinkView>> BY_MATERIAL =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final WeakReference<View> materialRef;
''',
    '''    private static final Map<View, WeakReference<LauncherGlassSinkView>> BY_MATERIAL =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final long PRESS_IN_DURATION_MS = 90L;
    private static final long PRESS_OUT_DURATION_MS = 160L;

    private final WeakReference<View> materialRef;
''',
    "sink press durations",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/LauncherGlassSinkView.java",
    '''    private volatile boolean disposed;
    private volatile boolean suppressedByFolderOpen;
    private boolean parentRecoveryPosted;
''',
    '''    private volatile boolean disposed;
    private volatile boolean suppressedByFolderOpen;
    private boolean pressTarget;
    private float pressProgress;
    private float glowCenterX = 0.5f;
    private float glowCenterY = 0.5f;
    private ValueAnimator pressAnimator;
    private boolean parentRecoveryPosted;
''',
    "sink press fields",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/LauncherGlassSinkView.java",
    '''    void setSuppressedByFolderOpen(boolean suppressed) {
        if (disposed || suppressedByFolderOpen == suppressed) return;
        suppressedByFolderOpen = suppressed;
        syncFromMaterial();
        requestLifecycleRefresh();
    }

    boolean syncFromMaterial() {
''',
    '''    void setSuppressedByFolderOpen(boolean suppressed) {
        if (disposed) return;
        if (suppressed) resetPressInteraction(false);
        if (suppressedByFolderOpen == suppressed) return;
        suppressedByFolderOpen = suppressed;
        syncFromMaterial();
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
''',
    "sink press behavior",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/LauncherGlassSinkView.java",
    '''    void dispose() {
        if (disposed) return;
        disposed = true;
''',
    '''    void dispose() {
        if (disposed) return;
        resetPressInteraction(false);
        disposed = true;
''',
    "sink dispose reset",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/LauncherGlassSinkView.java",
    '''    @Override
    protected void onDetachedFromWindow() {
        LauncherGlassSession live = session;
        if (live != null) live.unregisterSink(this);
        super.onDetachedFromWindow();
    }
''',
    '''    @Override
    protected void onDetachedFromWindow() {
        resetPressInteraction(false);
        LauncherGlassSession live = session;
        if (live != null) live.unregisterSink(this);
        super.onDetachedFromWindow();
    }
''',
    "sink detach reset",
)

# FolderIcon bridge: observe the exact system method after dispatch; never consume or replace touch handling.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java",
    '''import android.view.View;
import android.view.ViewGroup;
''',
    '''import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
''',
    "folder touch import",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java",
    '''    private static void installFolderOpenCloseHooks(ClassLoader classLoader) throws Exception {
        Class<?> folderIcon = Class.forName(FOLDER_ICON, false, classLoader);
        HookUtil.hook(HookUtil.findMethodExact(folderIcon, "onOpen", new Class<?>[0]), chain -> {
''',
    '''    private static void installFolderOpenCloseHooks(ClassLoader classLoader) throws Exception {
        Class<?> folderIcon = Class.forName(FOLDER_ICON, false, classLoader);
        // The inspected HyperOS build declares this override on FolderIcon itself. Hook only that
        // declaration so LiquidDock can never intercept View.dispatchTouchEvent process-wide.
        Method dispatchTouchEvent = folderIcon.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class);
        dispatchTouchEvent.setAccessible(true);
        HookUtil.hook(dispatchTouchEvent, chain -> {
            Object[] args = chain.getArgs().toArray(new Object[0]);
            Object result = chain.proceed(args);
            Object owner = chain.getThisObject();
            if (owner instanceof ViewGroup && args.length > 0 && args[0] instanceof MotionEvent) {
                updateFolderPressAfterDispatch((ViewGroup) owner, (MotionEvent) args[0]);
            }
            return result;
        });

        HookUtil.hook(HookUtil.findMethodExact(folderIcon, "onOpen", new Class<?>[0]), chain -> {
''',
    "folder dispatch hook",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java",
    '''    private static void setOwnerSuppressed(ViewGroup owner, boolean suppressed) {
''',
    '''    private static void updateFolderPressAfterDispatch(ViewGroup owner, MotionEvent event) {
        if (owner == null || event == null) return;
        LauncherGlassSinkView sink = resolveOwnerSink(owner);
        if (sink == null) return;
        try {
            Object value = HookUtil.getField(owner, "mIconImageView");
            if (!(value instanceof View)) return;
            View material = (View) value;
            int width = material.getWidth();
            int height = material.getHeight();
            if (width <= 0 || height <= 0) return;
            int[] location = new int[2];
            material.getLocationOnScreen(location);
            float x = (event.getRawX() - location[0]) / width;
            // Android local Y grows downward; Prismal glow coordinates grow upward.
            float y = 1f - (event.getRawY() - location[1]) / height;
            sink.setPressInteraction(owner.isPressed(), x, y);
        } catch (Throwable error) {
            MainHook.log(TAG + " press bridge failed: " + error);
        }
    }

    private static void setOwnerSuppressed(ViewGroup owner, boolean suppressed) {
''',
    "folder press mapping",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java",
    '''        openedFolderOwner = new WeakReference<>(owner);
        LauncherGlassSinkView sink = resolveOwnerSink(owner);
        openedFolderSink = new WeakReference<>(sink);
        if (sink != null) sink.setSuppressedByFolderOpen(true);
''',
    '''        openedFolderOwner = new WeakReference<>(owner);
        LauncherGlassSinkView sink = resolveOwnerSink(owner);
        openedFolderSink = new WeakReference<>(sink);
        if (sink != null) {
            sink.resetPressInteraction(false);
            sink.setSuppressedByFolderOpen(true);
        }
''',
    "folder open press reset",
)
