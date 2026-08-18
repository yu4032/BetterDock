package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Lightweight tint/brightness layer drawn above the zero-copy backdrop and below sharp optics. */
final class Miuix307ZeroCopyToneView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int tintAlpha;
    private int tintR;
    private int tintG;
    private int tintB;
    private float brightness = 1f;

    Miuix307ZeroCopyToneView(Context context, LiquidDockConfig.Glass glassConfig) {
        super(context);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setTone(glassConfig);
    }

    void setTone(LiquidDockConfig.Glass glassConfig) {
        if (glassConfig == null) return;
        tintAlpha = clamp255(glassConfig.tintAlpha);
        tintR = clamp255(glassConfig.tintR);
        tintG = clamp255(glassConfig.tintG);
        tintB = clamp255(glassConfig.tintB);
        brightness = Math.max(0f, Math.min(2f, glassConfig.brightness));
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        if (brightness < 0.999f) {
            int alpha = clamp255(Math.round((1f - brightness) * 255f));
            paint.setBlendMode(BlendMode.SRC_OVER);
            paint.setColor(Color.argb(alpha, 0, 0, 0));
            canvas.drawRect(0f, 0f, w, h, paint);
        } else if (brightness > 1.001f) {
            int alpha = clamp255(Math.round(Math.min(1f, brightness - 1f) * 255f));
            paint.setBlendMode(BlendMode.SCREEN);
            paint.setColor(Color.argb(alpha, 255, 255, 255));
            canvas.drawRect(0f, 0f, w, h, paint);
        }

        if (tintAlpha > 0) {
            paint.setBlendMode(BlendMode.SRC_OVER);
            paint.setColor(Color.argb(tintAlpha, tintR, tintG, tintB));
            canvas.drawRect(0f, 0f, w, h, paint);
        }
        paint.setBlendMode(BlendMode.SRC_OVER);
    }

    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
