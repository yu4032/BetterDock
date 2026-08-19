package com.hellovoid.liquiddock;

import android.content.Context;
import android.view.SurfaceControl;
import android.view.View;

/**
 * No-capture compatibility shell retained only for old MainHook call signatures.
 *
 * The rendering implementation that previously lived here used ScreenCapture HardwareBuffers,
 * Bitmap conversion/BitmapShader sampling and a RuntimeShader blur. release/1.3.0 removes that
 * pipeline. Real liquid glass is exclusively Miuix307PassBlurTextureView (PassBlur -> OES ->
 * Prismal). Every method below is intentionally inert so an obsolete fallback branch cannot
 * resurrect CPU/readback capture.
 */
final class DockLiquidGlassView extends View {
    interface ActiveBlurBackendListener {
        void onActiveBlurBackendChanged(LiquidBlurMode mode);
    }

    DockLiquidGlassView(Context context) {
        super(context);
        setWillNotDraw(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setActiveBlurBackendListener(ActiveBlurBackendListener listener) {}
    void setGlassGeometry(float radius, boolean squircle, float squircleCp) {}
    void setSystemUiPanelExpanded(boolean expanded) {}
    void setWorkstationMode(boolean enabled) {}
    void setLauncherState(boolean lifecycleKnown, boolean resumed) {}
    void onRecentsHapticTrigger() {}
    void onWorkstationRecentsButton() {}
    void requestCapture(String reason) {}
    void beginRotationStabilize() {}
    void onWallpaperOffsetChanged(float x, float y) {}
    void onWallpaperDisplayOffsetChanged(int x, int y) {}
    void onWallpaperZoomChanged(float zoom) {}
    void setAllAppsActive(boolean active, View captureRoot) {}
    void setGestureCaptureTarget(String target) {}
    void setOverviewActive(boolean active, String reason) {}
    void setRecentsView(View recents) {}
    void setDockDragging(boolean dragging, String layerName) {}
    void setDockDragging(boolean dragging, String layerName, SurfaceControl surface) {}
    void setSystemDockDragActive(boolean active) {}
    void onDockTouchEvent() {}
    void onDockGestureMotion(int action, float rawY) {}
    boolean isTouchInDockArea(float rawX, float rawY) { return false; }
    void prearmAppBackdrop(String reason) {}

    void setFullscreenCapture(boolean enabled) {}
    void setCaptureScale(float scale) {}
    void setCapturePowerLimitFps(int fps) {}
    void setPreserveGeometrySourceVisuals(boolean preserve) {}
}
