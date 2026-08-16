package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.View;

/**
 * Refraction + highlight overlay for the opt-in HyperOS 3.0.307+ MiuiX material path.
 *
 * MiuiX remains the blur owner. This View captures only the small unblurred composited strip
 * below the Floating Dock, keeps the result GPU-backed, and feeds it directly to AGSL as the
 * refraction input. It intentionally has no CaptureSceneState or launcher ownership logic.
 */
final class Miuix307RefractionView extends View {
    private static final int MAX_CAPTURE_FPS = 30;
    private static final float MIN_CAPTURE_SCALE = 0.25f;
    private static final float MAX_CAPTURE_SCALE = 0.50f;
    private static final int CAPTURE_MODE_FULL_DISPLAY = 1;
    private static final long RETIRE_BITMAP_DELAY_MS = 220L;
    private static final String[] CAPTURE_EXCLUDE_NAMES = {"Floating Dock"};

    private static final String REFRACTION_SHADER =
            "uniform shader content;"
          + "uniform float2 size;"
          + "uniform float2 captureInset;"
          + "uniform float2 captureScale;"
          + "uniform float radius;"
          + "uniform float thickness;"
          + "uniform float ior;"
          + "uniform float normalStrength;"
          + "uniform float dome;"
          + "uniform float lensRefractionPx;"
          + "uniform float chromaticAberration;"
          + "uniform float refractedAlpha;"
          + "float sdRound(float2 p,float2 h,float r){"
          + "float2 q=abs(p)-(h-float2(r));"
          + "return length(max(q,0.0))-r+min(max(q.x,q.y),0.0);"
          + "}"
          + "float2 sdfGrad(float2 p,float2 h,float r){"
          + "float e=1.0;"
          + "float gx=sdRound(p+float2(e,0.0),h,r)-sdRound(p-float2(e,0.0),h,r);"
          + "float gy=sdRound(p+float2(0.0,e),h,r)-sdRound(p-float2(0.0,e),h,r);"
          + "float2 g=float2(gx,gy);float l=length(g);"
          + "return l>0.0001?g/l:float2(0.0,1.0);"
          + "}"
          + "half4 backdrop(float2 local){"
          + "float2 c=(local+captureInset)*captureScale;"
          + "return content.eval(c);"
          + "}"
          + "half4 main(float2 coord){"
          + "float2 halfSize=max(size*0.5,float2(1.0));"
          + "float2 p=coord-halfSize;"
          + "float rr=clamp(radius,0.0,min(halfSize.x,halfSize.y));"
          + "float sd=sdRound(p,halfSize,rr);"
          + "float mask=1.0-smoothstep(-1.25,1.25,sd);"
          + "if(mask<=0.001){return half4(0.0);}"
          + "float edgeDist=max(-sd,0.0);"
          + "float edgeW=1.0-smoothstep(0.0,max(thickness,1.0),edgeDist);"
          + "float2 n=sdfGrad(p,halfSize,rr);"
          + "float plen=length(p);float2 radial=plen>0.001?p/plen:float2(0.0);"
          + "float eta=max(ior-1.0,0.02);"
          + "float displacementMagnitude=lensRefractionPx*eta*normalStrength;"
          + "float2 displacement=-n*displacementMagnitude*edgeW;"
          + "float domeW=(1.0-edgeW)*clamp(dome,0.0,2.0);"
          + "displacement+=-radial*lensRefractionPx*0.14*domeW;"
          + "float chromaPx=max(chromaticAberration,0.0)*max(lensRefractionPx,1.0)*0.65;"
          + "half4 center=backdrop(coord+displacement);"
          + "half4 redSample=backdrop(coord+displacement+n*chromaPx);"
          + "half4 blueSample=backdrop(coord+displacement-n*chromaPx);"
          + "half3 refracted=half3(redSample.r,center.g,blueSample.b);"
          + "float presence=clamp(0.18+edgeW*0.72+domeW*0.10,0.0,1.0);"
          + "float alpha=mask*refractedAlpha*presence;"
          + "return half4(refracted,alpha);"
          + "}";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Paint refractionPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF highlightBounds = new RectF();
    private final RuntimeShader refraction;
    private final int[] tmpLocation = new int[2];
    private final Point tmpDisplaySize = new Point();

    private final LiveScreenCapture screenCapture;
    private final float density;
    private final float requestedCaptureScale;
    private final int captureFps;
    private final long captureIntervalMs;
    private final float thicknessPx;
    private final float ior;
    private final float normalStrength;
    private final float dome;
    private final float lensRefractionPx;
    private final float chromaticAberration;

    private float radius;
    private float highlightAlpha = 1f;
    private float highlightWidth = 1f;
    private float captureInsetX;
    private float captureInsetY;
    private float effectiveCaptureScaleX = 1f;
    private float effectiveCaptureScaleY = 1f;
    private Bitmap currentBitmap;
    private BitmapShader currentShader;
    private boolean shaderReady;
    private boolean attached;
    private boolean capturing;
    private boolean captureDirty;
    private boolean captureDisabled;
    private int generation;
    private long lastCaptureStartUptime;

    private final Runnable captureTick = new Runnable() {
        @Override public void run() {
            if (!canCapture()) return;
            long now = SystemClock.uptimeMillis();
            long remaining = captureIntervalMs - (now - lastCaptureStartUptime);
            if (lastCaptureStartUptime != 0L && remaining > 0L) {
                mainHandler.postDelayed(this, remaining);
                return;
            }
            if (capturing) {
                captureDirty = true;
                return;
            }
            submitCapture();
        }
    };

    Miuix307RefractionView(Context context, ClassLoader launcherClassLoader, LiquidDockConfig config) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        requestedCaptureScale = Math.max(MIN_CAPTURE_SCALE,
                Math.min(MAX_CAPTURE_SCALE, config.glass.captureScale));
        captureFps = Math.max(1, Math.min(MAX_CAPTURE_FPS, config.glass.captureFps));
        captureIntervalMs = Math.max(1L, Math.round(1000f / captureFps));

        float unit = config.glass.dimensionsDp ? density : 1f;
        thicknessPx = Math.max(1f, config.glass.thickness * unit);
        ior = Math.max(1.01f, config.glass.ior);
        normalStrength = Math.max(0.05f, config.glass.normalStrength);
        dome = Math.max(0f, config.glass.dome);
        lensRefractionPx = Math.max(0f, config.glass.lensRefraction * unit);
        chromaticAberration = Math.max(0f, config.glass.chromatic);

        RuntimeShader shader = null;
        try {
            shader = new RuntimeShader(REFRACTION_SHADER);
        } catch (Throwable error) {
            captureDisabled = true;
            MainHook.log("[DC] MiuiX 307 refraction shader unavailable; highlight-only: " + error);
        }
        refraction = shader;

        LiveScreenCapture capture = null;
        if (refraction != null) {
            try {
                capture = new LiveScreenCapture(requestedCaptureScale, launcherClassLoader);
            } catch (Throwable error) {
                captureDisabled = true;
                MainHook.log("[DC] MiuiX 307 refraction capture unavailable; highlight-only: " + error);
            }
        }
        screenCapture = capture;

        if (refraction != null) refractionPaint.setShader(refraction);
        highlightPaint.setStyle(Paint.Style.STROKE);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);
        setClipToOutline(false);
    }

    void setMaterialGeometry(float radius, float alpha, float width) {
        this.radius = Math.max(0f, radius);
        highlightAlpha = Math.max(0f, Math.min(2f, alpha));
        highlightWidth = Math.max(.25f, Math.min(4f, width));
        invalidate();
        requestBackdrop(true);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        generation++;
        requestBackdrop(true);
    }

    @Override protected void onDetachedFromWindow() {
        attached = false;
        generation++;
        capturing = false;
        captureDirty = false;
        mainHandler.removeCallbacks(captureTick);
        Bitmap old = currentBitmap;
        currentBitmap = null;
        currentShader = null;
        shaderReady = false;
        if (old != null && !old.isRecycled()) old.recycle();
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) requestBackdrop(true);
        else mainHandler.removeCallbacks(captureTick);
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw || h != oldh) requestBackdrop(true);
    }

    private void requestBackdrop(boolean immediate) {
        if (!canCapture()) return;
        mainHandler.removeCallbacks(captureTick);
        if (immediate) {
            lastCaptureStartUptime = 0L;
            mainHandler.post(captureTick);
        } else {
            mainHandler.postDelayed(captureTick, captureIntervalMs);
        }
    }

    private boolean canCapture() {
        return attached && refraction != null && !captureDisabled && screenCapture != null && isShown()
                && getWidth() > 1 && getHeight() > 1 && getDisplay() != null;
    }

    private int computeBleedPx() {
        float optical = lensRefractionPx * (1f + chromaticAberration)
                + thicknessPx * .55f + 4f * density;
        return Math.max(8, Math.min(256, (int) Math.ceil(optical)));
    }

    private void submitCapture() {
        if (!canCapture()) return;
        Display display = getDisplay();
        if (display == null) return;

        display.getRealSize(tmpDisplaySize);
        getLocationOnScreen(tmpLocation);
        int x = tmpLocation[0];
        int y = tmpLocation[1];
        int bleed = computeBleedPx();
        Rect crop = new Rect(x - bleed, y - bleed,
                x + getWidth() + bleed, y + getHeight() + bleed);
        if (!crop.intersect(0, 0, tmpDisplaySize.x, tmpDisplaySize.y) || crop.isEmpty()) {
            requestBackdrop(false);
            return;
        }

        final int requestGeneration = generation;
        final Rect requestCrop = new Rect(crop);
        final float requestInsetX = x - crop.left;
        final float requestInsetY = y - crop.top;
        capturing = true;
        captureDirty = false;
        lastCaptureStartUptime = SystemClock.uptimeMillis();

        screenCapture.captureScreenAsync(requestCrop, requestedCaptureScale, display.getDisplayId(),
                null, CAPTURE_EXCLUDE_NAMES, CAPTURE_MODE_FULL_DISPLAY,
                new LiveScreenCapture.CaptureCallback() {
                    @Override public void onResult(Bitmap bitmap) {
                        mainHandler.post(() -> installFrame(requestGeneration, requestCrop,
                                requestInsetX, requestInsetY, bitmap));
                    }

                    @Override public void onError(Throwable error) {
                        mainHandler.post(() -> disableCapture(requestGeneration, error));
                    }
                });
    }

    private void installFrame(int requestGeneration, Rect requestCrop,
                              float requestInsetX, float requestInsetY, Bitmap bitmap) {
        if (requestGeneration != generation || !attached || refraction == null) {
            recycle(bitmap);
            return;
        }
        capturing = false;
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            disableCapture(requestGeneration, new IllegalStateException("empty refraction frame"));
            return;
        }

        Bitmap old = currentBitmap;
        BitmapShader nextShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        refraction.setInputShader("content", nextShader);
        currentBitmap = bitmap;
        currentShader = nextShader;
        captureInsetX = requestInsetX;
        captureInsetY = requestInsetY;
        effectiveCaptureScaleX = bitmap.getWidth() / (float) Math.max(1, requestCrop.width());
        effectiveCaptureScaleY = bitmap.getHeight() / (float) Math.max(1, requestCrop.height());
        shaderReady = true;
        invalidate();

        if (old != null && old != bitmap) {
            mainHandler.postDelayed(() -> {
                if (old != currentBitmap) recycle(old);
            }, RETIRE_BITMAP_DELAY_MS);
        }

        boolean trailing = captureDirty;
        captureDirty = false;
        requestBackdrop(trailing);
    }

    private void disableCapture(int requestGeneration, Throwable error) {
        if (requestGeneration != generation) return;
        capturing = false;
        captureDirty = false;
        captureDisabled = true;
        mainHandler.removeCallbacks(captureTick);
        MainHook.log("[DC] MiuiX 307 refraction capture disabled; highlight-only: " + error);
        invalidate();
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 1 || h <= 1) return;

        if (refraction != null && shaderReady && currentShader != null) {
            float safeRadius = Math.max(0f, Math.min(radius, Math.min(w, h) * .5f));
            refraction.setFloatUniform("size", w, h);
            refraction.setFloatUniform("captureInset", captureInsetX, captureInsetY);
            refraction.setFloatUniform("captureScale", effectiveCaptureScaleX, effectiveCaptureScaleY);
            refraction.setFloatUniform("radius", safeRadius);
            refraction.setFloatUniform("thickness", thicknessPx);
            refraction.setFloatUniform("ior", ior);
            refraction.setFloatUniform("normalStrength", normalStrength);
            refraction.setFloatUniform("dome", dome);
            refraction.setFloatUniform("lensRefractionPx", lensRefractionPx);
            refraction.setFloatUniform("chromaticAberration", chromaticAberration);
            refraction.setFloatUniform("refractedAlpha", .62f);
            canvas.drawRect(0f, 0f, w, h, refractionPaint);
        }

        drawHighlight(canvas, w, h);
    }

    private void drawHighlight(Canvas canvas, int w, int h) {
        if (highlightAlpha <= .001f) return;
        float stroke = Math.max(1f, 1.25f * density * highlightWidth);
        float inset = stroke * .5f + .5f;
        highlightBounds.set(inset, inset, w - inset, h - inset);
        if (highlightBounds.width() <= 0f || highlightBounds.height() <= 0f) return;

        int a0 = Math.min(255, Math.round(190f * highlightAlpha));
        int a1 = Math.min(255, Math.round(72f * highlightAlpha));
        highlightPaint.setStrokeWidth(stroke);
        highlightPaint.setShader(new LinearGradient(
                0f, 0f, w, h,
                new int[]{Color.argb(a0, 255, 255, 255),
                        Color.argb(a1, 242, 248, 255), Color.TRANSPARENT},
                new float[]{0f, .48f, 1f}, Shader.TileMode.CLAMP));
        float r = Math.max(0f, Math.min(radius - inset,
                Math.min(highlightBounds.width(), highlightBounds.height()) * .5f));
        canvas.drawRoundRect(highlightBounds, r, r, highlightPaint);
        highlightPaint.setShader(null);
    }
}
