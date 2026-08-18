package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.Color;
import android.view.View;

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
    private boolean blurActive;
    private int retryFrames;
    private long activationGeneration;

    Miuix307ZeroCopyBackdropView(Context context, int blurRadiusPx) {
        super(context);
        this.blurRadiusPx = sanitizeRadius(blurRadiusPx);
        setBackgroundColor(Color.TRANSPARENT);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setBlurRadius(int blurRadiusPx) {
        int next = sanitizeRadius(blurRadiusPx);
        if (this.blurRadiusPx == next && blurActive) return;
        this.blurRadiusPx = next;
        if (!isAttachedToWindow()) return;

        if (blurActive && MiBlurBridge.setPassWindowBlurRadius(this, next)) {
            invalidate();
            return;
        }
        blurActive = false;
        retryFrames = 0;
        long generation = ++activationGeneration;
        tryActivate(generation);
    }

    boolean isBlurActive() {
        return blurActive;
    }

    int blurRadiusPx() {
        return blurRadiusPx;
    }

    void clearBlur() {
        ++activationGeneration;
        retryFrames = 0;
        blurActive = false;
        MiBlurBridge.clearPassWindowBlur(this);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        retryFrames = 0;
        long generation = ++activationGeneration;
        tryActivate(generation);
    }

    @Override protected void onDetachedFromWindow() {
        clearBlur();
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0 || !isAttachedToWindow() || blurActive) return;
        retryFrames = 0;
        long generation = ++activationGeneration;
        tryActivate(generation);
    }

    private void tryActivate(long generation) {
        if (generation != activationGeneration || !isAttachedToWindow()) return;
        if (!MiBlurBridge.isPassWindowBlurAvailable()) return;

        if (getWidth() > 0 && getHeight() > 0
                && MiBlurBridge.applyPassWindowBlur(this, blurRadiusPx)) {
            if (!blurActive) {
                MainHook.log(TAG + " backdrop pass-window blur active radius=" + blurRadiusPx
                        + " size=" + getWidth() + "x" + getHeight());
            }
            blurActive = true;
            retryFrames = 0;
            return;
        }

        blurActive = false;
        if (retryFrames >= MAX_ATTACH_RETRY_FRAMES) {
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
