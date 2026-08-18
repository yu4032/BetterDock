package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;

/**
 * Dedicated MiuiX 307 backdrop RenderNode.
 *
 * SurfaceFlinger samples the content behind this View and performs the realtime blur in the GPU
 * compositor. LiquidDock never receives that backdrop as a Bitmap/texture, so this View has no
 * capture lifecycle. The parent host draws sharp Prismal optics after this child.
 */
final class Miuix307ZeroCopyBackdropView extends View {
    private static final String TAG = "[DC][ZC]";
    private static final int MAX_ATTACH_RETRY_FRAMES = 8;

    private int blurRadiusPx;
    private float glassRadiusPx;
    private final boolean passWindowBlurEnabled;
    private boolean blurActive;
    private boolean externalCompositorBlurActive;
    private boolean activationExhausted;
    private int retryFrames;
    private long activationGeneration;

    Miuix307ZeroCopyBackdropView(
            Context context, int blurRadiusPx, boolean passWindowBlurEnabled) {
        super(context);
        this.blurRadiusPx = sanitizeRadius(blurRadiusPx);
        this.passWindowBlurEnabled = passWindowBlurEnabled;
        setBackgroundColor(Color.TRANSPARENT);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                int width = view.getWidth();
                int height = view.getHeight();
                if (width <= 0 || height <= 0) {
                    outline.setEmpty();
                    return;
                }
                float maxRadius = Math.min(width, height) * 0.5f;
                float radius = Math.max(0f, Math.min(glassRadiusPx, maxRadius));
                outline.setRoundRect(0, 0, width, height, radius);
            }
        });
        // Compositor blur lives on this RenderNode. Parent Canvas clipping cannot constrain the
        // compositor region, so the child itself must own the same rounded outline.
        setClipToOutline(true);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setBlurRadius(int blurRadiusPx) {
        int next = sanitizeRadius(blurRadiusPx);
        if (this.blurRadiusPx == next && isBlurActive()) return;
        this.blurRadiusPx = next;
        if (!passWindowBlurEnabled || !isAttachedToWindow()) return;

        if (blurActive && MiBlurBridge.setPassWindowBlurRadius(this, next)) {
            activationExhausted = false;
            invalidate();
            return;
        }
        blurActive = false;
        activationExhausted = false;
        retryFrames = 0;
        long generation = ++activationGeneration;
        tryActivate(generation);
    }

    void setExternalCompositorBlurActive(boolean active) {
        externalCompositorBlurActive = active;
        if (active) {
            activationExhausted = false;
            retryFrames = 0;
        }
    }

    void setGlassRadius(float radiusPx) {
        float next = Math.max(0f, radiusPx);
        if (Float.compare(glassRadiusPx, next) == 0) return;
        glassRadiusPx = next;
        invalidateOutline();
        invalidate();
    }

    boolean isBlurActive() {
        return blurActive || externalCompositorBlurActive;
    }

    boolean isActivationExhausted() {
        return activationExhausted;
    }

    int blurRadiusPx() {
        return blurRadiusPx;
    }

    void clearBlur() {
        ++activationGeneration;
        retryFrames = 0;
        blurActive = false;
        externalCompositorBlurActive = false;
        activationExhausted = false;
        if (passWindowBlurEnabled) {
            MiBlurBridge.clearPassWindowBlur(this);
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        retryFrames = 0;
        activationExhausted = false;
        invalidateOutline();
        if (!passWindowBlurEnabled) return;
        long generation = ++activationGeneration;
        tryActivate(generation);
    }

    @Override protected void onDetachedFromWindow() {
        clearBlur();
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        invalidateOutline();
        if (!passWindowBlurEnabled || w <= 0 || h <= 0 || !isAttachedToWindow() || blurActive) {
            return;
        }
        retryFrames = 0;
        activationExhausted = false;
        long generation = ++activationGeneration;
        tryActivate(generation);
    }

    private void tryActivate(long generation) {
        if (!passWindowBlurEnabled
                || generation != activationGeneration || !isAttachedToWindow()) return;
        if (!MiBlurBridge.isPassWindowBlurAvailable()) {
            activationExhausted = true;
            return;
        }

        if (getWidth() > 0 && getHeight() > 0
                && MiBlurBridge.applyPassWindowBlur(this, blurRadiusPx)) {
            if (!blurActive) {
                MainHook.log(TAG + " backdrop pass-window blur active radius=" + blurRadiusPx
                        + " glassRadius=" + glassRadiusPx
                        + " size=" + getWidth() + "x" + getHeight());
            }
            blurActive = true;
            activationExhausted = false;
            retryFrames = 0;
            return;
        }

        blurActive = false;
        if (retryFrames >= MAX_ATTACH_RETRY_FRAMES) {
            activationExhausted = true;
            MainHook.log(TAG + " backdrop activation exhausted frames=" + retryFrames
                    + " size=" + getWidth() + "x" + getHeight());
            return;
        }
        retryFrames++;
        postOnAnimation(() -> tryActivate(generation));
    }

    private static int sanitizeRadius(int radiusPx) {
        return Math.max(0, Math.min(400, radiusPx));
    }
}
