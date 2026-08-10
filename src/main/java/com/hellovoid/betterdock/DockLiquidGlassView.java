package com.hellovoid.betterdock;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.ViewTreeObserver;

/**
 * Event-driven liquid-glass renderer for the Launcher Dock.
 *
 * The capture source follows HyperOS Home's own wallpaper-only capture path:
 * captureDisplay + vendor captureMode(2) + the Launcher wallpaper layer selector.
 * Captures are one-shot and coalesced: scrolling/wallpaper-offset/rotation/Dock geometry
 * changes mark the source dirty, while an unchanged pre-draw does not request another frame.
 */
final class DockLiquidGlassView extends View implements ViewTreeObserver.OnPreDrawListener {
    private static final String TAG = "BetterDock";
    private static final float CAPTURE_SCALE = 0.5f;
    private static final String REFRACTION_SHADER =
        "uniform shader content;"
      + "uniform float2 size;"
      + "uniform float2 offset;"
      + "uniform float4 cornerRadii;"
      + "uniform float refractionHeight;"
      + "uniform float refractionAmount;"
      + "uniform float sCurveAmount;"
      + "uniform float depthEffect;"
      + "uniform float chromaticAberration;"
      + "uniform float blurRadius;"
      + "uniform float2 screenOffset;"
      + "uniform float2 captureScale;"
      + "float radiusAt(float2 p,float4 r){if(p.x>=0){return p.y<=0?r.y:r.z;}return p.y<=0?r.x:r.w;}"
      + "float sdRound(float2 p,float2 h,float r){float2 q=abs(p)-(h-float2(r));return length(max(q,0.0))-r+min(max(q.x,q.y),0.0);}"
      + "float2 gradRound(float2 p,float2 h,float r){float2 q=abs(p)-(h-float2(r));float2 s=sign(p);"
      + "s.x=s.x==0.0?1.0:s.x;s.y=s.y==0.0?1.0:s.y;if(q.x>=0.0||q.y>=0.0)return s*normalize(max(q,0.0001));"
      + "float gx=step(q.y,q.x);return s*float2(gx,1.0-gx);}"
      + "float circleMap(float x){x=clamp(x,0.0,1.0);return 1.0-sqrt(max(0.0,1.0-x*x));}"
      + "float surfaceHeight(float2 p){return 0.9*sin(p.x*0.004+p.y*0.001)"
      + "+0.55*cos(p.x*0.001-p.y*0.004)+0.35*sin((p.x+p.y)*0.008);}"
      + "float2 surfaceGrad(float2 p){return float2("
      + "0.9*0.004*cos(p.x*0.004+p.y*0.001)-0.55*0.001*sin(p.x*0.001-p.y*0.004)"
      + "+0.35*0.008*cos((p.x+p.y)*0.008),"
      + "0.9*0.001*cos(p.x*0.004+p.y*0.001)+0.55*0.004*sin(p.x*0.001-p.y*0.004)"
      + "+0.35*0.008*cos((p.x+p.y)*0.008))*12.0;}"
      + "half4 source(float2 p){return content.eval((p+screenOffset)*captureScale);}"
      + "half4 blurred(float2 p){return source(p);}"
      + "half4 main(float2 coord){float2 hs=size*0.5;float2 cc=(coord+offset)-hs;float r=radiusAt(cc,cornerRadii);"
      + "float sd=sdRound(cc,hs,r);float edge=clamp(-sd/max(refractionHeight,0.001),0.0,1.0);"
      + "float edgeFade=1.0-edge*edge*(3.0-2.0*edge);"
      + "float2 gs=surfaceGrad(cc);float2 g=normalize(gradRound(cc,hs,r)+depthEffect*edgeFade*normalize(cc+0.0001));"
      + "float2 shift=gs*sCurveAmount+g*circleMap(edge)*refractionAmount*0.8;"
      + "float2 rc=coord+shift;float pf=abs(cc.x*cc.y)/max(hs.x*hs.y,1.0);float di=chromaticAberration*(0.4+0.6*pf);"
      + "float2 dd=shift*di;half4 rr=blurred(rc+dd);half4 gg=blurred(rc);half4 bb=blurred(rc-dd);"
      + "return half4(rr.r,gg.g,bb.b,(rr.a+gg.a+bb.a)/3.0);}";

    private final View workspace;
    private final View geometrySource;
    private final RuntimeShader refraction;
    private final Paint glassPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int blurRadius;
    private final float refractionAmount;
    private final float sCurveAmount;
    private final float chromaticAberration;
    private final long captureIntervalNanos;
    private final int captureBleedPx;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final int[] tmpDockLocation = new int[2];
    private final int[] tmpWorkspaceLocation = new int[2];
    private final Point tmpDisplaySize = new Point();

    private Bitmap capture;
    private BitmapShader captureShader;
    private float captureSampleOffsetX;
    private float captureSampleOffsetY;
    private float captureSourceWidth = 1f;
    private float captureSourceHeight = 1f;

    private HandlerThread captureThread;
    private Handler captureHandler;
    private LiveScreenCapture liveCapture;
    private ViewTreeObserver observedTree;

    private boolean attached;
    private boolean launcherResumed;
    private boolean launcherLifecycleKnown;
    private boolean windowVisible;
    private boolean windowFocused;
    private android.view.SurfaceControl dockWindowSurface;
    private boolean capturing;
    private boolean sourceDirty;
    private boolean nullFrameLogged;
    private int drawFailLogged;
    private boolean nativeBackgroundHiddenByGlass;
    private boolean kickScheduled;
    private long captureGeneration;
    private long lastCaptureStartNanos;

    private boolean observationValid;
    private int observedRotation;
    private int observedDisplayWidth;
    private int observedDisplayHeight;
    private int observedDockX;
    private int observedDockY;
    private int observedDockWidth;
    private int observedDockHeight;
    private int observedDockTranslationX;
    private int observedDockTranslationY;
    private int observedDockScaleX;
    private int observedDockScaleY;
    private int observedWorkspaceX;
    private int observedWorkspaceY;
    private int observedWorkspaceScrollX;
    private int observedWorkspaceScrollY;
    private int observedWorkspaceTranslationX;
    private int observedWorkspaceTranslationY;
    private int observedWorkspaceScaleX;
    private int observedWorkspaceScaleY;

    private boolean wallpaperOffsetValid;
    private int wallpaperOffsetXBits;
    private int wallpaperOffsetYBits;
    private boolean wallpaperDisplayOffsetValid;
    private int wallpaperDisplayOffsetX;
    private int wallpaperDisplayOffsetY;
    private boolean wallpaperZoomValid;
    private int wallpaperZoomBits;

    private float cornerRadius;
    private boolean squircle;
    private float squircleCp;

    private final Runnable captureKick = new Runnable() {
        @Override public void run() {
            kickScheduled = false;
            if (!isCaptureAllowed()) {
                // Keep the dirty bit.  Attach/setupViews commonly runs before the Launcher
                // window acquires focus; dropping it here can lose the only initial frame.
                logCaptureGate("kick-blocked");
                return;
            }
            if (capturing || !sourceDirty) return;

            long now = System.nanoTime();
            long remaining = lastCaptureStartNanos == 0L
                    ? 0L : captureIntervalNanos - (now - lastCaptureStartNanos);
            if (remaining > 0L) {
                // One trailing, coalesced frame only. This is not a repeating capture loop.
                kickScheduled = true;
                mainHandler.postDelayed(this, Math.max(1L, (remaining + 999_999L) / 1_000_000L));
                return;
            }

            sourceDirty = false;
            startCapture();
        }
    };

    private final View.OnLayoutChangeListener geometryLayoutListener =
            (v, l, t, r, b, ol, ot, or, ob) -> {
                if (l != ol || t != ot || r != or || b != ob) {
                    observationValid = false;
                    requestStateCapture();
                }
            };

    DockLiquidGlassView(View geometrySource, View workspace, int blurRadius,
                        float refractionAmount, float sCurveAmount, float chromaticAberration,
                        int tintAlpha, boolean squircle, float squircleCp,
                        int captureFps) {
        super(geometrySource.getContext());
        this.geometrySource = geometrySource;
        this.workspace = workspace;
        this.blurRadius = Math.max(0, blurRadius);
        this.refractionAmount = refractionAmount;
        this.sCurveAmount = sCurveAmount;
        this.chromaticAberration = chromaticAberration;
        int fps = Math.max(5, Math.min(60, captureFps));
        this.captureIntervalNanos = 1_000_000_000L / fps;

        float density = getResources().getDisplayMetrics().density;
        float displacement = Math.abs(refractionAmount) * (1f + Math.abs(chromaticAberration));
        captureBleedPx = Math.max(8, Math.min(512,
                (int) Math.ceil(this.blurRadius + displacement + 8f * density)));

        this.squircle = squircle;
        this.squircleCp = squircleCp;
        refraction = new RuntimeShader(REFRACTION_SHADER);
        tintPaint.setColor(Color.argb(Math.max(0, Math.min(255, tintAlpha)), 238, 244, 255));
        setWillNotDraw(false);
    }

    void setGlassGeometry(float radius, boolean useSquircle, float cp) {
        cornerRadius = Math.max(0f, radius);
        squircle = useSquircle;
        squircleCp = cp;
        invalidate();
    }

    void setGlassRadius(float radius) {
        cornerRadius = Math.max(0f, radius);
        invalidate();
    }

    /** Called by MainHook's Launcher lifecycle hooks.  Unknown is intentionally allowed:
     * the actual View window visibility/focus is sufficient to bootstrap the first frame. */
    void setLauncherState(boolean known, boolean resumed) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setLauncherState(known, resumed));
            return;
        }
        boolean changed = launcherLifecycleKnown != known || launcherResumed != resumed;
        launcherLifecycleKnown = known;
        launcherResumed = resumed;
        if (known && !resumed) {
            // The Dock lives in its own overlay window ("Floating Dock", type 2997) that stays
            // visible over other apps.  A Launcher onPause does NOT mean the Dock is hidden, so
            // we must not hard-cancel capture here: window visibility/isShown() below is the
            // authoritative gate for the floating window.  Just re-evaluate.
            Log.i(TAG, "Liquid capture lifecycle=PAUSED; window visibility decides");
            requestStateCapture("lifecycle-paused");
            return;
        }
        if (changed) {
            Log.i(TAG, "Liquid capture lifecycle=" + (known ? "RESUMED" : "UNKNOWN")
                    + "; window gate will decide capture");
            observationValid = false;
            requestStateCapture("lifecycle");
        }
    }

    void setLauncherResumed(boolean resumed) {
        setLauncherState(true, resumed);
    }

    /** Called from WallpaperManager wallpaper-transform hooks in this process. */
    void onWallpaperOffsetChanged(float xOffset, float yOffset) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> onWallpaperOffsetChanged(xOffset, yOffset));
            return;
        }
        int xb = Float.floatToIntBits(xOffset);
        int yb = Float.floatToIntBits(yOffset);
        if (wallpaperOffsetValid && xb == wallpaperOffsetXBits && yb == wallpaperOffsetYBits) {
            return;
        }
        wallpaperOffsetValid = true;
        wallpaperOffsetXBits = xb;
        wallpaperOffsetYBits = yb;
        requestStateCapture();
    }

    void onWallpaperDisplayOffsetChanged(int x, int y) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> onWallpaperDisplayOffsetChanged(x, y));
            return;
        }
        if (wallpaperDisplayOffsetValid
                && x == wallpaperDisplayOffsetX && y == wallpaperDisplayOffsetY) return;
        wallpaperDisplayOffsetValid = true;
        wallpaperDisplayOffsetX = x;
        wallpaperDisplayOffsetY = y;
        requestStateCapture();
    }

    void onWallpaperZoomChanged(float zoom) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> onWallpaperZoomChanged(zoom));
            return;
        }
        int bits = Float.floatToIntBits(zoom);
        if (wallpaperZoomValid && bits == wallpaperZoomBits) return;
        wallpaperZoomValid = true;
        wallpaperZoomBits = bits;
        requestStateCapture();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        captureGeneration++;
        windowVisible = getWindowVisibility() == View.VISIBLE;
        windowFocused = hasWindowFocus();
        dockWindowSurface = resolveWindowSurfaceControl();
        logOwnWindowInfo();
        observationValid = false;

        captureThread = new HandlerThread("BetterDock-WallpaperCapture");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());

        observedTree = getRootView().getViewTreeObserver();
        observedTree.addOnPreDrawListener(this);
        geometrySource.addOnLayoutChangeListener(geometryLayoutListener);
        Log.i(TAG, "Liquid capture attached: visible=" + windowVisible
                + " focus=" + windowFocused + " shown=" + isShown()
                + " lifecycleKnown=" + launcherLifecycleKnown
                + " resumed=" + launcherResumed);
        requestStateCapture("attach");
        // setupViews can attach this child while ViewRoot is still transitioning to the focused
        // Home window.  Re-evaluate once on the next main-loop turn; this is one-shot, not a loop.
        mainHandler.post(() -> {
            if (!attached) return;
            windowVisible = getWindowVisibility() == View.VISIBLE;
            windowFocused = hasWindowFocus();
            requestStateCapture("attach-post");
        });
    }

    @Override protected void onDetachedFromWindow() {
        attached = false;
        cancelPendingCaptureWork();

        if (observedTree != null && observedTree.isAlive()) {
            observedTree.removeOnPreDrawListener(this);
        }
        observedTree = null;
        geometrySource.removeOnLayoutChangeListener(geometryLayoutListener);

        if (nativeBackgroundHiddenByGlass) {
            geometrySource.setAlpha(1f);
            nativeBackgroundHiddenByGlass = false;
        }

        Bitmap old = capture;
        capture = null;
        captureShader = null;
        if (old != null && !old.isRecycled()) old.recycle();

        HandlerThread thread = captureThread;
        captureThread = null;
        captureHandler = null;
        liveCapture = null;
        if (thread != null) thread.quitSafely();
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        windowVisible = visibility == View.VISIBLE;
        if (windowVisible) {
            observationValid = false;
            requestStateCapture("window-visible");
        } else {
            cancelPendingCaptureWork();
        }
    }

    @Override public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        windowFocused = hasWindowFocus;
        // The Dock's NOT_FOCUSABLE overlay window never reports focus, so a false value is the
        // steady state on HyperOS Pad and must not cancel capture.  A genuine focus gain is
        // still a useful hint that the Home surface is in front, so keep it as a capture trigger.
        if (hasWindowFocus) {
            observationValid = false;
            requestStateCapture("window-focus");
        }
    }

    @Override public boolean onPreDraw() {
        if (!isCaptureAllowed()) return true;
        if (updateObservation()) {
            requestStateCapture("observation");
        }
        return true;
    }

    /**
     * Cheap state polling only; this never captures by itself when all tracked values are static.
     * It catches workspace page motion, Dock motion/resize and display rotation/size changes.
     */
    private boolean updateObservation() {
        Display display = geometrySource.getDisplay();
        if (display == null) return false;
        display.getRealSize(tmpDisplaySize);
        geometrySource.getLocationOnScreen(tmpDockLocation);
        if (workspace != null) workspace.getLocationOnScreen(tmpWorkspaceLocation);
        else { tmpWorkspaceLocation[0] = 0; tmpWorkspaceLocation[1] = 0; }

        int rotation = display.getRotation();
        int dockW = geometrySource.getWidth();
        int dockH = geometrySource.getHeight();
        int dockTx = Float.floatToIntBits(geometrySource.getTranslationX());
        int dockTy = Float.floatToIntBits(geometrySource.getTranslationY());
        int dockSx = Float.floatToIntBits(geometrySource.getScaleX());
        int dockSy = Float.floatToIntBits(geometrySource.getScaleY());

        int wsScrollX = workspace != null ? workspace.getScrollX() : 0;
        int wsScrollY = workspace != null ? workspace.getScrollY() : 0;
        int wsTx = workspace != null ? Float.floatToIntBits(workspace.getTranslationX()) : 0;
        int wsTy = workspace != null ? Float.floatToIntBits(workspace.getTranslationY()) : 0;
        int wsSx = workspace != null ? Float.floatToIntBits(workspace.getScaleX()) : 0;
        int wsSy = workspace != null ? Float.floatToIntBits(workspace.getScaleY()) : 0;

        boolean changed = !observationValid
                || rotation != observedRotation
                || tmpDisplaySize.x != observedDisplayWidth
                || tmpDisplaySize.y != observedDisplayHeight
                || tmpDockLocation[0] != observedDockX
                || tmpDockLocation[1] != observedDockY
                || dockW != observedDockWidth
                || dockH != observedDockHeight
                || dockTx != observedDockTranslationX
                || dockTy != observedDockTranslationY
                || dockSx != observedDockScaleX
                || dockSy != observedDockScaleY
                || tmpWorkspaceLocation[0] != observedWorkspaceX
                || tmpWorkspaceLocation[1] != observedWorkspaceY
                || wsScrollX != observedWorkspaceScrollX
                || wsScrollY != observedWorkspaceScrollY
                || wsTx != observedWorkspaceTranslationX
                || wsTy != observedWorkspaceTranslationY
                || wsSx != observedWorkspaceScaleX
                || wsSy != observedWorkspaceScaleY;

        observedRotation = rotation;
        observedDisplayWidth = tmpDisplaySize.x;
        observedDisplayHeight = tmpDisplaySize.y;
        observedDockX = tmpDockLocation[0];
        observedDockY = tmpDockLocation[1];
        observedDockWidth = dockW;
        observedDockHeight = dockH;
        observedDockTranslationX = dockTx;
        observedDockTranslationY = dockTy;
        observedDockScaleX = dockSx;
        observedDockScaleY = dockSy;
        observedWorkspaceX = tmpWorkspaceLocation[0];
        observedWorkspaceY = tmpWorkspaceLocation[1];
        observedWorkspaceScrollX = wsScrollX;
        observedWorkspaceScrollY = wsScrollY;
        observedWorkspaceTranslationX = wsTx;
        observedWorkspaceTranslationY = wsTy;
        observedWorkspaceScaleX = wsSx;
        observedWorkspaceScaleY = wsSy;
        observationValid = true;
        return changed;
    }

    /** Log which window this glass view actually lives in — the Dock overlay (2997) or the
     * Launcher main window — so we know which layer to exclude from captures. */
    private void logOwnWindowInfo() {
        try {
            java.lang.reflect.Method getVri = View.class.getDeclaredMethod("getViewRootImpl");
            getVri.setAccessible(true);
            Object vri = getVri.invoke(this);
            if (vri == null) { Log.i(TAG, "own window: no ViewRootImpl"); return; }
            Class<?> vriClass = Class.forName("android.view.ViewRootImpl");
            java.lang.reflect.Field attrsField = vriClass.getDeclaredField("mWindowAttributes");
            attrsField.setAccessible(true);
            Object attrs = attrsField.get(vri);
            if (attrs instanceof android.view.WindowManager.LayoutParams) {
                android.view.WindowManager.LayoutParams lp =
                        (android.view.WindowManager.LayoutParams) attrs;
                Log.i(TAG, "own window: type=" + lp.type + " title=" + lp.getTitle());
            }
        } catch (Throwable e) {
            Log.w(TAG, "own window info failed: " + e);
        }
    }

    /** The Dock's own window layer, resolved once at attach, so full-display captures can
     * exclude it (matching the native blur-behind which blurs only layers below the Dock).
     *
     * The Dock is hosted in a dedicated overlay window ("Floating Dock", type 2997), NOT in
     * the Launcher main window.  We scan WindowManagerGlobal's roots for that window type and
     * return its SurfaceControl; excluding the wrong window (e.g. the Launcher window our view
     * tree happens to live in) would leave the Dock icons in the captured frame. */
    private android.view.SurfaceControl resolveWindowSurfaceControl() {
        try {
            Class<?> wmgClass = Class.forName("android.view.WindowManagerGlobal");
            java.lang.reflect.Method getInstance = wmgClass.getDeclaredMethod("getInstance");
            getInstance.setAccessible(true);
            Object wmg = getInstance.invoke(null);
            java.lang.reflect.Field rootsField = wmgClass.getDeclaredField("mRoots");
            rootsField.setAccessible(true);
            Object roots = rootsField.get(wmg);
            if (!(roots instanceof java.util.ArrayList)) return null;
            java.util.ArrayList<?> list = (java.util.ArrayList<?>) roots;
            for (Object root : list) {
                if (root == null) continue;
                try {
                    Class<?> vriClass = Class.forName("android.view.ViewRootImpl");
                    java.lang.reflect.Field attrsField = vriClass.getDeclaredField("mWindowAttributes");
                    attrsField.setAccessible(true);
                    Object attrs = attrsField.get(root);
                    if (!(attrs instanceof android.view.WindowManager.LayoutParams)) continue;
                    android.view.WindowManager.LayoutParams lp =
                            (android.view.WindowManager.LayoutParams) attrs;
                    // Dock overlay window type 2997 (HyperOS Floating Dock).
                    if (lp.type == 2997) {
                        java.lang.reflect.Method getSc = vriClass.getDeclaredMethod("getSurfaceControl");
                        getSc.setAccessible(true);
                        Object sc = getSc.invoke(root);
                        if (sc instanceof android.view.SurfaceControl) {
                            Log.i(TAG, "Liquid capture dock window surface resolved from root["
                                    + list.indexOf(root) + "] type=" + lp.type
                                    + " title=" + lp.getTitle() + " sc=" + sc);
                            return (android.view.SurfaceControl) sc;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            Log.w(TAG, "dock window surface: no root with type 2997 found (roots=" + list.size() + ")");
        } catch (Throwable e) {
            Log.w(TAG, "dock window surface resolve failed: " + e);
        }
        return null;
    }

    private boolean isCaptureAllowed() {
        // A confirmed onPause is authoritative ONLY while the Dock window itself is hidden.
        // The Dock is a floating overlay window (type 2997) that stays on screen over other
        // apps, so a Launcher onPause alone must not gate capture: windowVisible/isShown()
        // reflect the actual floating-window state.
        //
        // HyperOS 3.0 Pad hosts the Dock in a dedicated NOT_FOCUSABLE overlay window
        // ("Floating Dock", window type 2997).  NOT_FOCUSABLE windows never receive window
        // focus, so hasWindowFocus() is permanently false there and MUST NOT gate capture.
        return attached && windowVisible && isShown();
    }

    private void requestStateCapture() {
        requestStateCapture("state");
    }

    private void requestStateCapture(String reason) {
        // Dirty is a state fact, not a scheduling fact.  Preserve it even if the Home window is
        // not captureable yet; the next focus/visibility/resume transition will consume it.
        sourceDirty = true;
        if (!isCaptureAllowed()) {
            logCaptureGate(reason);
            return;
        }
        if (capturing || kickScheduled) return;
        kickScheduled = true;
        Log.i(TAG, "Liquid capture scheduled reason=" + reason);
        mainHandler.post(captureKick);
    }

    private long lastGateLogNanos;
    private String lastGateSummary;

    private void logCaptureGate(String reason) {
        String summary = "reason=" + reason
                + " attached=" + attached
                + " lifecycleKnown=" + launcherLifecycleKnown
                + " resumed=" + launcherResumed
                + " visible=" + windowVisible
                + " focus=" + windowFocused
                + " shown=" + isShown();
        long now = System.nanoTime();
        if (!summary.equals(lastGateSummary) || now - lastGateLogNanos > 1_000_000_000L) {
            lastGateSummary = summary;
            lastGateLogNanos = now;
            Log.i(TAG, "Liquid capture gated: " + summary);
        }
    }

    /** Remove every queued main/worker capture task; an already-running binder/native capture
     * cannot be interrupted safely, so generation invalidation makes its result disposable. */
    private void cancelPendingCaptureWork() {
        mainHandler.removeCallbacks(captureKick);
        kickScheduled = false;
        sourceDirty = false;
        lastCaptureStartNanos = 0L;
        observationValid = false;
        captureGeneration++;
        capturing = false;
        Handler worker = captureHandler;
        if (worker != null) worker.removeCallbacksAndMessages(null);
    }

    private void startCapture() {
        final Handler worker = captureHandler;
        if (worker == null || !isCaptureAllowed() || capturing) return;

        final CaptureRequest request = makeCaptureRequest();
        if (request == null) {
            Log.w(TAG, "Liquid capture request has no valid Dock/display geometry: "
                    + "dock=" + geometrySource.getWidth() + "x" + geometrySource.getHeight());
            return;
        }

        // The Dock is a floating overlay window (type 2997) that can be summoned over any app.
        // Wallpaper-only capture is wrong there: the glass must refract the app content below.
        // Default to full-display capture; keep wallpaper mode for desktop-only setups via
        // config key "liquid_capture_fullscreen" (false = wallpaper layer only).
        boolean fullscreen = true;
        try {
            ConfigReader cfg = ConfigReader.load();
            fullscreen = cfg.b("liquid_capture_fullscreen", true);
        } catch (Throwable e) {
            fullscreen = true;
        }
        final boolean useFullscreen = fullscreen;

        final long generation = captureGeneration;
        capturing = true;
        lastCaptureStartNanos = System.nanoTime();
        Log.i(TAG, (useFullscreen ? "fullscreen" : "captureMode(2)") + " attempt display=" + request.displayId
                + " strip=" + request.stripRect + " tile=" + request.tileRect
                + " scale=" + CAPTURE_SCALE);

        worker.post(() -> {
            Bitmap strip = null;
            CroppedFrame cropped = null;
            Throwable failure = null;
            try {
                LiveScreenCapture client = liveCapture;
                if (client == null) {
                    client = new LiveScreenCapture(
                            CAPTURE_SCALE, geometrySource.getContext().getClassLoader());
                    liveCapture = client;
                }
                if (useFullscreen) {
                    android.view.SurfaceControl[] excludes = null;
                    if (dockWindowSurface != null) {
                        excludes = new android.view.SurfaceControl[]{dockWindowSurface};
                    }
                    strip = client.captureScreen(request.stripRect, CAPTURE_SCALE, request.displayId,
                            excludes);
                } else {
                    strip = client.captureWallpaper(request.stripRect, CAPTURE_SCALE, request.displayId);
                }
                if (strip != null) {
                    cropped = cropWallpaperTile(strip, request.stripRect,
                            request.tileRect, request.dockRect);
                    strip = null; // cropWallpaperTile owns/recycles it.
                }
            } catch (Throwable error) {
                failure = error;
                liveCapture = null;
            } finally {
                if (strip != null && !strip.isRecycled()) strip.recycle();
            }

            final CroppedFrame frame = cropped;
            final Throwable captureFailure = failure;
            mainHandler.post(() -> {
                if (generation != captureGeneration || !isCaptureAllowed()) {
                    if (frame != null) frame.recycle();
                    Log.i(TAG, "Liquid capture result discarded: generation="
                            + (generation == captureGeneration)
                            + " allowed=" + isCaptureAllowed());
                    return;
                }

                capturing = false;
                if (captureFailure != null) {
                    Log.e(TAG, "HyperOS wallpaper-only capture failed", captureFailure);
                } else if (frame != null) {
                    nullFrameLogged = false;
                    installCapture(frame);
                } else if (!nullFrameLogged) {
                    nullFrameLogged = true;
                    Log.w(TAG, "HyperOS captureMode(2) wallpaper path returned no buffer");
                }

                // Only another state change that happened while this capture was in flight
                // can arm another frame. There is no autonomous/static capture loop.
                if (sourceDirty) requestStateCapture();
            });
        });
    }

    /**
     * Read back only the screen's lower strip that contains the Dock. The strip spans the
     * display width but starts at Dock-minus-bleed, so on a bottom Dock it is only a small
     * fraction of the display. The returned strip is cropped again to Dock+bleed in memory.
     */
    private CaptureRequest makeCaptureRequest() {
        Display display = geometrySource.getDisplay();
        if (display == null) return null;
        display.getRealSize(tmpDisplaySize);
        if (tmpDisplaySize.x <= 0 || tmpDisplaySize.y <= 0) return null;

        geometrySource.getLocationOnScreen(tmpDockLocation);
        Rect displayRect = new Rect(0, 0, tmpDisplaySize.x, tmpDisplaySize.y);
        Rect dockRect = new Rect(
                tmpDockLocation[0], tmpDockLocation[1],
                tmpDockLocation[0] + Math.max(1, geometrySource.getWidth()),
                tmpDockLocation[1] + Math.max(1, geometrySource.getHeight()));
        if (!dockRect.intersect(displayRect) || dockRect.isEmpty()) return null;

        Rect tileRect = new Rect(dockRect);
        tileRect.inset(-captureBleedPx, -captureBleedPx);
        if (!tileRect.intersect(displayRect) || tileRect.isEmpty()) return null;

        // Deliberately hard-limit compositor readback to the lower display region.
        Rect stripRect = new Rect(0, tileRect.top, displayRect.right, displayRect.bottom);
        if (stripRect.isEmpty()) return null;

        return new CaptureRequest(display.getDisplayId(), stripRect, tileRect, dockRect);
    }

    private static CroppedFrame cropWallpaperTile(Bitmap strip, Rect stripRect,
                                                   Rect tileRect, Rect dockRect) {
        if (strip == null || strip.isRecycled() || stripRect.isEmpty() || tileRect.isEmpty()) {
            if (strip != null && !strip.isRecycled()) strip.recycle();
            return null;
        }

        float sx = strip.getWidth() / (float) stripRect.width();
        float sy = strip.getHeight() / (float) stripRect.height();
        if (!(sx > 0f) || !(sy > 0f)) {
            strip.recycle();
            return null;
        }

        int left = clamp((int) Math.floor((tileRect.left - stripRect.left) * sx),
                0, strip.getWidth() - 1);
        int top = clamp((int) Math.floor((tileRect.top - stripRect.top) * sy),
                0, strip.getHeight() - 1);
        int right = clamp((int) Math.ceil((tileRect.right - stripRect.left) * sx),
                left + 1, strip.getWidth());
        int bottom = clamp((int) Math.ceil((tileRect.bottom - stripRect.top) * sy),
                top + 1, strip.getHeight());

        Bitmap tile = Bitmap.createBitmap(strip, left, top, right - left, bottom - top);
        if (tile != strip && !strip.isRecycled()) strip.recycle();

        float actualLeft = stripRect.left + left / sx;
        float actualTop = stripRect.top + top / sy;
        float sourceWidth = tile.getWidth() / sx;
        float sourceHeight = tile.getHeight() / sy;
        return new CroppedFrame(tile,
                dockRect.left - actualLeft,
                dockRect.top - actualTop,
                sourceWidth, sourceHeight);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Ask SurfaceFlinger to blur THIS view's own rendered content (MIUI self-blur).
     *  The RuntimeShader now only refracts; the expensive, high-quality blur is the system's
     *  native pass (RenderNode.setSelfBlurRadius → makePassBlurBetterDownShader), which is
     *  far better than the software 5-tap sampling the shader used to do.
     *
     *  Decompiled evidence (HyperOS launcher, mingou_desktop_blur_overlay path): self-blur
     *  only takes effect when the view ALSO gets setMiSelfBlurEnhanceFlag(0x200, 0x200) —
     *  without the enhance flag SurfaceFlinger ignores the RenderNode's self-blur radius. */
    private void applySystemSelfBlur(int radius) {
        try {
            if (radius <= 0) return;
            // View.setMiSelfBlur(int radius, ArrayList<Display.ColorMode> colorModes) — hidden
            // MIUI API (TEST-API in framework).  Called reflectively like the rest of the
            // module's hidden-surface usage.
            Class<?> viewClass = View.class;
            java.lang.reflect.Method m;
            try {
                m = viewClass.getDeclaredMethod("setMiSelfBlur",
                        int.class, java.util.ArrayList.class);
            } catch (NoSuchMethodException e) {
                m = viewClass.getDeclaredMethod("setMiSelfBlur", int.class);
            }
            m.setAccessible(true);
            if (m.getParameterTypes().length == 2) {
                m.invoke(this, radius, null);
            } else {
                m.invoke(this, radius);
            }
            // Enhance flag 0x200 = "blur this view's own drawn content".  Without it the
            // self-blur radius is set on the RenderNode but SurfaceFlinger never honors it.
            try {
                java.lang.reflect.Method flagMethod =
                        viewClass.getDeclaredMethod("setMiSelfBlurEnhanceFlag", int.class, int.class);
                flagMethod.setAccessible(true);
                flagMethod.invoke(this, 0x200, 0x200);
            } catch (NoSuchMethodException e) {
                Log.w(TAG, "setMiSelfBlurEnhanceFlag not available");
            }
            Log.i(TAG, "Liquid glass: system self-blur applied radius=" + radius);
        } catch (Throwable e) {
            Log.w(TAG, "system self-blur failed: " + e);
        }
    }

    private void installCapture(CroppedFrame frame) {
        // Do not make the native Dock transparent until a real wallpaper-only frame exists.
        // This avoids the fully-transparent Dock failure mode when hidden capture APIs reject.
        if (!nativeBackgroundHiddenByGlass) {
            geometrySource.setAlpha(0f);
            nativeBackgroundHiddenByGlass = true;
            // The RuntimeShader draws the REFRACTED capture (blur-free); the actual blur is
            // done by SurfaceFlinger on our own content via MIUI self-blur (RenderNode
            // setSelfBlurRadius), matching the native makePassBlurBetterDownShader pipeline.
            applySystemSelfBlur(blurRadius);
        }

        Bitmap old = capture;
        capture = frame.bitmap;
        captureSampleOffsetX = frame.sampleOffsetX;
        captureSampleOffsetY = frame.sampleOffsetY;
        captureSourceWidth = Math.max(1f, frame.sourceWidth);
        captureSourceHeight = Math.max(1f, frame.sourceHeight);
        captureShader = new BitmapShader(capture, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        invalidate();
        if (old != null && old != capture && !old.isRecycled()) old.recycle();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (captureShader == null || capture == null || capture.isRecycled()
                || getWidth() <= 0 || getHeight() <= 0) {
            if (drawFailLogged < 2) { drawFailLogged++;
                Log.w(TAG, "onDraw skip: shader=" + (captureShader != null)
                        + " capture=" + (capture != null && !capture.isRecycled())
                        + " w=" + getWidth() + " h=" + getHeight()); }
            return;
        }
        captureShader.setLocalMatrix(null);
        refraction.setInputShader("content", captureShader);
        refraction.setFloatUniform("size", getWidth(), getHeight());
        refraction.setFloatUniform("offset", 0f, 0f);
        refraction.setFloatUniform("cornerRadii", cornerRadius, cornerRadius, cornerRadius, cornerRadius);
        refraction.setFloatUniform("refractionHeight", Math.max(1f, Math.min(getHeight() * .48f, 140f)));
        refraction.setFloatUniform("refractionAmount", refractionAmount);
        refraction.setFloatUniform("sCurveAmount", sCurveAmount);
        refraction.setFloatUniform("depthEffect", .08f);
        refraction.setFloatUniform("chromaticAberration", chromaticAberration);
        refraction.setFloatUniform("blurRadius", blurRadius);
        refraction.setFloatUniform("screenOffset", captureSampleOffsetX, captureSampleOffsetY);
        refraction.setFloatUniform("captureScale",
                capture.getWidth() / Math.max(1f, captureSourceWidth),
                capture.getHeight() / Math.max(1f, captureSourceHeight));
        glassPaint.setShader(refraction);
        Path shape = shapePath(getWidth(), getHeight(), cornerRadius);
        canvas.save();
        canvas.clipPath(shape);
        canvas.drawRect(0, 0, getWidth(), getHeight(), glassPaint);
        canvas.drawPath(shape, tintPaint);
        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setStrokeWidth(Math.max(1f, getResources().getDisplayMetrics().density * .65f));
        highlightPaint.setShader(new LinearGradient(0, 0, getWidth(), getHeight(),
            new int[]{Color.argb(175, 255, 255, 255), Color.argb(25, 255, 255, 255), Color.argb(105, 255, 255, 255)},
            null, Shader.TileMode.CLAMP));
        canvas.drawPath(shape, highlightPaint);
        canvas.restore();
    }

    private Path shapePath(float width, float height, float radius) {
        RectF r = new RectF(.5f, .5f, width - .5f, height - .5f);
        Path p = new Path();
        if (!squircle || radius <= 1f) {
            p.addRoundRect(r, radius, radius, Path.Direction.CW);
            return p;
        }
        float a = Math.min(radius, Math.min(width, height) * .5f), c = a * squircleCp;
        p.moveTo(r.left, r.top + a);
        p.cubicTo(r.left, r.top + a - c, r.left + a - c, r.top, r.left + a, r.top);
        p.lineTo(r.right - a, r.top);
        p.cubicTo(r.right - a + c, r.top, r.right, r.top + a - c, r.right, r.top + a);
        p.lineTo(r.right, r.bottom - a);
        p.cubicTo(r.right, r.bottom - a + c, r.right - a + c, r.bottom, r.right - a, r.bottom);
        p.lineTo(r.left + a, r.bottom);
        p.cubicTo(r.left + a - c, r.bottom, r.left, r.bottom - a + c, r.left, r.bottom - a);
        p.close();
        return p;
    }

    private static final class CaptureRequest {
        final int displayId;
        final Rect stripRect;
        final Rect tileRect;
        final Rect dockRect;

        CaptureRequest(int displayId, Rect stripRect, Rect tileRect, Rect dockRect) {
            this.displayId = displayId;
            this.stripRect = new Rect(stripRect);
            this.tileRect = new Rect(tileRect);
            this.dockRect = new Rect(dockRect);
        }
    }

    private static final class CroppedFrame {
        final Bitmap bitmap;
        final float sampleOffsetX;
        final float sampleOffsetY;
        final float sourceWidth;
        final float sourceHeight;

        CroppedFrame(Bitmap bitmap, float sampleOffsetX, float sampleOffsetY,
                     float sourceWidth, float sourceHeight) {
            this.bitmap = bitmap;
            this.sampleOffsetX = sampleOffsetX;
            this.sampleOffsetY = sampleOffsetY;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
        }

        void recycle() {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
    }
}
