package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.widget.FrameLayout;

/** Lightweight clip/geometry host for the zero-copy Prismal TextureView. */
final class DockLiquidGlassHostView extends FrameLayout {
    private final Path clipPath = new Path();
    private float radius;
    private boolean squircle;
    private float squircleCp = .58f;
    private boolean shapeDirty = true;

    DockLiquidGlassHostView(Context context) {
        super(context);
        setWillNotDraw(true);
        setClipChildren(false);
        setClipToPadding(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setGeometry(float radius, boolean squircle, float cp) {
        float nextRadius = Math.max(0f, radius);
        float nextCp = Math.max(.05f, Math.min(.95f, cp));
        boolean changed = this.radius != nextRadius || this.squircle != squircle
                || this.squircleCp != nextCp;
        this.radius = nextRadius;
        this.squircle = squircle;
        this.squircleCp = nextCp;
        if (changed) {
            shapeDirty = true;
            DockStrokeRenderer.updateRadius(this, this.radius);
            invalidate();
        }
    }

    void setRadius(float radius) {
        setGeometry(radius, squircle, squircleCp);
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw || h != oldh) shapeDirty = true;
    }

    private void ensureClipPath() {
        if (!shapeDirty) return;
        clipPath.rewind();
        if (getWidth() > 1 && getHeight() > 1) {
            DockShapePath.build(clipPath, getWidth(), getHeight(), radius, squircle, squircleCp);
        }
        shapeDirty = false;
    }

    @Override protected void onDetachedFromWindow() {
        try {
            MiuixGlassHook.onHostDetached(this);
        } finally {
            super.onDetachedFromWindow();
        }
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        ensureClipPath();
        if (clipPath.isEmpty()) return;
        int save = canvas.save();
        canvas.clipPath(clipPath);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }
}
