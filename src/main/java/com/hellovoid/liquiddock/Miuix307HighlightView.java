package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Lightweight highlight-only overlay for the opt-in HyperOS 3.0.307+ demo pipeline. */
final class Miuix307HighlightView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private float radius;
    private float highlightAlpha = 1f;
    private float highlightWidth = 1f;

    Miuix307HighlightView(Context context) {
        super(context);
        paint.setStyle(Paint.Style.STROKE);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);
    }

    void setMaterialGeometry(float radius, float alpha, float width) {
        this.radius = Math.max(0f, radius);
        this.highlightAlpha = Math.max(0f, Math.min(2f, alpha));
        this.highlightWidth = Math.max(.25f, Math.min(4f, width));
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 1 || h <= 1 || highlightAlpha <= .001f) return;

        float density = getResources().getDisplayMetrics().density;
        float stroke = Math.max(1f, 1.25f * density * highlightWidth);
        float inset = stroke * .5f + .5f;
        bounds.set(inset, inset, w - inset, h - inset);
        if (bounds.width() <= 0f || bounds.height() <= 0f) return;

        int a0 = Math.min(255, Math.round(190f * highlightAlpha));
        int a1 = Math.min(255, Math.round(72f * highlightAlpha));
        paint.setStrokeWidth(stroke);
        paint.setShader(new LinearGradient(
                0f, 0f, w, h,
                new int[]{Color.argb(a0, 255, 255, 255),
                        Color.argb(a1, 242, 248, 255), Color.TRANSPARENT},
                new float[]{0f, .48f, 1f}, Shader.TileMode.CLAMP));
        float r = Math.max(0f, Math.min(radius - inset, Math.min(bounds.width(), bounds.height()) * .5f));
        canvas.drawRoundRect(bounds, r, r, paint);
        paint.setShader(null);
    }
}
