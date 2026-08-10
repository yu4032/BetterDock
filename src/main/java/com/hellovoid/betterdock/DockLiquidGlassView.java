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
    // Compositor readback scale: 1.0 = full resolution, 0.5 = half (4x fewer pixels).
    // GUI-configurable via liquid_capture_scale (%); 0.25 is the recommended low-cost
    // setting — the glass is blurred anyway, so refraction is visually lossless there.
    private float captureScale = 0.5f;
    private static final String REFRACTION_SHADER =
        "uniform shader content;"
      + "uniform float2 size;"
      + "uniform float2 offset;"
      + "uniform float4 cornerRadii;"
      + "uniform float refractionHeight;"
      + "uniform float refractionAmount;"
      + "uniform float depthEffect;"
      + "uniform float chromaticAberration;"
      + "uniform float blurRadius;"
      + "uniform float2 screenOffset;"
      + "uniform float2 captureScale;"
      + "uniform float thickness;"
      + "uniform float ior;"
      + "uniform float normalStrength;"
      + "uniform float liquidDome;"
      + "uniform float lensRefractionPx;"
      + "uniform float refractionInset;"
      + "uniform float highlightWidth;"
      + "uniform float brightness;"
      + "uniform float specularSharp;"
      + "uniform float specularStrength;"
      + "uniform float rimLight;"
      + "uniform float causticStrength;"
      + "uniform float edgeBand;"
      + "float radiusAt(float2 p,float4 r){if(p.x>=0){return p.y<=0?r.y:r.z;}return p.y<=0?r.x:r.w;}"
      + "float sdRound(float2 p,float2 h,float r){float2 q=abs(p)-(h-float2(r));return length(max(q,0.0))-r+min(max(q.x,q.y),0.0);}"
      + "float2 gradRound(float2 p,float2 h,float r){float2 q=abs(p)-(h-float2(r));float2 s=sign(p);"
      + "s.x=s.x==0.0?1.0:s.x;s.y=s.y==0.0?1.0:s.y;if(q.x>=0.0||q.y>=0.0)return s*normalize(max(q,0.0001));"
      + "float gx=step(q.y,q.x);return s*float2(gx,1.0-gx);}"
      + "float circleMap(float x){x=clamp(x,0.0,1.0);return 1.0-sqrt(max(0.0,1.0-x*x));}"
      + "float getHeightFromDist(float dist,float tw){float t=clamp(-dist/tw,0.0,1.0);return sqrt(max(0.0,2.0*t-t*t));}"
      + "float2 computeGradientHeight(float2 p,float2 halfSz,float cr,float tw){"
      + "float s=1.0;float hpx=getHeightFromDist(sdRound(p+float2(s,0.0),halfSz,cr),tw);"
      + "float hnx=getHeightFromDist(sdRound(p-float2(s,0.0),halfSz,cr),tw);"
      + "float hpy=getHeightFromDist(sdRound(p+float2(0.0,s),halfSz,cr),tw);"
      + "float hny=getHeightFromDist(sdRound(p-float2(0.0,s),halfSz,cr),tw);"
      + "return float2((hpx-hnx)*0.5,(hpy-hny)*0.5);}"
      + "half4 source(float2 p){return content.eval((p+screenOffset)*captureScale);}"
      + "half4 blurred(float2 p){"
      + "if(blurRadius<=0.5){return source(p);}"
      + "float stepPx=max(blurRadius/3.0,1.0);"
      + "half3 col=half3(0.0);float norm=0.0;"
      + "for(float i=-3.0;i<=3.0;i+=1.0){"
      + "for(float j=-3.0;j<=3.0;j+=1.0){"
      + "float d2=i*i+j*j;float w=exp(-d2*0.5);"
      + "col+=source(p+float2(i*stepPx,j*stepPx)).rgb*w;norm+=w;}}"
      + "return half4(col/norm,1.0);}"
      + "float2 backdropUv(float2 uv,float2 offsetPx){return clamp(uv+offsetPx,0.0,1.0);}"
      + "half4 main(float2 coord){"
      + "float2 hs=size*0.5;float2 cc=(coord+offset)-hs;float2 pPx=cc;"
      + "float r=radiusAt(cc,cornerRadii);float cr=min(r,min(hs.x,hs.y));"
      + "float sd=sdRound(cc,hs,cr);"
      + "float minDim=min(hs.x,hs.y);"
      + "float dome=clamp(liquidDome,0.0,2.0);"
      + "float tw=max(refractionHeight*(1.0+0.38*dome),1.0);tw=min(tw,minDim*0.98);"
      + "float hSig=getHeightFromDist(sd,tw);"
      + "float2 gradHSig=computeGradientHeight(pPx,hs,cr,tw);"
      + "float gradRadius=min(cr*1.5,min(hs.x,hs.y));"
      + "float2 gradLens=gradRound(cc,hs,gradRadius);"
      + "float edgeDist=-sd;"
      + "float innerReach=max(min(hs.x,hs.y)-cr*0.42,minDim*0.22);"
      + "innerReach+=refractionHeight*(1.0+0.25*dome);innerReach=min(innerReach,max(hs.x,hs.y)*0.95);"
      + "float tDeep=clamp(edgeDist/max(innerReach,2.0),0.0,1.0);"
      + "float tShell=1.0-tDeep;"
      + "float meniscusBand=smoothstep(0.0,0.12,tShell);"
      + "float hCap=pow(tShell,0.38);"
      + "float edgeBulge=0.10*pow(tShell,2.8);"
      + "float hDome=(hCap+edgeBulge)*meniscusBand;"
      + "float coreBlend=smoothstep(0.0,0.38,tDeep);"
      + "float hSlab=mix(hSig*(0.58+0.42*coreBlend),hSig,0.4+0.6*(1.0-dome));"
      + "float domeW=dome*(0.74+0.26*smoothstep(0.12,0.94,tShell));"
      + "float height=mix(hSlab,hDome,domeW);"
      + "float edgeRound=1.0-smoothstep(0.72,1.0,tShell);"
      + "height=clamp(height*(0.84+0.16*meniscusBand+0.08*edgeRound),0.0,1.0);"
      + "float2 outward=(length(gradLens)>1e-4)?normalize(gradLens):float2(0.0,1.0);"
      + "float shellCurv=smoothstep(0.0,1.0,tShell);"
      + "float2 gCap=outward*(-shellCurv*(0.38/max(minDim,8.0)));"
      + "gCap*=meniscusBand*edgeRound;"
      + "float2 gradH=mix(gradHSig,gCap,domeW);"
      + "float3 N=normalize(float3(-gradH.x*normalStrength,-gradH.y*normalStrength,1.0));"
      + "float3 V=float3(0.0,0.0,1.0);"
      + "float cosVN=clamp(dot(N,V),0.0,1.0);"
      + "float r0=pow((1.0-ior)/(1.0+ior),2.0);"
      + "float silW=clamp(minDim*0.12*max(0.1,highlightWidth),1.0,90.0);"
      + "float edgeSil=smoothstep(silW,0.0,edgeDist)*smoothstep(-4.5,0.0,sd);"
      + "float tiltW=clamp(length(N.xy)*2.4,0.0,1.0);"
      + "float grazingW=clamp(edgeSil*0.94+tiltW*0.55,0.0,1.0);"
      + "float cosVNeff=mix(cosVN,max(0.04,cosVN*0.22+0.07*tiltW),grazingW);"
      + "float F=r0+(1.0-r0)*pow(1.0-cosVNeff,5.0);"
      + "float2 cenSafe=cc+float2(1e-4,1e-4);"
      + "float2 lensDir=gradLens+depthEffect*normalize(cenSafe);"
      + "float ldLen=length(lensDir);lensDir=ldLen>1e-5?lensDir/ldLen:float2(0.0);"
      + "float lensRh=refractionHeight;"
      + "float sdIn=min(sd,0.0);"
      + "float dLens=0.0;"
      + "if((-sd)<lensRh){dLens=circleMap(1.0-(-sdIn/lensRh))*(-lensRefractionPx);}"
      + "float2 lensDeltaUv=(dLens*lensDir)/size;"
      + "float parallaxK=0.052*1.15;"
      + "float2 parallax=(gradLens*height*(7.0+22.0*F))/size*parallaxK*1.0;"
      + "lensDeltaUv+=parallax;"
      + "lensDeltaUv*=mix(0.78,1.12,(1.0-F)*(0.42+0.58*height));"
      + "float refrStr=height*(0.5+F*0.35);"
      + "float3 refIn=refract(-V,N,1.0/ior);"
      + "float3 refOut=(dot(refIn,refIn)<0.001)?float3(0.0):refract(refIn,-N,ior);"
      + "float2 snellOff=(refOut.xy*thickness*refrStr/size)*1.15;"
      + "snellOff*=mix(0.72,1.18,(1.0-F)*(0.5+0.5*height));"
      + "float2 bDir=length(pPx)>1e-3?-normalize(pPx):float2(0.0,-1.0);"
      + "float bulge=smoothstep(0.05,0.38,tDeep)*(1.0-smoothstep(0.52,0.94,tDeep));"
      + "bulge=pow(max(bulge,0.0),0.62)*height*(0.014+0.01*dome);"
      + "bulge*=smoothstep(0.02,0.36,tDeep);"
      + "float2 bulgeUv=bDir*bulge*hs/size;"
      + "float2 baseOffset=lensDeltaUv+snellOff+bulgeUv;"
      + "float2 uv=backdropUv(coord/size,baseOffset);"
      + "float caAmt=max(chromaticAberration,0.0);"
      + "float avgDim=(size.x+size.y)*0.5;"
      + "float chromaFar=avgDim*0.5;"
      + "float edgeFac=pow(smoothstep(chromaFar,0.0,edgeDist),1.8);"
      + "float chromaBase=caAmt*0.0018*edgeFac;"
      + "float2 dispDir=length(pPx)>1e-3?normalize(pPx):float2(0.0,1.0);"
      + "float2 chromaPush=dispDir*chromaBase;"
      + "float2 uvR=backdropUv(coord/size,baseOffset+chromaPush);"
      + "float2 uvB=backdropUv(coord/size,baseOffset-chromaPush);"
      + "float2 centerPx=uv*size-screenOffset;"
      + "half4 gg=blurred(centerPx);"
      + "half4 rr=(chromaBase<1e-4)?gg:blurred(uvR*size-screenOffset);"
      + "half4 bb=(chromaBase<1e-4)?gg:blurred(uvB*size-screenOffset);"
      + "float3 color=float3(rr.r,gg.g,bb.b);"
      + "color*=brightness;"
      + "color=mix(color,color*float3(0.137,0.145,1.0),0.137);"
      + "float3 Lp=normalize(float3(-0.5,-0.8,1.45));"
      + "float3 Hp=normalize(Lp+V);"
      + "float sh=max(specularSharp,1.0);float sp=1.52*max(specularStrength,0.0);"
      + "float specP=pow(max(dot(N,Hp),0.0),sh)*sp;"
      + "specP*=(0.32+0.68*height);"
      + "color+=specP*float3(0.99,0.993,1.0);"
      + "float dotNV=clamp(dot(N,V),0.0,1.0);"
      + "float FedgeRim=pow(1.0-cosVNeff,3.25);"
      + "float bandFracR=max(edgeBand,0.005);"
      + "float bandR=clamp(minDim*bandFracR,0.5,min(12.0,minDim*0.1));"
      + "float shellRim=smoothstep(bandR,bandR*0.06,edgeDist)*smoothstep(-2.2,0.0,sd);"
      + "float2 cn=cc/max(hs,float2(1.0));"
      + "float2 Lxy=normalize(float2(-0.5,-0.8)+float2(1e-5));"
      + "float2 gN=normalize(gradLens+float2(1e-4));"
      + "float edgeLight=dot(gN,Lxy);"
      + "float rimLitSide=pow(max(edgeLight,0.0),3.6)*shellRim*1.22*0.95*rimLight*(0.58+0.42*height);"
      + "float rimOpposite=pow(max(-edgeLight,0.0),1.05)*shellRim*1.22*0.4*rimLight*(0.4+0.6*height);"
      + "color+=float3(0.98,0.992,1.008)*rimLitSide;"
      + "color+=float3(0.952,0.968,1.018)*rimOpposite;"
      + "float causticDot=dot(normalize(float3(gradH*normalStrength,0.45)),Lp);"
      + "float caust=pow(max(causticDot,0.0),7.0)*max(causticStrength,0.0)*height;"
      + "color+=caust*float3(1.0,0.96,0.90);"
      + "return half4(color,1.0);}";

    private final View workspace;
    // Launcher's recents/multitasking view (Launcher.getRecentsView()).  While it is
    // visible, its content moves even when the Dock itself is static (task cards flying in,
    // scrolling the overview), so observation must also track it — but ONLY when visible,
    // so normal home-screen page swipes (recents hidden) still do not trigger captures.
    private View recentsView;
    private final View geometrySource;
    private final RuntimeShader refraction;
    private final Paint glassPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int blurRadius;
    private final float refractionAmount;
    private final float chromaticAberration;
    // Prismal liquid-glass parameters (GUI-configurable)
    private float glassThickness = 18f;      // liquid_thickness (dp -> px at render)
    private float glassIor = 1.55f;          // liquid_ior
    private float glassNormalStrength = 1.15f; // liquid_normal_strength
    private float glassLiquidDome = 1.0f;    // liquid_dome
    private float glassLensRefraction = 12f; // liquid_lens_refraction (dp -> px)
    private float glassRefractionInset = 20f; // liquid_refraction_inset (dp -> px)
    // Edge highlight thickness multiplier (liquid_highlight_width): scales the shader's
    // edge-glow band (silW) AND the canvas stroke highlight width.
    private float glassHighlightWidth = 1.0f;
    // Glass tint color (liquid_tint_r/g/b); alpha stays liquid_tint_alpha.
    private int glassTintR = 238, glassTintG = 244, glassTintB = 255;
    // Shader appearance knobs (all GUI-configurable)
    private float glassDepthEffect = 0.08f;    // liquid_depth_effect
    private float glassBrightness = 1.08f;     // liquid_brightness
    private float glassSpecularSharp = 88f;    // liquid_specular_sharp
    private float glassSpecularStrength = 1.05f; // liquid_specular_strength
    private float glassRimLight = 1.0f;        // liquid_rim_light
    private float glassCaustics = 0.28f;       // liquid_caustics
    private float glassEdgeBand = 0.032f;      // liquid_edge_band (fraction of minDim)
    // Canvas stroke highlight opacity multiplier (liquid_highlight_alpha)
    private float glassHighlightAlpha = 1.0f;
    // True while a Dock icon drag is in flight (MainHook hooks DragController.startDrag/
    // endDrag).  During a drag the glass keeps capturing so the background follows the icon
    // re-arrangement, and the drag surface layer is excluded so the floating icon never
    // freezes into the captured background.
    private volatile boolean dockDragging = false;
    private volatile String dragLayerName = null;
    private final long captureIntervalNanos;
    private final int captureBleedPx;
    // Extra capture height above/below the glass (GUI: liquid_capture_bleed_top /
    // liquid_capture_bleed_bottom).  The Dock's distance from the screen bottom is fixed,
    // so top and bottom are independent knobs; the bottom one can stay small so the
    // home-indicator / dock boundary lines never leak into the captured background.
    // -1 = half the horizontal bleed (previous behavior).
    private int bleedTopPx = -1;
    private int bleedBottomPx = -1;

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
    private String dockWindowLayerName;
    private boolean capturing;
    private boolean sourceDirty;
    private boolean nullFrameLogged;
    private int drawFailLogged;
    private boolean nativeBackgroundHiddenByGlass;
    private boolean kickScheduled;
    private long captureGeneration;
    private long lastCaptureStartNanos;

    // Grace period for capture-stop: the Dock is often mid-animation (collapse/translate)
    // when window visibility flips, and killing capture instantly freezes the last frame
    // mid-animation.  Keep capturing for stopGraceMillis after the first "not allowed"
    // signal so the animation tail is still captured; the frame is discarded by
    // generation check once a real stop lands.
    private long stopGraceMillis = 150L;
    private long lastAllowedNanos = Long.MAX_VALUE;
    private final Runnable cancelGrace = new Runnable() {
        @Override public void run() { cancelPendingCaptureWork(); }
    };

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
    private boolean observedRecentsVisible;
    private int observedRecentsScrollX;
    private int observedRecentsScrollY;
    private int observedRecentsTranslationX;
    private int observedRecentsTranslationY;

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
                // One trailing, coalesced frame only.  Capture is event-driven: it only
                // runs when the Dock itself moves (observation/state change), never on a
                // free-running cadence, so a static Dock costs zero captures.
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
                        float refractionAmount, float chromaticAberration,
                        int tintAlpha, boolean squircle, float squircleCp,
                        int captureFps) {
        super(geometrySource.getContext());
        this.geometrySource = geometrySource;
        this.workspace = workspace;
        this.blurRadius = Math.max(0, blurRadius);
        this.refractionAmount = refractionAmount;
        this.chromaticAberration = chromaticAberration;
        int fps = Math.max(5, Math.min(165, captureFps));
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
        applyRoundedOutline();
    }

    void setGlassGeometry(float radius, boolean useSquircle, float cp) {
        cornerRadius = Math.max(0f, radius);
        squircle = useSquircle;
        squircleCp = cp;
        applyRoundedOutline();
        invalidate();
    }

    void setGlassRadius(float radius) {
        cornerRadius = Math.max(0f, radius);
        applyRoundedOutline();
        invalidate();
    }

    /** Give the RenderNode a rounded outline so SurfaceFlinger's self-blur follows the
     *  glass shape instead of blurring a rectangle (blur-behind honours the outline). */
    private void applyRoundedOutline() {
        try {
            setClipToOutline(true);
            setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(android.view.View view, android.graphics.Outline outline) {
                    float r = Math.max(0f, cornerRadius);
                    int w = view.getWidth(), h = view.getHeight();
                    if (w <= 0 || h <= 0) {
                        outline.setRect(0, 0, 1, 1);
                        return;
                    }
                    // Squircle and rounded-rect both approximate as a rounded rect for the
                    // blur region (the outline only drives the SurfaceFlinger blur mask).
                    outline.setRoundRect(0, 0, w, h, r);
                }
            });
        } catch (Throwable e) {
            Log.w(TAG, "rounded outline failed: " + e);
        }
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
            mainHandler.removeCallbacks(cancelGrace);
            observationValid = false;
            requestStateCapture("window-visible");
        } else {
            // Defer the hard cancel by the stop-grace period so a collapse/hide animation
            // tail is still captured instead of freezing mid-animation.
            mainHandler.removeCallbacks(cancelGrace);
            if (stopGraceMillis > 0) {
                Log.i(TAG, "Liquid capture window hidden; grace " + stopGraceMillis + "ms before stop");
                mainHandler.postDelayed(cancelGrace, stopGraceMillis);
            } else {
                cancelPendingCaptureWork();
            }
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
        // Guard the hidden native background: dock animations may reset the background's
        // alpha back to 1, which would show the default background through/over the glass.
        // Re-hide it every frame while the glass owns the background.
        if (nativeBackgroundHiddenByGlass && geometrySource.getAlpha() != 0f) {
            geometrySource.setAlpha(0f);
        }
        if (updateObservation()) {
            requestStateCapture("observation");
        }
        return true;
    }

    /**
     * Cheap state polling only; this never captures by itself when all tracked values are static.
     * Tracks the Dock's own geometry (position/size/scale/translation) plus display
     * rotation/size, and — while the recents/multitasking panel is VISIBLE — the recents
     * view's scroll/translation, so task-card animations keep the glass live even though the
     * Dock itself has stopped moving.  Normal home-screen page swipes (recents hidden) do
     * NOT trigger captures.
     */
    private boolean updateObservation() {
        Display display = geometrySource.getDisplay();
        if (display == null) return false;
        display.getRealSize(tmpDisplaySize);
        geometrySource.getLocationOnScreen(tmpDockLocation);

        int rotation = display.getRotation();
        int dockW = geometrySource.getWidth();
        int dockH = geometrySource.getHeight();
        int dockTx = Float.floatToIntBits(geometrySource.getTranslationX());
        int dockTy = Float.floatToIntBits(geometrySource.getTranslationY());
        int dockSx = Float.floatToIntBits(geometrySource.getScaleX());
        int dockSy = Float.floatToIntBits(geometrySource.getScaleY());

        boolean recentsVisible = false;
        int recentsScrollX = 0, recentsScrollY = 0, recentsTx = 0, recentsTy = 0;
        View rec = recentsView;
        if (rec != null && rec.getVisibility() == View.VISIBLE) {
            recentsVisible = true;
            recentsScrollX = rec.getScrollX();
            recentsScrollY = rec.getScrollY();
            recentsTx = Float.floatToIntBits(rec.getTranslationX());
            recentsTy = Float.floatToIntBits(rec.getTranslationY());
        }

        boolean changed = dockDragging   // icon drag: keep capturing through the rearrange
                || !observationValid
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
                || recentsVisible != observedRecentsVisible
                || (recentsVisible && (
                        recentsScrollX != observedRecentsScrollX
                        || recentsScrollY != observedRecentsScrollY
                        || recentsTx != observedRecentsTranslationX
                        || recentsTy != observedRecentsTranslationY));

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
        observedRecentsVisible = recentsVisible;
        observedRecentsScrollX = recentsScrollX;
        observedRecentsScrollY = recentsScrollY;
        observedRecentsTranslationX = recentsTx;
        observedRecentsTranslationY = recentsTy;
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
                            try {
                                java.lang.reflect.Field nameField = sc.getClass().getDeclaredField("mName");
                                nameField.setAccessible(true);
                                Object nm = nameField.get(sc);
                                if (nm instanceof String) dockWindowLayerName = (String) nm;
                            } catch (Throwable ignored) {
                            }
                            Log.i(TAG, "Liquid capture dock window surface resolved from root["
                                    + list.indexOf(root) + "] type=" + lp.type
                                    + " title=" + lp.getTitle() + " sc=" + sc
                                    + " layerName=" + dockWindowLayerName);
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

    /** Recents/multitasking view to watch for background motion (set via reflection by
     *  MainHook from Launcher.getRecentsView()).  Null disables the extra observation. */
    void setRecentsView(View view) {
        recentsView = view;
    }

    /** A touch event on the Dock area (MainHook's touch listener on the Dock window root).
     *  Any touch — tap, hover before an up-swipe, drag — means the user is interacting with
     *  the Dock, so refresh the glass even if the Dock geometry has not moved yet.  Simple
     *  additive trigger: it coexists with the observation-driven captures and the normal
     *  capture-rate limiter still applies. */
    void onDockTouchEvent() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::onDockTouchEvent);
            return;
        }
        requestStateCapture("dock-touch");
    }

    /** Coordinate test: is this screen point inside (or near) the Dock window area?
     *  MainHook uses this from the launcher window's touch listener, so touches that the
     *  system dispatches elsewhere (e.g. the drag surface during an icon drag) still count
     *  as Dock-area interaction as long as the finger stays near the Dock. */
    boolean isTouchInDockArea(float rawX, float rawY) {
        geometrySource.getLocationOnScreen(tmpDockLocation);
        int w = geometrySource.getWidth();
        int h = geometrySource.getHeight();
        if (w <= 0 || h <= 0) return false;
        int margin = (int) Math.max(80f, getResources().getDisplayMetrics().density * 40f);
        return rawX >= tmpDockLocation[0] - margin
                && rawX <= tmpDockLocation[0] + w + margin
                && rawY >= tmpDockLocation[1] - margin
                && rawY <= tmpDockLocation[1] + h + margin;
    }

    /** Dock icon drag state (MainHook hooks DragController.startDrag/endDrag).  While
     *  dragging, the glass keeps capturing continuously so the background follows the icon
     *  rearrangement; the drag surface layer is excluded from captures. */
    void setDockDragging(boolean dragging, String dragSurfaceLayerName) {
        dockDragging = dragging;
        dragLayerName = dragging ? dragSurfaceLayerName : null;
        if (dragging) {
            observationValid = false;
            requestStateCapture("drag-start");
        }
    }

    /** Configurable by the GUI (liquid_capture_stop_delay, up to 10s). */
    void setStopGraceMillis(int millis) {
        stopGraceMillis = Math.max(0, Math.min(10000, millis));
    }

    /** Configurable by the GUI (liquid_capture_bleed_top / _bottom): extra capture height
     *  above and below the glass.  -1 (or unset) = half the horizontal bleed. */
    void setBleedVerticalPx(int topPx, int bottomPx) {
        bleedTopPx = Math.max(0, Math.min(512, topPx));
        bleedBottomPx = Math.max(0, Math.min(512, bottomPx));
    }

    /** Prismal liquid-glass parameters (GUI-configurable; values are dp where noted,
     *  converted to px at render time using the display density). */
    void setPrismalParams(float thicknessDp, float ior, float normalStrength,
                          float liquidDome, float lensRefractionDp, float refractionInsetDp) {
        glassThickness = thicknessDp;
        glassIor = ior;
        glassNormalStrength = normalStrength;
        glassLiquidDome = liquidDome;
        glassLensRefraction = lensRefractionDp;
        glassRefractionInset = refractionInsetDp;
        invalidate();
    }

    /** Compositor readback scale (GUI: liquid_capture_scale, 10-100%).  Lower = cheaper
     *  captures; the glass is blurred anyway so refraction stays visually lossless. */
    void setCaptureScale(float scale) {
        captureScale = Math.max(0.1f, Math.min(1.0f, scale));
    }

    /** Edge-highlight thickness multiplier (GUI: liquid_highlight_width, 20-300%).
     *  Scales both the shader's edge-glow band and the canvas stroke highlight. */
    void setHighlightWidth(float multiplier) {
        glassHighlightWidth = Math.max(0.2f, Math.min(3.0f, multiplier));
        invalidate();
    }

    /** Glass tint RGB (GUI: liquid_tint_r/g/b, 0-255).  Alpha is liquid_tint_alpha. */
    void setTintColor(int r, int g, int b) {
        glassTintR = Math.max(0, Math.min(255, r));
        glassTintG = Math.max(0, Math.min(255, g));
        glassTintB = Math.max(0, Math.min(255, b));
        invalidate();
    }

    /** Shader appearance knobs (GUI: liquid_depth_effect / liquid_brightness /
     *  liquid_specular_sharp / liquid_specular_strength / liquid_rim_light /
     *  liquid_caustics / liquid_edge_band). */
    void setAppearance(float depthEffect, float brightness, float specularSharp,
                       float specularStrength, float rimLight, float caustics, float edgeBand) {
        glassDepthEffect = Math.max(0f, Math.min(1f, depthEffect));
        glassBrightness = Math.max(0.5f, Math.min(2f, brightness));
        glassSpecularSharp = Math.max(1f, Math.min(400f, specularSharp));
        glassSpecularStrength = Math.max(0f, Math.min(5f, specularStrength));
        glassRimLight = Math.max(0f, Math.min(3f, rimLight));
        glassCaustics = Math.max(0f, Math.min(1f, caustics));
        glassEdgeBand = Math.max(0.005f, Math.min(0.1f, edgeBand));
        invalidate();
    }

    /** Canvas stroke highlight opacity multiplier (GUI: liquid_highlight_alpha). */
    void setHighlightAlpha(float multiplier) {
        glassHighlightAlpha = Math.max(0f, Math.min(2f, multiplier));
        invalidate();
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
        //
        // Stop grace: during collapse/hide animations the window briefly reports
        // !isShown()/!windowVisible while the Dock is still on screen.  Treat a short
        // "not allowed" window as still allowed (lastAllowedNanos within stopGraceMillis)
        // so the animation tail keeps being captured instead of freezing mid-frame.
        // Dock icon drag in flight: keep capturing so the background follows the icon
        // rearrangement (the drag surface layer is excluded from captures).
        if (dockDragging) {
            lastAllowedNanos = System.nanoTime();
            return true;
        }
        boolean baseAllowed = attached && windowVisible && isShown();
        if (baseAllowed) {
            lastAllowedNanos = System.nanoTime();
            return true;
        }
        if (stopGraceMillis <= 0) return false;
        long graceEndNanos = lastAllowedNanos + stopGraceMillis * 1_000_000L;
        if (System.nanoTime() <= graceEndNanos) return true;
        return false;
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
        // The Dock window's SF layer handle changes every time the window is recreated
        // (dock show/hide, launcher restart).  Re-resolve the current handle before each
        // capture so the exclusion never goes stale.
        dockWindowSurface = resolveWindowSurfaceControl();
        Log.i(TAG, (useFullscreen ? "fullscreen" : "captureMode(2)") + " attempt display=" + request.displayId
                + " strip=" + request.stripRect + " tile=" + request.tileRect
                + " scale=" + captureScale + " exclude=" + (dockWindowSurface != null));

        worker.post(() -> {
            Bitmap strip = null;
            CroppedFrame cropped = null;
            Throwable failure = null;
            try {
                LiveScreenCapture client = liveCapture;
                if (client == null) {
                    client = new LiveScreenCapture(
                            captureScale, geometrySource.getContext().getClassLoader());
                    liveCapture = client;
                }
                if (useFullscreen) {
                    android.view.SurfaceControl[] excludes = null;
                    if (dockWindowSurface != null) {
                        excludes = new android.view.SurfaceControl[]{dockWindowSurface};
                    }
                    // Async path: submit and return; the result arrives on the SF callback
                    // thread, so this worker thread is free to service the next request
                    // immediately (no blocking wait inside getBuffer()).
                    final CaptureRequest req = request;
                    // On the home screen the glass sits over the wallpaper: use MIUI's
                    // wallpaper-only capture mode (captureMode=2, same as HyperOS Home's own
                    // capture path) — fast AND inherently icon/dock-free.  Over an app the
                    // glass refracts the app content, so fall back to full-display capture
                    // with the Dock + drag-surface layers excluded (mode 1).
                    boolean wallpaperMode = launcherResumed && launcherLifecycleKnown;
                    String[] excludeNames = null;
                    if (!wallpaperMode) {
                        excludeNames = dockWindowLayerName != null
                                ? new String[]{dockWindowLayerName, dragLayerName}
                                : (dragLayerName != null ? new String[]{dragLayerName} : null);
                    }
                    Log.i(TAG, "capture mode=" + (wallpaperMode ? 2 : 1)
                            + " names=" + java.util.Arrays.toString(
                                    wallpaperMode ? new String[]{"Wallpaper BBQ wrapper"} : excludeNames)
                            + " crop=" + req.stripRect + " scale=" + captureScale);
                    client.captureScreenAsync(req.stripRect, captureScale, req.displayId,
                            wallpaperMode ? null : excludes, excludeNames,
                            wallpaperMode ? 2 : 1,
                            new LiveScreenCapture.CaptureCallback() {
                                @Override public void onResult(Bitmap bmp) {
                                    handleCaptureResult(bmp, req, generation);
                                }
                                @Override public void onError(Throwable error) {
                                    liveCapture = null;
                                    mainHandler.post(() -> {
                                        if (generation != captureGeneration) return;
                                        capturing = false;
                                        Log.e(TAG, "async fullscreen capture failed", error);
                                        if (sourceDirty) requestStateCapture();
                                    });
                                }
                            });
                    return; // async path owns completion via handleCaptureResult
                } else {
                    strip = client.captureWallpaper(request.stripRect, captureScale, request.displayId);
                    if (strip != null) {
                        cropped = cropWallpaperTile(strip, request.stripRect,
                                request.tileRect, request.dockRect);
                        strip = null; // cropWallpaperTile owns/recycles it.
                    }
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

                // Autonomous cadence in captureKick drives the next frame; this catches any
                // state change that arrived while the capture was in flight.
                if (sourceDirty) requestStateCapture();
            });
        });
    }

    /** Shared completion path for async captures: crop on the SF callback thread, install
     *  on the main thread. */
    private void handleCaptureResult(Bitmap strip, CaptureRequest request, long generation) {
        try {
            CroppedFrame cropped = cropWallpaperTile(strip, request.stripRect,
                    request.tileRect, request.dockRect);
            strip = null; // cropWallpaperTile owns/recycles it.
            final CroppedFrame frame = cropped;
            mainHandler.post(() -> {
                if (generation != captureGeneration || !isCaptureAllowed()) {
                    if (frame != null) frame.recycle();
                    Log.i(TAG, "Liquid async capture result discarded: generation="
                            + (generation == captureGeneration)
                            + " allowed=" + isCaptureAllowed());
                    return;
                }
                capturing = false;
                nullFrameLogged = false;
                installCapture(frame);
                if (sourceDirty) requestStateCapture();
            });
        } catch (Throwable e) {
            if (strip != null && !strip.isRecycled()) strip.recycle();
            mainHandler.post(() -> {
                if (generation != captureGeneration) return;
                capturing = false;
                Log.e(TAG, "async capture crop failed", e);
                if (sourceDirty) requestStateCapture();
            });
        }
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
        // Horizontal bleed covers the refraction displacement.  Top/bottom are independent
        // GUI knobs (liquid_capture_bleed_top / _bottom): the Dock sits at a fixed distance
        // from the screen bottom, so the bottom bleed can stay small to keep the
        // home-indicator / boundary lines out of the captured background.
        int defaultBleedV = Math.max(8, captureBleedPx / 2);
        int bleedTop = bleedTopPx >= 0 ? bleedTopPx : defaultBleedV;
        int bleedBottom = bleedBottomPx >= 0 ? bleedBottomPx : defaultBleedV;
        tileRect.inset(-captureBleedPx, -bleedTop);
        tileRect.bottom += bleedBottom;
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
            // Blur is now done in-shader (Prismal-style Gaussian in blurred()); the MIUI
            // system self-blur pass is disabled because it double-blurs and its quality
            // was reported poor.
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
        refraction.setFloatUniform("depthEffect", glassDepthEffect);
        refraction.setFloatUniform("chromaticAberration", chromaticAberration);
        refraction.setFloatUniform("blurRadius", blurRadius);
        // Prismal liquid-glass model parameters (ported from styropyr0/Prismal);
        // GUI-configurable via liquid_* settings.
        float density = getResources().getDisplayMetrics().density;
        refraction.setFloatUniform("thickness", Math.max(1f, glassThickness * density));
        refraction.setFloatUniform("ior", Math.max(1.001f, Math.min(2f, glassIor)));
        refraction.setFloatUniform("normalStrength", Math.max(0f, Math.min(5f, glassNormalStrength)));
        refraction.setFloatUniform("liquidDome", Math.max(0f, Math.min(2f, glassLiquidDome)));
        refraction.setFloatUniform("lensRefractionPx", Math.max(0f, glassLensRefraction * density));
        refraction.setFloatUniform("refractionInset", Math.max(0f, glassRefractionInset * density));
        refraction.setFloatUniform("highlightWidth", glassHighlightWidth);
        refraction.setFloatUniform("brightness", glassBrightness);
        refraction.setFloatUniform("specularSharp", glassSpecularSharp);
        refraction.setFloatUniform("specularStrength", glassSpecularStrength);
        refraction.setFloatUniform("rimLight", glassRimLight);
        refraction.setFloatUniform("causticStrength", glassCaustics);
        refraction.setFloatUniform("edgeBand", glassEdgeBand);
        refraction.setFloatUniform("screenOffset", captureSampleOffsetX, captureSampleOffsetY);
        refraction.setFloatUniform("captureScale",
                capture.getWidth() / Math.max(1f, captureSourceWidth),
                capture.getHeight() / Math.max(1f, captureSourceHeight));
        glassPaint.setShader(refraction);
        Path shape = shapePath(getWidth(), getHeight(), cornerRadius);
        canvas.save();
        canvas.clipPath(shape);
        canvas.drawRect(0, 0, getWidth(), getHeight(), glassPaint);
        tintPaint.setColor(Color.argb(tintPaint.getAlpha(),
                glassTintR, glassTintG, glassTintB));
        canvas.drawPath(shape, tintPaint);
        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setStrokeWidth(Math.max(1f,
                getResources().getDisplayMetrics().density * .65f * glassHighlightWidth));
        highlightPaint.setShader(new LinearGradient(0, 0, getWidth(), getHeight(),
            new int[]{Color.argb((int)(175 * glassHighlightAlpha), 255, 255, 255),
                      Color.argb((int)(25 * glassHighlightAlpha), 255, 255, 255),
                      Color.argb((int)(105 * glassHighlightAlpha), 255, 255, 255)},
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
