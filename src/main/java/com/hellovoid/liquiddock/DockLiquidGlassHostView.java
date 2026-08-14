package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * Exact Dock-sized composition host.
 *
 * The glass child deliberately stays rectangular while SurfaceFlinger self-blurs its
 * RenderNode. Final round/squircle clipping happens here, after the child render pass, so
 * rounded-corner source pixels (notably the upper-left corner) are present during blur.
 */
final class DockLiquidGlassHostView extends FrameLayout {
    private final Path clipPath = new Path();
    private DockLiquidGlassView glassView;
    private DockStrokeOverlayView overlayView;
    private float radius;
    private boolean squircle;
    private float squircleCp = .58f;

    DockLiquidGlassHostView(Context context) {
        super(context);
        setWillNotDraw(true);
        setClipChildren(false);
        setClipToPadding(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setLayers(DockLiquidGlassView glass, DockStrokeOverlayView overlay) {
        removeAllViews();
        glassView = glass;
        overlayView = overlay;
        addView(glass, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addView(overlay, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    DockLiquidGlassView glassView() {
        return glassView;
    }

    void setGeometry(float radius, boolean squircle, float cp) {
        this.radius = Math.max(0f, radius);
        this.squircle = squircle;
        this.squircleCp = Math.max(.05f, Math.min(.95f, cp));
        if (glassView != null) glassView.setGlassGeometry(this.radius, squircle, this.squircleCp);
        if (overlayView != null) overlayView.setGeometry(this.radius, squircle, this.squircleCp);
        invalidate();
    }

    void setRadius(float radius) {
        setGeometry(radius, squircle, squircleCp);
    }

    void reloadOverlay(LiquidDockConfig.Dock dock, LiquidDockConfig.Glass glass) {
        squircle = dock.squircle;
        squircleCp = dock.squircleCp;
        if (overlayView != null) overlayView.reload(dock, glass, radius);
        invalidate();
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        if (getWidth() <= 1 || getHeight() <= 1) return;
        DockShapePath.build(clipPath, getWidth(), getHeight(), radius, squircle, squircleCp);
        if (clipPath.isEmpty()) return;
        int save = canvas.save();
        canvas.clipPath(clipPath);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }
}
