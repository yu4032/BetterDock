package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

/** Sharp layer above the self-blurred glass body: Canvas highlight + configurable stroke. */
final class DockStrokeOverlayView extends View {
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private float radius;
    private boolean squircle;
    private float squircleCp = .58f;
    private float highlightAlpha = 1f;
    private float highlightWidth = 1f;
    private final float density;

    DockStrokeOverlayView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setGeometry(float radius, boolean squircle, float cp) {
        this.radius = Math.max(0f, radius);
        this.squircle = squircle;
        this.squircleCp = Math.max(.05f, Math.min(.95f, cp));
        DockStrokeRenderer.updateRadius(this, this.radius);
        invalidate();
    }

    void setHighlight(float alpha, float width) {
        highlightAlpha = Math.max(0f, Math.min(2f, alpha));
        highlightWidth = Math.max(0f, width);
        invalidate();
    }

    void reload(LiquidDockConfig.Dock dock, LiquidDockConfig.Glass glass, float radius) {
        setGeometry(radius, dock.squircle, dock.squircleCp);
        setHighlight(glass.highlightAlpha, glass.highlightWidth);
        DockStrokeRenderer.configure(this, dock, radius);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w <= 1 || h <= 1 || highlightAlpha <= 0f || highlightWidth <= 0f) return;
        DockShapePath.build(shape, w, h, radius, squircle, squircleCp);
        if (shape.isEmpty()) return;

        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setStrokeWidth(Math.max(1f, density * .65f * highlightWidth));
        int strong = clampAlpha(175f * highlightAlpha);
        int faint = clampAlpha(25f * highlightAlpha);
        int tail = clampAlpha(105f * highlightAlpha);
        highlightPaint.setShader(new LinearGradient(0, 0, w, h,
                new int[]{
                        Color.argb(strong, 255, 255, 255),
                        Color.argb(faint, 255, 255, 255),
                        Color.argb(tail, 255, 255, 255)},
                null, Shader.TileMode.CLAMP));
        canvas.drawPath(shape, highlightPaint);
        highlightPaint.setShader(null);
    }

    private static int clampAlpha(float alpha) {
        return Math.max(0, Math.min(255, Math.round(alpha)));
    }
}
