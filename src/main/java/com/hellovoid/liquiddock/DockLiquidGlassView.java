package com.hellovoid.liquiddock;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.Configuration;
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
import android.os.PowerManager;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.core.graphics.BitmapCompat;

/**
 * Event-driven liquid-glass renderer for the Launcher Dock.
 *
 * Backdrop sources: wallpaper-only display capture (vendor captureMode 2) on the home
 * screen, full-display capture with Dock-layer exclusion over apps.  Captures are
 * one-shot and coalesced; unchanged pre-draws do not request new frames.
 */
final class DockLiquidGlassView extends View implements ViewTreeObserver.OnPreDrawListener {
    private static final String TAG = "LiquidDock";
    private static final long CONFIG_VISIBLE_RELOAD_MS = 1000L;
    private static final long CONFIG_DISABLED_RELOAD_MS = 5000L;
    private static final long VISIBILITY_CACHE_NS = 100_000_000L;
    private static final int CAPTURE_TIMEOUT_BREAKER_LIMIT = 4;
    private static final long[] CAPTURE_TIMEOUT_BACKOFF_MS = {0L, 250L, 750L};

    /** Debug logs gated by the master switch (MainHook.debugLogging); also
     *  appended to Download/liquiddock.log by MainHook.fileLog. */
    private static void logI(String m) { if (MainHook.debugLogging) { Log.i(TAG, m); MainHook.log(m); } }
    private static void logW(String m) { if (MainHook.debugLogging) { Log.w(TAG, m); MainHook.log(m); } }
    private static void logW(String m, Throwable t) { if (MainHook.debugLogging) { Log.w(TAG, m, t); MainHook.log(m + " " + t); } }
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
      + "uniform float depthEffect;"
      + "uniform float chromaticAberration;"
      + "uniform float blurRadius;"
      + "uniform float shaderBlurEnabled;"
      + "uniform float2 screenOffset;"
      + "uniform float2 captureScale;"
      + "uniform float thickness;"
      + "uniform float ior;"
      + "uniform float normalStrength;"
      + "uniform float liquidDome;"
      + "uniform float lensRefractionPx;"
      + "uniform float highlightWidth;"
      + "uniform float highlightEnabled;"
      + "uniform float highlightAlpha;"
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
      + "uniform float2 captureSize;"
      + "half4 source(float2 p){"
      + "float2 c=(p+screenOffset)*captureScale;"
      + "float2 hi=max(captureSize-float2(1.0),float2(1.0));"
      + "float2 period=hi*2.0;"
      + "c=mod(mod(c,period)+period,period);"
      + "c=min(c,period-c);"
      + "return content.eval(clamp(c,float2(0.0),hi));}"
      + "half4 blurred(float2 p){"
      + "if(shaderBlurEnabled < 0.5 || blurRadius<=0.5){return source(p);}"
      + "float r=max(blurRadius,1.0);"
      + "half4 col=source(p)*0.256;"
      // 5-ring octagonal kernel (40 samples) — ghost-free up to blurRadius 60.
      + "float2 r1=float2(1.0,0.0)*r*0.16;float2 r1d=float2(0.7071,0.7071)*r*0.16;"
      + "float2 r2=float2(1.0,0.0)*r*0.34;float2 r2d=float2(0.7071,0.7071)*r*0.34;"
      + "float2 r3=float2(1.0,0.0)*r*0.54;float2 r3d=float2(0.7071,0.7071)*r*0.54;"
      + "float2 r4=float2(1.0,0.0)*r*0.74;float2 r4d=float2(0.7071,0.7071)*r*0.74;"
      + "float2 r5=float2(1.0,0.0)*r*0.90;float2 r5d=float2(0.7071,0.7071)*r*0.90;"
      // ring 1
      + "col+=source(p+r1)*0.045;col+=source(p-r1)*0.045;"
      + "col+=source(p+float2(0.0,r1.x))*0.045;col+=source(p-float2(0.0,r1.x))*0.045;"
      + "col+=source(p+r1d)*0.045;col+=source(p-r1d)*0.045;"
      + "col+=source(p+float2(-r1d.x,r1d.y))*0.045;col+=source(p-float2(-r1d.x,r1d.y))*0.045;"
      // ring 2
      + "col+=source(p+r2)*0.028;col+=source(p-r2)*0.028;"
      + "col+=source(p+float2(0.0,r2.x))*0.028;col+=source(p-float2(0.0,r2.x))*0.028;"
      + "col+=source(p+r2d)*0.028;col+=source(p-r2d)*0.028;"
      + "col+=source(p+float2(-r2d.x,r2d.y))*0.028;col+=source(p-float2(-r2d.x,r2d.y))*0.028;"
      // ring 3
      + "col+=source(p+r3)*0.013;col+=source(p-r3)*0.013;"
      + "col+=source(p+float2(0.0,r3.x))*0.013;col+=source(p-float2(0.0,r3.x))*0.013;"
      + "col+=source(p+r3d)*0.013;col+=source(p-r3d)*0.013;"
      + "col+=source(p+float2(-r3d.x,r3d.y))*0.013;col+=source(p-float2(-r3d.x,r3d.y))*0.013;"
      // ring 4
      + "col+=source(p+r4)*0.005;col+=source(p-r4)*0.005;"
      + "col+=source(p+float2(0.0,r4.x))*0.005;col+=source(p-float2(0.0,r4.x))*0.005;"
      + "col+=source(p+r4d)*0.005;col+=source(p-r4d)*0.005;"
      + "col+=source(p+float2(-r4d.x,r4d.y))*0.005;col+=source(p-float2(-r4d.x,r4d.y))*0.005;"
      // ring 5
      + "col+=source(p+r5)*0.002;col+=source(p-r5)*0.002;"
      + "col+=source(p+float2(0.0,r5.x))*0.002;col+=source(p-float2(0.0,r5.x))*0.002;"
      + "col+=source(p+r5d)*0.002;col+=source(p-r5d)*0.002;"
      + "col+=source(p+float2(-r5d.x,r5d.y))*0.002;col+=source(p-float2(-r5d.x,r5d.y))*0.002;"
      + "return half4(clamp(col.rgb,0.0,1.0),1.0);}"
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
      + "float bandFracR=max(edgeBand,0.005);"
      + "float bandR=clamp(minDim*bandFracR,0.5,min(12.0,minDim*0.1));"
      + "float shellRim=smoothstep(bandR,bandR*0.06,edgeDist)*smoothstep(-2.2,0.0,sd);"
      + "float2 cn=cc/max(hs,float2(1.0));"
      + "float2 Lxy=normalize(float2(-0.5,-0.8)+float2(1e-5));"
      + "float2 gN=normalize(gradLens+float2(1e-4));"
      + "float edgeLight=dot(gN,Lxy);"
      + "float rimLitSide=pow(max(edgeLight,0.0),3.6)*shellRim*1.22*0.95*rimLight*(0.58+0.42*height);"
      + "float rimOpposite=pow(max(-edgeLight,0.0),1.05)*shellRim*1.22*0.4*rimLight*(0.4+0.6*height);"
      + "float causticDot=dot(normalize(float3(gradH*normalStrength,0.45)),Lp);"
      + "float caust=pow(max(causticDot,0.0),7.0)*max(causticStrength,0.0)*height;"
      + "float3 hl=specP*float3(0.99,0.993,1.0)"
      + "+float3(0.98,0.992,1.008)*rimLitSide"
      + "+float3(0.952,0.968,1.018)*rimOpposite"
      + "+caust*float3(1.0,0.96,0.90);"
      + "if(highlightEnabled>0.5){color+=hl*highlightAlpha;}"
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
    private int blurRadius;
    interface ActiveBlurBackendListener {
        void onActiveBlurBackendChanged(LiquidBlurMode mode);
    }

    private LiquidBlurMode requestedBlurMode = LiquidBlurMode.SHADER;
    private LiquidBlurMode activeBlurBackend = LiquidBlurMode.SHADER;
    private ActiveBlurBackendListener activeBlurBackendListener;
    private boolean advancedMaterialActive;
    private boolean advancedMaterialUnavailableForProcess;
    private boolean fullscreenCapture = true;
    private final float chromaticAberration;
    // Prismal liquid-glass parameters (GUI-configurable)
    private float glassThickness = 18f;      // liquid_thickness (dp -> px at render)
    private float glassIor = 1.55f;          // liquid_ior
    private float glassNormalStrength = 1.15f; // liquid_normal_strength
    private float glassLiquidDome = 1.0f;    // liquid_dome
    private float glassLensRefraction = 12f; // liquid_lens_refraction (dp -> px)
    // Edge highlight thickness multiplier (liquid_highlight_width): scales the shader's
    // edge-glow band (silW) AND the canvas stroke highlight width.
    private float glassHighlightWidth = 1.0f;
    private float glassHighlightAlpha = 1.0f;
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
    // True while a Dock icon drag is in flight (MainHook hooks DragController.startDrag/
    // endDrag).  During a drag the glass keeps capturing so the background follows the icon
    // re-arrangement, and the drag surface layer is excluded so the floating icon never
    // freezes into the captured background.
    private volatile boolean dockDragging = false;
    private volatile String dragLayerName = null;
    private final CaptureCadence captureCadence;
    private boolean dynamicAppCapture;
    private long dynamicAppActiveUntilNanos;
    private long lastAppVisualSignature;
    private boolean appVisualSignatureValid;
    private int dynamicMotionDifferenceThreshold = 12;
    private int dynamicMotionBitThreshold = 18;
    private long dynamicMotionHoldNanos = 900_000_000L;
    private int blackFrameThreshold = 10;
    private int captureBleedPx;
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
    private final Rect tmpGlassVisibleRect = new Rect();
     private final PowerManager powerManager;
    private final float displayDensity;
    private boolean runtimeGlassEnabled = true;
    private boolean configReloadScheduled;
    private boolean glassVisibilityDirty = true;
    private long glassVisibilityCheckedNanos;
    private boolean cachedGlassActuallyVisible;
    // Authoritative Overview latch. HyperOS keeps mOverviewPanel attached/VISIBLE on HOME
    // on some builds, so View visibility must never start the self-sustained Recents loop.
    private boolean overviewActive;
    private int captureTimeoutStreak;
    private int captureFailureStreak;
    private int captureFailureRetryToken;
    private static final long[] CAPTURE_FAILURE_RETRY_DELAYS_MS = {0L, 100L, 300L, 800L};
    private boolean captureCircuitOpen;
    // Scene provenance of the bitmap currently installed into captureShader. Async request
    // validation prevents stale callbacks, but without this marker an already-installed HOME
    // wallpaper frame can survive into APP until the first mode-1 callback arrives.
    private CaptureScene installedCaptureScene;
    // APP focus is known before the floating Dock becomes visible. Keep a short, bounded
    // hidden-capture window so an APP frame is already installed before the first reveal
    // pixel; normal hidden APP capture remains forbidden outside this pre-arm window.
    private boolean appBackdropPrearmActive;
    private int appBackdropPrearmToken;
    private static final long[] APP_BACKDROP_PREARM_DELAYS_MS = {0L, 120L, 320L};
    private static final long APP_BACKDROP_PREARM_WINDOW_MS = 850L;

    private Bitmap capture;
    private BitmapShader captureShader;
    private final Path cachedDrawShape = new Path();
    private int cachedDrawShapeW = -1, cachedDrawShapeH = -1;
    private int cachedDrawRadiusBits, cachedDrawCpBits;
    private boolean cachedDrawSquircle;
    private float captureSampleOffsetX;
    private float captureSampleOffsetY;
    private float captureSourceWidth = 1f;
    private float captureSourceHeight = 1f;

    private HandlerThread captureThread;
    private Handler captureHandler;
    private int captureWorkerId;
    // Per-attempt identity: distinguishes "this one Binder request timed out / was
    // superseded" (attempt) from "the whole capture context is stale" (generation).
    // A wedged captureDisplay() call only invalidates its own attempt, never the
    // rotation/scene generation.
    private long captureAttemptSeq;
    private volatile long activeCaptureAttempt;
    private Runnable captureTimeout;
    private volatile LiveScreenCapture liveCapture;
    private final SurfaceLayerNameResolver surfaceLayerNameResolver;
    private final FreeformLayerResolver freeformLayerResolver;
    private ViewTreeObserver observedTree;

    private boolean attached;
    private boolean launcherResumed;
    private boolean launcherLifecycleKnown;
    // Set by onLauncherFocusLost() when the launcher window genuinely loses focus.
    // Consumed by onLauncherFocused() to distinguish HOME return (delay capture)
    // from a HOME-local Dock spring-back (keep live rendering).
    private boolean launcherWasAway;
    // APP -> HOME is a two-phase handoff: authority/barrier changes immediately, while a
    // fresh vendor mode-2 wallpaper readback waits for the Launcher wallpaper wrapper to settle.
    private long homeWallpaperSettleUntilNanos;
    private int homeWallpaperSettleToken;
    private long homeWallpaperCaptureEpoch;
    private boolean windowVisible;
    private boolean windowFocused;
    private boolean systemUiPanelExpanded;
    private android.view.SurfaceControl dockWindowSurface;
    private String dockWindowLayerName;
    private final DockExcludeRecovery dockExcludeRecovery = new DockExcludeRecovery();
    private boolean moduleSettingsForeground;
    private boolean capturing;
    private boolean sourceDirty;
    private boolean nullFrameLogged;
    private int drawFailLogged;
    private int blackFrameLogCount;
    private boolean nativeBackgroundHiddenByGlass;
    private boolean kickScheduled;
    private long captureGeneration;
    private final CaptureSceneState sceneState = new CaptureSceneState();
    private float gestureDownRawY = Float.NaN;
    private float recentsPrearmDistancePx;
    private boolean recentsPrearmed;
    private int recentsPrearmToken;
    private int allAppsPrearmToken;
    // Workstation owns a separate Dock background. It remains static/wallpaper-backed;
    // this normal LiquidDock view is activated only for the exact Recents button path.
    private boolean workstationMode;
    private boolean workstationRecentsActive;
    private boolean workstationRecentsWasVisible;
    private int workstationRecentsSessionToken;
    private final WorkstationCaptureBurst workstationCaptureBurst = new WorkstationCaptureBurst();
    private boolean workstationSuspendWhenBurstSettles;
    private long lastCaptureStartNanos;
    // Wallpaper strip cache: mode-2 (wallpaper-only) capture is skipped entirely while
    // the wallpaper is unchanged — the strip is static, so one capture is enough.  The
    // cached strip is cropped per request; it is invalidated on rotation, display-size
    // change, wallpaper ID change, or a taller strip request (Dock higher on screen).
    private Bitmap wallpaperStripCache;
    private Rect cacheStripRect;
    private int cacheRotation = -1;
    private int cacheDisplayWidth = -1;
    private int cacheDisplayHeight = -1;
    private int cacheWallpaperId = -1;
    private final Object wallpaperCacheLock = new Object();
    private volatile long wallpaperTransformRevision;
    private long cacheWallpaperTransformRevision = -1L;
    // Rotation barrier: cache is only served after the CURRENT orientation produced a
    // real, non-black SF frame that passed the stale checks and was installed.  Cleared
    // on every configuration change — a stale strip from the previous orientation must
    // never be served for the new one.
    private volatile boolean wallpaperCacheReady;
    // HyperOS can return status=0 + a valid-sized but pure-black wallpaper buffer for a
    // short period after display rotation.  Retry those transient frames autonomously
    // instead of waiting for another Dock/layout event to dirty the capture state.
    private static final long[] BLACK_FRAME_RETRY_DELAYS_MS =
            {80L, 120L, 180L, 260L, 400L, 600L};
    private int blackFrameRetryCount;
    private int blackFrameRetryRotation = -1;

    // Rotation stabilization: after an orientation change the wallpaper layer settles
    // only after the rotation animation + re-render complete.  The black-frame guard
    // alone is insufficient — transitional frames with black edges (band0/band3 ~0)
    // have a normal mean and get installed, freezing the transition on screen.  Keep
    // capturing every ROTATION_STABILIZE_INTERVAL_MS until two consecutive frames
    // have identical content signatures (the wallpaper is static, so a stable frame
    // repeats bit-for-bit), or the window expires.
    private static final long ROTATION_STABILIZE_WINDOW_MS = 3000L;
    private static final long ROTATION_STABILIZE_INTERVAL_MS = 300L;
    private long rotationStabilizeUntilNanos;
    private long lastRotationSignature = -1;
    private boolean rotationStabilizeTickPending;

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
            updateDesiredScene();
            if (isHomeWallpaperSettleActive(sceneState.desired())) {
                sourceDirty = true;
                logI("capture-kick deferred by HOME wallpaper settle");
                return;
            }
            if (capturing || !sourceDirty) return;

            long now = System.nanoTime();
            long interval = captureCadence.intervalNanos(sceneState.desired(), dynamicAppCapture,
                    dynamicAppActiveUntilNanos, now);
            long remaining = lastCaptureStartNanos == 0L
                    ? 0L : interval - (now - lastCaptureStartNanos);
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
                    beginObservationBurst();
                    requestStateCapture();
                }
            };

    DockLiquidGlassView(View geometrySource, View workspace, int blurRadius,
                        float chromaticAberration,
                        int tintAlpha, boolean squircle, float squircleCp,
                        int captureFps) {
        super(geometrySource.getContext());
        this.geometrySource = geometrySource;
        this.workspace = workspace;
        this.blurRadius = Math.max(0, blurRadius);
        this.recentsPrearmDistancePx = geometrySource.getResources()
                .getDisplayMetrics().density * 8f;
        this.chromaticAberration = chromaticAberration;
        this.captureCadence = new CaptureCadence(captureFps);
        this.surfaceLayerNameResolver = new SurfaceLayerNameResolver();
        this.freeformLayerResolver = new FreeformLayerResolver(
                getContext(), surfaceLayerNameResolver);
        this.powerManager = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
        this.displayDensity = getResources().getDisplayMetrics().density;

        float density = getResources().getDisplayMetrics().density;
        float displacement = this.blurRadius * .5f * (1f + Math.abs(chromaticAberration));
        captureBleedPx = Math.max(8, Math.min(512,
                (int) Math.ceil(this.blurRadius + displacement + 8f * density)));

        this.squircle = squircle;
        this.squircleCp = squircleCp;
        refraction = new RuntimeShader(REFRACTION_SHADER);
        tintPaint.setColor(Color.argb(Math.max(0, Math.min(255, tintAlpha)), 238, 244, 255));
        setWillNotDraw(false);
        setClipToOutline(false);
    }

    void setGlassGeometry(float radius, boolean useSquircle, float cp) {
        cornerRadius = Math.max(0f, radius);
        squircle = useSquircle;
        squircleCp = cp;
        // Final shape clipping is owned by DockLiquidGlassHostView. The glass RenderNode
        // stays rectangular so SurfaceFlinger self-blur receives corner source pixels.
        setClipToOutline(false);
        invalidate();
    }

    void setGlassRadius(float radius) {
        cornerRadius = Math.max(0f, radius);
        setClipToOutline(false);
        invalidate();
    }

    void setBlurMode(LiquidBlurMode mode) {
        LiquidBlurMode next = mode == null ? LiquidBlurMode.SHADER : mode;
        if (requestedBlurMode == next
                && (next != LiquidBlurMode.ADVANCED_MATERIAL
                    || advancedMaterialActive || advancedMaterialUnavailableForProcess)) {
            return;
        }
        requestedBlurMode = next;
        updateBlurBackend();
    }

    void setActiveBlurBackendListener(ActiveBlurBackendListener listener) {
        activeBlurBackendListener = listener;
        if (listener != null) listener.onActiveBlurBackendChanged(activeBlurBackend);
    }

    private void setActiveBlurBackendState(LiquidBlurMode mode) {
        LiquidBlurMode next = mode == null ? LiquidBlurMode.SHADER : mode;
        if (activeBlurBackend == next) return;
        activeBlurBackend = next;
        advancedMaterialActive = next == LiquidBlurMode.ADVANCED_MATERIAL;
        if (activeBlurBackendListener != null) {
            activeBlurBackendListener.onActiveBlurBackendChanged(activeBlurBackend);
        }
    }

    void setBlurRadiusPx(int radiusPx) {
        int next = Math.max(0, radiusPx);
        if (blurRadius == next) return;
        blurRadius = next;
        float displacement = blurRadius * .5f * (1f + Math.abs(chromaticAberration));
        captureBleedPx = Math.max(8, Math.min(512,
                (int) Math.ceil(blurRadius + displacement + 8f * displayDensity)));
        if (requestedBlurMode == LiquidBlurMode.ADVANCED_MATERIAL) {
            updateBlurBackend();
        } else {
            invalidate();
        }
    }

    private void updateBlurBackend() {
        boolean applied = false;
        if (requestedBlurMode == LiquidBlurMode.ADVANCED_MATERIAL
                && !advancedMaterialUnavailableForProcess) {
            applied = MiBlurBridge.applyContentBlur(this, blurRadius, .5f);
            if (!applied) {
                advancedMaterialUnavailableForProcess = true;
                logW("advanced material blur failed; shader fallback for this Launcher process");
            }
        } else if (requestedBlurMode == LiquidBlurMode.SHADER) {
            MiBlurBridge.clearContentBlur(this);
        }
        setActiveBlurBackendState(
                LiquidBlurBackendPolicy.activeBackend(requestedBlurMode, applied));
        if (!advancedMaterialActive && requestedBlurMode == LiquidBlurMode.ADVANCED_MATERIAL) {
            MiBlurBridge.clearContentBlur(this);
        }
        invalidate();
    }

    /** Called by MainHook's Launcher lifecycle hooks.  Unknown is intentionally allowed:
     * the actual View window visibility/focus is sufficient to bootstrap the first frame. */
    void setLauncherState(boolean known, boolean resumed) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setLauncherState(known, resumed));
            return;
        }
        // Focus/lifecycle callbacks are also the freeform enter/exit boundary. HyperOS may
        // report the same logical Launcher state on both sides, so always invalidate the
        // short task/layer cache and request a fresh scene sample below.
        freeformLayerResolver.invalidate();
        boolean changed = launcherLifecycleKnown != known || launcherResumed != resumed;
        launcherLifecycleKnown = known;
        launcherResumed = resumed;
        if (known && !resumed) {
            sceneState.setLauncherAwayHint(true);
            // The Dock lives in its own overlay window ("Floating Dock", type 2997) that stays
            // visible over other apps.  A Launcher onPause does NOT mean the Dock is hidden, so
            // we must not hard-cancel capture here: window visibility/isShown() below is the
            // authoritative gate for the floating window.  Just re-evaluate.
            logI("Liquid capture lifecycle=PAUSED; window visibility decides");
            requestStateCapture("lifecycle-paused");
            return;
        }
        if (changed) {
            logI("Liquid capture lifecycle=" + (known ? "RESUMED" : "UNKNOWN")
                    + "; window gate will decide capture");
        }
        // Even when (known,resumed) is unchanged, a freeform task may just have appeared or
        // disappeared. Force the boundary frame so HOME switches between wallpaper and the
        // live full-display-with-exclusions path immediately.
        observationValid = false;
        lastCaptureStartNanos = 0L;
        requestStateCapture(changed ? "lifecycle" : "lifecycle-scene-refresh");
    }

    void setLauncherResumed(boolean resumed) {
        setLauncherState(true, resumed);
    }

    /** Notification shade / control center is a SystemUI surface above Launcher and apps.
     * It must never drive Dock backdrop capture. Expansion hard-cancels queued work without
     * the Dock animation grace; collapse schedules one fresh frame for the underlying scene. */
    void setSystemUiPanelExpanded(boolean expanded) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setSystemUiPanelExpanded(expanded));
            return;
        }
        if (systemUiPanelExpanded == expanded) return;
        systemUiPanelExpanded = expanded;
        if (expanded) {
            logI("Liquid capture stopped: SystemUI panel expanded");
            mainHandler.removeCallbacks(cancelGrace);
            cancelPendingCaptureWork();
        } else {
            logI("Liquid capture resumed: SystemUI panel collapsed");
            resetCaptureCircuit("systemui-collapse");
            beginObservationBurst();
            observationValid = false;
            requestStateCapture("systemui-panel-collapsed");
        }
    }

    /** The module settings Activity is not a useful live-Dock capture target.  It can also
     * destroy/recreate the Floating Dock root, so pause mode-1 until another known top task
     * replaces it. UNKNOWN observations deliberately leave this latch unchanged. */
    void setModuleSettingsForeground(boolean foreground) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setModuleSettingsForeground(foreground));
            return;
        }
        if (moduleSettingsForeground == foreground) return;
        moduleSettingsForeground = foreground;
        if (foreground) {
            appBackdropPrearmActive = false;
            appBackdropPrearmToken++;
            mainHandler.removeCallbacks(cancelGrace);
            cancelPendingCaptureWork();
            invalidateDockWindowSurfaceCache();
            logI("Liquid capture paused: module settings foreground");
            return;
        }
        invalidateDockWindowSurfaceCache();
        resetCaptureCircuit("module-settings-exit");
        sourceDirty = true;
        observationValid = false;
        lastCaptureStartNanos = 0L;
        requestStateCapture("module-settings-exit");
        logI("Liquid capture resumed: module settings left foreground");
    }

    /** Called from WallpaperManager wallpaper-transform hooks in this process. */
    private void invalidateWallpaperTransform() {
        wallpaperTransformRevision++;
        wallpaperCacheReady = false;
        clearWallpaperCacheSafely();
    }

    void onWallpaperOffsetChanged(float xOffset, float yOffset) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> onWallpaperOffsetChanged(xOffset, yOffset));
            return;
        }
        int xb = Float.floatToIntBits(xOffset);
        int yb = Float.floatToIntBits(yOffset);
        if (wallpaperOffsetValid && xb == wallpaperOffsetXBits && yb == wallpaperOffsetYBits) return;
        wallpaperOffsetValid = true;
        wallpaperOffsetXBits = xb;
        wallpaperOffsetYBits = yb;
        invalidateWallpaperTransform();
        requestStateCapture("wallpaper-offset");
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
        invalidateWallpaperTransform();
        requestStateCapture("wallpaper-display-offset");
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
        invalidateWallpaperTransform();
        requestStateCapture("wallpaper-zoom");
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        if (requestedBlurMode == LiquidBlurMode.ADVANCED_MATERIAL
                && !advancedMaterialUnavailableForProcess
                && !advancedMaterialActive) {
            updateBlurBackend();
        }
        captureGeneration++;
        captureTimeoutStreak = 0;
        captureCircuitOpen = false;
        beginObservationBurst();
        windowVisible = getWindowVisibility() == View.VISIBLE;
        windowFocused = hasWindowFocus();
        refreshDockWindowSurfaceCache("attach");
        logOwnWindowInfo();
        refreshForegroundAppLayer();
        observationValid = false;
        // Independent config hot-reload ticker: GUI edits to tint/highlight keys
        // apply within ~1s even when the Dock is static (no captures -> no capture-loop
        // reload).  Runs for the lifetime of the glass view.
        stopConfigReloadTick();
        ensureConfigReloadTick(false);

        captureThread = new HandlerThread("LiquidDock-WallpaperCapture");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());

        observedTree = getRootView().getViewTreeObserver();
        observedTree.addOnPreDrawListener(this);
        geometrySource.addOnLayoutChangeListener(geometryLayoutListener);
        logI("Liquid capture attached: visible=" + windowVisible
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
        MiBlurBridge.clearContentBlur(this);
        setActiveBlurBackendState(LiquidBlurMode.SHADER);
        cancelPendingCaptureWork();
        stopConfigReloadTick();
        invalidateDockWindowSurfaceCache();

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
            resetCaptureCircuit("window-visible");
            beginObservationBurst();
            invalidateDockWindowSurfaceCache();
            observationValid = false;
            ensureConfigReloadTick(true);
            requestStateCapture("window-visible");
        } else {
            markGlassVisibilityDirty();
            // Defer the hard cancel by the stop-grace period so a collapse/hide animation
            // tail is still captured instead of freezing mid-animation.
            mainHandler.removeCallbacks(cancelGrace);
            if (stopGraceMillis > 0) {
                logI("Liquid capture window hidden; grace " + stopGraceMillis + "ms before stop");
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
            resetCaptureCircuit("window-focus");
            beginObservationBurst();
            observationValid = false;
            ensureConfigReloadTick(true);
            requestStateCapture("window-focus");
        }
    }

    @Override public boolean onPreDraw() {
        ensureConfigReloadTick(false);
        if (!isCaptureAllowed()) return true;
        if (nativeBackgroundHiddenByGlass && geometrySource.getAlpha() != 0f) {
            geometrySource.setAlpha(0f);
        }
        if (updateObservation()) {
            if (workstationMode && (sceneState.allAppsActive() || isRecentsVisible())) {
                workstationCaptureBurst.start();
            }
            requestStateCapture("observation");
        }
        updateDesiredScene();
        return true;
    }

    @Override protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        resetCaptureCircuit("configuration");
        beginObservationBurst();
        // HyperOS may stop drawing the floating Dock while rebuilding its window, so rotation
        // cannot rely on onPreDraw polling. Invalidate immediately, then capture again after
        // the new orientation's Dock geometry has settled.
        observationValid = false;
        appVisualSignatureValid = false;
        dynamicAppActiveUntilNanos = 0L;
        // Rotation barrier: the strip from the previous orientation is stale by
        // definition; never serve it for the new one.  Also reset the black-frame log
        // gate (it accumulates to 5 per process and silently stops logging).
        wallpaperCacheReady = false;
        invalidateDockWindowSurfaceCache();
        clearWallpaperCacheSafely();
        blackFrameLogCount = 0;
        blackFrameRetryCount = 0;
        requestStateCapture("configuration-changed");
        mainHandler.postDelayed(() -> {
            if (!attached) return;
            observationValid = false;
            requestStateCapture("configuration-settled");
        }, 180L);
    }

    /** Drop the wallpaper strip cache without racing worker-thread cache reads. */
    private void clearWallpaperCacheSafely() {
        synchronized (wallpaperCacheLock) {
            Bitmap old = wallpaperStripCache;
            wallpaperStripCache = null;
            cacheStripRect = null;
            cacheRotation = -1;
            cacheDisplayWidth = -1;
            cacheDisplayHeight = -1;
            cacheWallpaperId = -1;
            cacheWallpaperTransformRevision = -1L;
            if (old != null && old != capture && !old.isRecycled()) old.recycle();
        }
    }

    /**
     * Cheap state polling; never captures by itself when all tracked values are static.
     * Tracks the Dock's own geometry plus — while the recents panel is VISIBLE — its
     * scroll/translation, so task-card animations keep the glass live.  Normal
     * home-screen page swipes (recents hidden) do NOT trigger captures.
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

        boolean recentsVisible = overviewActive;
        int recentsScrollX = 0, recentsScrollY = 0, recentsTx = 0, recentsTy = 0;
        View rec = recentsView;
        // mOverviewPanel is only an animation-observation source. It is NOT a state signal.
        if (recentsVisible && rec != null) {
            recentsScrollX = rec.getScrollX();
            recentsScrollY = rec.getScrollY();
            recentsTx = Float.floatToIntBits(rec.getTranslationX());
            recentsTy = Float.floatToIntBits(rec.getTranslationY());
        }

        boolean rotationChanged = observationValid && rotation != observedRotation;
        boolean dockGeometryChanged = tmpDockLocation[0] != observedDockX
                || tmpDockLocation[1] != observedDockY
                || dockW != observedDockWidth
                || dockH != observedDockHeight
                || dockTx != observedDockTranslationX
                || dockTy != observedDockTranslationY
                || dockSx != observedDockScaleX
                || dockSy != observedDockScaleY;

        boolean changed = dockDragging
                || !observationValid
                || rotation != observedRotation
                || tmpDisplaySize.x != observedDisplayWidth
                || tmpDisplaySize.y != observedDisplayHeight
                || dockGeometryChanged
                || recentsVisible != observedRecentsVisible
                || (recentsVisible && (
                        recentsScrollX != observedRecentsScrollX
                        || recentsScrollY != observedRecentsScrollY
                        || recentsTx != observedRecentsTranslationX
                        || recentsTy != observedRecentsTranslationY));
        if (rotationChanged) {
            // Never compare samples from two coordinate systems. The changed observation
            // schedules one fresh capture using the new display geometry below.
            appVisualSignatureValid = false;
            dynamicAppActiveUntilNanos = 0L;
        }
        // Dock geometry motion (drag-out / expand / collapse) activates the dynamic
        // app-capture rate for the animation window; static content would otherwise fall
        // back to the slow probe cadence (~3 fps) while the Dock is being pulled out.
        if (dynamicAppCapture && changed && dockGeometryChanged) {
            dynamicAppActiveUntilNanos = Math.max(dynamicAppActiveUntilNanos,
                    System.nanoTime() + dynamicMotionHoldNanos);
        }
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
            if (vri == null) { logI("own window: no ViewRootImpl"); return; }
            Class<?> vriClass = Class.forName("android.view.ViewRootImpl");
            java.lang.reflect.Field attrsField = vriClass.getDeclaredField("mWindowAttributes");
            attrsField.setAccessible(true);
            Object attrs = attrsField.get(vri);
            if (attrs instanceof android.view.WindowManager.LayoutParams) {
                android.view.WindowManager.LayoutParams lp =
                        (android.view.WindowManager.LayoutParams) attrs;
                logI("own window: type=" + lp.type + " title=" + lp.getTitle());
            }
        } catch (Throwable e) {
            logW("own window info failed: " + e);
        }
    }

    /** Compatibility callback retained for LauncherSceneController.
     * Mode-1 uses full-display capture with the Dock SurfaceControl excluded, so there is no
     * app-layer lookup here and no dependency on the removed ISurfaceComposer$Stub API. */
    void refreshForegroundAppLayer() {
        // Intentionally empty.
    }

    private static final class DockWindowSurfaceSnapshot {
        final android.view.SurfaceControl surface;
        final String layerName;
        final int rootIndex;

        DockWindowSurfaceSnapshot(android.view.SurfaceControl surface,
                                  String layerName, int rootIndex) {
            this.surface = surface;
            this.layerName = layerName;
            this.rootIndex = rootIndex;
        }
    }

    private String readSurfaceControlLayerName(android.view.SurfaceControl sc) {
        if (sc == null) return null;
        try {
            java.lang.reflect.Field nameField = sc.getClass().getDeclaredField("mName");
            nameField.setAccessible(true);
            Object value = nameField.get(sc);
            if (value instanceof String && !((String) value).isEmpty()) return (String) value;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private FullDisplayExclusions resolveFullDisplayExclusions() {
        boolean freeformActive = freeformLayerResolver.hasVisibleFreeformTasks();
        java.util.Collection<String> freeformLayers =
                freeformLayerResolver.resolveVisibleLayerNames();
        String[] names = CaptureExclusionNames.merge(
                dockWindowLayerName, dragLayerName, freeformLayers);
        boolean safe = !freeformActive || !freeformLayers.isEmpty();
        if (freeformActive) {
            logI("freeform capture exclusion: resolved=" + freeformLayers.size()
                    + " names=" + java.util.Arrays.toString(names));
        }
        return new FullDisplayExclusions(names, safe);
    }

    private static final class FullDisplayExclusions {
        static final FullDisplayExclusions NONE = new FullDisplayExclusions(null, true);
        final String[] layerNames;
        final boolean safe;
        FullDisplayExclusions(String[] layerNames, boolean safe) {
            this.layerNames = layerNames;
            this.safe = safe;
        }
    }

    /** Resolve the newest live type-2997 root.  HyperOS can retain the retired root in
     * WindowManagerGlobal while the rebuilt Floating Dock root is appended after it, so
     * returning the first match can resurrect an old SurfaceControl forever. */
    private DockWindowSurfaceSnapshot resolveDockWindowSurfaceSnapshot() {
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
            DockWindowSurfaceSnapshot best = null;
            for (int i = 0; i < list.size(); i++) {
                Object root = list.get(i);
                if (root == null) continue;
                try {
                    Class<?> vriClass = Class.forName("android.view.ViewRootImpl");
                    java.lang.reflect.Field attrsField =
                            vriClass.getDeclaredField("mWindowAttributes");
                    attrsField.setAccessible(true);
                    Object attrs = attrsField.get(root);
                    if (!(attrs instanceof android.view.WindowManager.LayoutParams)) continue;
                    android.view.WindowManager.LayoutParams lp =
                            (android.view.WindowManager.LayoutParams) attrs;
                    if (lp.type != 2997) continue;

                    java.lang.reflect.Method getSc =
                            vriClass.getDeclaredMethod("getSurfaceControl");
                    getSc.setAccessible(true);
                    Object value = getSc.invoke(root);
                    if (!(value instanceof android.view.SurfaceControl)) continue;
                    android.view.SurfaceControl sc = (android.view.SurfaceControl) value;
                    String name = readSurfaceControlLayerName(sc);
                    if (name == null || name.isEmpty()) {
                        CharSequence title = lp.getTitle();
                        name = title == null ? null : title.toString();
                    }
                    DockWindowSurfaceSnapshot candidate =
                            new DockWindowSurfaceSnapshot(sc, name, i);
                    if (best == null
                            || DockLayerIdentity.isNewerGeneration(name, best.layerName)
                            || (DockLayerIdentity.layerId(name) < 0L
                                && DockLayerIdentity.layerId(best.layerName) < 0L
                                && i > best.rootIndex)) {
                        best = candidate;
                    }
                } catch (Throwable ignored) {
                }
            }
            if (best == null) {
                logW("dock window surface: no root with type 2997 found (roots="
                        + list.size() + ")");
            }
            return best;
        } catch (Throwable e) {
            logW("dock window surface resolve failed: " + e);
            return null;
        }
    }

    private boolean hasValidDockWindowSurface() {
        android.view.SurfaceControl cached = dockWindowSurface;
        String cachedName = dockWindowLayerName;
        if (cached == null || cachedName == null) return false;
        DockWindowSurfaceSnapshot current = resolveDockWindowSurfaceSnapshot();
        if (current == null
                || !DockLayerIdentity.sameGeneration(cachedName, current.layerName)) return false;
        try {
            return cached.isValid();
        } catch (Throwable ignored) {
            // Vendor isValid() is not authoritative.  A matching current WMG generation is.
            return current.surface != null;
        }
    }

    private boolean refreshDockWindowSurfaceCache(String reason) {
        DockWindowSurfaceSnapshot current = resolveDockWindowSurfaceSnapshot();
        if (current == null || current.surface == null || current.layerName == null) {
            dockWindowSurface = null;
            logW("dock window surface unavailable reason=" + reason
                    + " cachedLayer=" + dockWindowLayerName);
            return false;
        }
        String oldName = dockWindowLayerName;
        android.view.SurfaceControl oldSurface = dockWindowSurface;
        boolean generationChanged = oldName != null
                && (!DockLayerIdentity.sameGeneration(oldName, current.layerName)
                    || (DockLayerIdentity.layerId(oldName) < 0L
                        && oldSurface != null && oldSurface != current.surface));
        dockWindowSurface = current.surface;
        dockWindowLayerName = current.layerName;
        if (generationChanged) {
            dockExcludeRecovery.onSurfaceGenerationChanged();
            logW("Floating Dock surface generation changed old=" + oldName
                    + " new=" + current.layerName + " reason=" + reason);
        }
        logI("Liquid capture dock window surface root[" + current.rootIndex
                + "] layerName=" + current.layerName + " reason=" + reason);
        return true;
    }

    private void invalidateDockWindowSurfaceHandle() {
        dockWindowSurface = null;
    }

    private void invalidateDockWindowSurfaceCache() {
        dockWindowSurface = null;
        dockWindowLayerName = null;
        dockExcludeRecovery.onSurfaceGenerationChanged();
    }

    /** Recents/multitasking view to watch for background motion (set via reflection by
     *  MainHook from Launcher.getRecentsView()).  Null disables the extra observation. */
    void setRecentsView(View view) {
        recentsView = view;
    }

    boolean isAllAppsActive() {
        return sceneState.allAppsActive();
    }

    boolean isOverviewActive() {
        return overviewActive;
    }

    void prearmAllAppsCapture(String reason) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> prearmAllAppsCapture(reason));
            return;
        }
        if (workstationMode) return;
        final int token = ++allAppsPrearmToken;
        sceneState.prearmAllApps(System.nanoTime());
        updateDesiredScene();
        observationValid = false;
        lastCaptureStartNanos = 0L;
        requestStateCapture("all-apps-prearm-" + reason);
        mainHandler.postDelayed(() -> {
            if (token != allAppsPrearmToken || sceneState.allAppsActive()) return;
            if (!sceneState.allAppsPrearmExpired(System.nanoTime())) return;
            sceneState.clearAllAppsPrearm();
            updateDesiredScene();
            requestStateCapture("all-apps-prearm-expired");
        }, 920L);
    }

    void setAllAppsActive(boolean active) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setAllAppsActive(active));
            return;
        }
        allAppsPrearmToken++;
        sceneState.clearAllAppsPrearm();
        boolean stateChanged = sceneState.allAppsActive() != active;
        sceneState.setAllAppsActive(active);
        if (stateChanged && workstationMode) {
            workstationSuspendWhenBurstSettles = !active;
            if (active) {
                startWorkstationCaptureBurst("all-apps-enter");
            } else {
                startWorkstationCaptureBurst("all-apps-exit");
            }
        }
        updateDesiredScene();
        if (!stateChanged) return;
        observationValid = false;
        lastCaptureStartNanos = 0L;
        requestStateCapture(active ? "all-apps-enter" : "all-apps-exit");
    }

    /** Stock Recents callbacks own confirmed Overview state; gesture targets remain prearm-only. */
    void setOverviewActive(boolean active, String reason) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setOverviewActive(active, reason));
            return;
        }
        clearRecentsPrearmWindow();
        if (overviewActive == active) return;
        overviewActive = active;
        observationValid = false;
        lastCaptureStartNanos = 0L;
        if (workstationMode && workstationRecentsActive) {
            workstationSuspendWhenBurstSettles = !active;
            if (active) {
                startWorkstationCaptureBurst("workstation-recents-enter");
            } else {
                startWorkstationCaptureBurst("workstation-recents-exit");
            }
        }
        if (active) {
            sceneState.setGestureTarget("RECENTS", System.nanoTime());
        } else if (sceneState.desired() == CaptureScene.RECENTS) {
            sceneState.clearGestureTarget();
        }
        updateDesiredScene();
        requestStateCapture(active ? "overview-enter-" + reason : "overview-exit-" + reason);
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
        resetCaptureCircuit("dock-touch");
        beginObservationBurst();
        requestStateCapture("dock-touch");
    }

    void onDockGestureMotion(int action, float rawY) {
        onDockGestureMotion(action, rawY, false);
    }

    void onDockGestureMotion(int action, float rawY,
                             boolean externalAppForegroundConfirmed) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> onDockGestureMotion(
                    action, rawY, externalAppForegroundConfirmed));
            return;
        }
        // Workstation has no swipe-to-Recents path; only Launcher.showOrHideRecent()
        // may activate live capture there.
        if (workstationMode) return;
        if (action == android.view.MotionEvent.ACTION_DOWN) {
            resetCaptureCircuit("gesture-down");
            beginObservationBurst();
            gestureDownRawY = rawY;
            clearRecentsPrearmWindow();
            // Source correctness is independent of the optional continuous APP capture
            // cadence. If the Dock pull starts over an external app, keep the whole
            // pre-haptic interaction in the live APP/RECENTS domain until Launcher HOME
            // is authoritatively confirmed.
            if (externalAppForegroundConfirmed) {
                sceneState.setExternalAppForegroundConfirmed(externalAppForegroundConfirmed);
            }
            boolean externalAppInteraction = (externalAppForegroundConfirmed ||
                    (launcherLifecycleKnown && !launcherResumed))
                    && !overviewActive && !sceneState.allAppsActive();
            sceneState.setExternalAppDockInteraction(externalAppInteraction);
            if (externalAppInteraction) {
                // ACTION_DOWN is the first deadline for a visible APP backdrop. Supersede any
                // older HOME/APP request now instead of letting requestStateCapture coalesce
                // behind it; its callback is invalidated by cancelPendingCaptureWork().
                cancelPendingCaptureWork();
            }
            // The Dock may still be fully collapsed here. Allow exactly this gesture's
            // first APP frame to bypass visibility so the backdrop is live before motion.
            armAppBackdropForGestureDown();
            requestStateCapture("dock-gesture-down");
            return;
        }
        if (action == android.view.MotionEvent.ACTION_MOVE && !recentsPrearmed
                && !Float.isNaN(gestureDownRawY)
                && gestureDownRawY - rawY >= recentsPrearmDistancePx) {
            prearmRecentsCapture("recents-prearm-distance");
            logI("Recents capture pre-armed after upward distance="
                    + (gestureDownRawY - rawY));
            return;
        }
        if (action == android.view.MotionEvent.ACTION_UP
                || action == android.view.MotionEvent.ACTION_CANCEL) {
            gestureDownRawY = Float.NaN;
            sceneState.setExternalAppDockInteraction(false);
            updateDesiredScene();
            // Keep the haptic/gesture prearm alive until an authoritative Recents callback,
            // a HOME/APP gesture target, or the bounded gesture-target timeout clears it.
            if (sceneState.desired() != CaptureScene.RECENTS) clearRecentsPrearmWindow();
            requestStateCapture("dock-gesture-end");
        }
    }

    /** Called from the launcher's dedicated performEnterRecent haptic event. */
    void onRecentsHapticTrigger() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::onRecentsHapticTrigger);
            return;
        }
        if (workstationMode) return;
        prearmRecentsCapture("recents-prearm-haptic");
        logI("Recents capture pre-armed by launcher haptic event");
    }

    void prearmRecentsCaptureHint(String reason) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> prearmRecentsCaptureHint(reason));
            return;
        }
        if (workstationMode) return;
        prearmRecentsCapture(reason);
    }

    private void armRecentsPrearmWindow(long durationMs) {
        recentsPrearmed = true;
        final int token = ++recentsPrearmToken;
        mainHandler.postDelayed(() -> {
            if (token != recentsPrearmToken || overviewActive) return;
            recentsPrearmed = false;
            if (sceneState.gestureTargetExpired(System.nanoTime())) sceneState.clearGestureTarget();
            updateDesiredScene();
            requestStateCapture("recents-prearm-expired");
        }, durationMs);
    }

    private void clearRecentsPrearmWindow() {
        recentsPrearmToken++;
        recentsPrearmed = false;
    }

    private void prearmRecentsCapture(String reason) {
        armRecentsPrearmWindow(720L);
        sceneState.prearmRecents(System.nanoTime());
        observationValid = false;
        lastCaptureStartNanos = 0L;
        mainHandler.removeCallbacks(captureKick);
        kickScheduled = false;
        cancelPendingCaptureWork();
        requestStateCapture(reason);
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
            resetCaptureCircuit("drag-start");
            beginObservationBurst();
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
        bleedTopPx = topPx < 0 ? -1 : Math.min(512, topPx);
        bleedBottomPx = bottomPx < 0 ? -1 : Math.min(512, bottomPx);
    }

    /** Prismal liquid-glass parameters (GUI-configurable; values are dp where noted,
     *  converted to px at render time using the display density). */
    void setPrismalParams(float thicknessDp, float ior, float normalStrength,
                          float liquidDome, float lensRefractionDp) {
        glassThickness = thicknessDp;
        glassIor = ior;
        glassNormalStrength = normalStrength;
        glassLiquidDome = liquidDome;
        glassLensRefraction = lensRefractionDp;
        invalidate();
    }

    /** Compositor readback scale (GUI: liquid_capture_scale, 10-100%).  Lower = cheaper
     *  captures; the glass is blurred anyway so refraction stays visually lossless. */
    void setCaptureScale(float scale) {
        captureScale = Math.max(0.1f, Math.min(1.0f, scale));
    }

    /** Optional continuous refresh for games/video while the floating Dock is visible. */
    void setDynamicAppCapture(boolean enabled, int fps, int probeFps,
                              int differenceThreshold, int bitThreshold,
                              int holdMillis, int blackThreshold) {
        dynamicAppCapture = enabled;
        captureCadence.setDynamicFps(fps, probeFps);
        dynamicMotionDifferenceThreshold = Math.max(1, Math.min(240, differenceThreshold));
        dynamicMotionBitThreshold = Math.max(1, Math.min(64, bitThreshold));
        dynamicMotionHoldNanos = Math.max(0, Math.min(5000, holdMillis)) * 1_000_000L;
        // Threshold 0 would disable the guard entirely (sum/count < 0 is never true),
        // and the device config once carried exactly that — pure-black rotation frames
        // sailed through to installCapture.  Clamp to >= 1 so the guard always works;
        // a config of 0 now means "very strict" instead of "off".
        blackFrameThreshold = Math.max(1, Math.min(64, blackThreshold));
        if (!enabled) {
            dynamicAppActiveUntilNanos = 0L;
            appVisualSignatureValid = false;
        }
    }

    /** Hard ceiling shared by animation, recents and adaptive APP captures. */
    void setCapturePowerLimitFps(int fps) {
        captureCadence.setPowerLimitFps(fps);
    }

    /** Edge-highlight thickness multiplier (GUI: liquid_highlight_width, 20-300%).
     *  Scales both the shader's edge-glow band and the canvas stroke highlight. */
    void setHighlightWidth(float multiplier) {
        float next = Math.max(0.2f, Math.min(3.0f, multiplier));
        if (next == glassHighlightWidth) return; // idempotent: no repaint on hot-reload
        glassHighlightWidth = next;
        invalidate();
    }

    /** Glass tint RGB (GUI: liquid_tint_r/g/b, 0-255).  Alpha is liquid_tint_alpha. */
    void setTintColor(int r, int g, int b) {
        int nr = Math.max(0, Math.min(255, r));
        int ng = Math.max(0, Math.min(255, g));
        int nb = Math.max(0, Math.min(255, b));
        if (nr == glassTintR && ng == glassTintG && nb == glassTintB) return;
        glassTintR = nr;
        glassTintG = ng;
        glassTintB = nb;
        invalidate();
    }

    /** Shader appearance knobs (GUI: liquid_depth_effect / liquid_brightness /
     *  liquid_specular_sharp / liquid_specular_strength / liquid_rim_light /
     *  liquid_caustics / liquid_edge_band). */
    void setAppearance(float depthEffect, float brightness, float specularSharp,
                       float specularStrength, float rimLight, float caustics, float edgeBand,
                       float highlightAlpha) {
        float nd = Math.max(0f, Math.min(1f, depthEffect));
        float nb = Math.max(0.5f, Math.min(2f, brightness));
        float ns = Math.max(1f, Math.min(400f, specularSharp));
        float nst = Math.max(0f, Math.min(5f, specularStrength));
        float nr = Math.max(0f, Math.min(3f, rimLight));
        float nc = Math.max(0f, Math.min(1f, caustics));
        float ne = Math.max(0.005f, Math.min(0.1f, edgeBand));
        float nha = Math.max(0f, Math.min(2f, highlightAlpha));
        if (nd == glassDepthEffect && nb == glassBrightness && ns == glassSpecularSharp
                && nst == glassSpecularStrength && nr == glassRimLight
                && nc == glassCaustics && ne == glassEdgeBand
                && nha == glassHighlightAlpha) return;
        glassDepthEffect = nd;
        glassBrightness = nb;
        glassSpecularSharp = ns;
        glassSpecularStrength = nst;
        glassRimLight = nr;
        glassCaustics = nc;
        glassEdgeBand = ne;
        glassHighlightAlpha = nha;
        invalidate();
    }

    void setRecentsPrearmDistanceDp(float distanceDp) {
        recentsPrearmDistancePx = Math.max(1f,
                distanceDp * getResources().getDisplayMetrics().density);
    }

    void setFullscreenCapture(boolean enabled) { fullscreenCapture = enabled; }

    /** Apply the live liquid_glass switch without requiring a Launcher restart. */
    private void setRuntimeGlassEnabled(boolean enabled) {
        if (runtimeGlassEnabled == enabled) return;
        runtimeGlassEnabled = enabled;
        if (!enabled) {
            appBackdropPrearmActive = false;
            appBackdropPrearmToken++;
            cancelPendingCaptureWork();
            markGlassVisibilityDirty();
            appVisualSignatureValid = false;
            dynamicAppActiveUntilNanos = 0L;
            clearWallpaperCacheSafely();

            Bitmap old = capture;
            capture = null;
            captureShader = null;
            installedCaptureScene = null;
            if (old != null && !old.isRecycled()) old.recycle();

            if (nativeBackgroundHiddenByGlass) {
                geometrySource.setAlpha(1f);
                nativeBackgroundHiddenByGlass = false;
            }
            setVisibility(INVISIBLE);
            invalidate();
            logI("Liquid glass runtime-disabled; capture pipeline suspended");
            return;
        }

        if (!sceneState.workstationSuspended()) setVisibility(VISIBLE);
        resetCaptureCircuit("runtime-enable");
        beginObservationBurst();
        observationValid = false;
        appVisualSignatureValid = false;
        dynamicAppActiveUntilNanos = 0L;
        logI("Liquid glass runtime-enabled; requesting fresh backdrop");
        requestStateCapture("runtime-glass-enabled");
    }

    /**
     * Keep the 1 Hz hot-reload only while the glass is actually on-screen (or Recents is
     * intentionally self-capturing). A runtime-disabled glass polls slowly so it can be
     * re-enabled without restarting Launcher. Screen-off/Doze does not poll at all.
     */
    private void ensureConfigReloadTick(boolean immediate) {
        if (configReloadScheduled || !attached || !isDisplayInteractive()) return;
        long delay;
        if (!runtimeGlassEnabled) {
            delay = immediate ? 0L : CONFIG_DISABLED_RELOAD_MS;
        } else if (isRecentsVisible() || isGlassActuallyVisible()) {
            delay = immediate ? 0L : CONFIG_VISIBLE_RELOAD_MS;
        } else {
            return;
        }
        configReloadScheduled = true;
        mainHandler.postDelayed(configReloadTick, delay);
    }

    private void stopConfigReloadTick() {
        mainHandler.removeCallbacks(configReloadTick);
        configReloadScheduled = false;
    }

    /** Re-read GUI-adjustable appearance keys from Remote Preferences. */
    private void reloadAppearanceFromConfig() {
        try {
            LiquidDockConfig fullConfig = LiquidDockConfig.load();
            LiquidDockConfig.Glass cfg = fullConfig.glass;
            if (!cfg.enabled) {
                setRuntimeGlassEnabled(false);
                return;
            }

            boolean wasEnabled = runtimeGlassEnabled;
            setTintColor(cfg.tintR, cfg.tintG, cfg.tintB);
            tintPaint.setAlpha(cfg.tintAlpha);
            setHighlightWidth(cfg.highlightWidth);
            float blurScale = cfg.dimensionsDp ? displayDensity : 1f;
            setBlurRadiusPx(Math.round(cfg.blur * blurScale));
            setBlurMode(cfg.blurMode);
            if (getParent() instanceof DockLiquidGlassHostView) {
                ((DockLiquidGlassHostView) getParent()).reloadOverlay(fullConfig.dock, cfg);
            }
            setAppearance(cfg.depthEffect, cfg.brightness, cfg.specularSharp,
                    cfg.specularStrength, cfg.rimLight, cfg.caustics, cfg.edgeBand,
                    cfg.highlightAlpha);
            setCaptureScale(cfg.captureScale);
            setDynamicAppCapture(cfg.dynamicAppCapture, cfg.captureFps, cfg.probeFps,
                    cfg.motionThreshold, cfg.motionBitThreshold, cfg.motionHoldMs,
                    cfg.blackThreshold);
            setCapturePowerLimitFps(cfg.captureFps);
            setRecentsPrearmDistanceDp(cfg.recentsPrearmDistance);
            setFullscreenCapture(cfg.fullscreenCapture);
            if (!wasEnabled) setRuntimeGlassEnabled(true);
        } catch (Throwable e) {
            // Config missing/corrupt: keep the current values.
        }
    }

    /** Adaptive config hot-reload: visible glass = 1 Hz, disabled = 0.2 Hz,
     * hidden enabled glass = event-driven only, screen-off/Doze = fully stopped. */
    private final Runnable configReloadTick = new Runnable() {
        @Override public void run() {
            configReloadScheduled = false;
            if (!attached || !isDisplayInteractive()) return;
            reloadAppearanceFromConfig();
            ensureConfigReloadTick(false);
        }
    };

    private void beginObservationBurst() {
        markGlassVisibilityDirty();
    }

    private void markGlassVisibilityDirty() {
        glassVisibilityDirty = true;
        glassVisibilityCheckedNanos = 0L;
    }

    /** Recents is an explicit launcher state, not a View-visibility heuristic. */
    private boolean isRecentsVisible() {
        return overviewActive;
    }

    /** Capture source is target-state driven; animation progress only controls cadence. */
    private CaptureScene resolveCaptureScene() {
        return sceneState.resolve(System.nanoTime(), isRecentsVisible(),
                launcherLifecycleKnown, launcherResumed);
    }

    /** HyperOS Dock v3 reports the gesture destination before the stock state callbacks.
     * The target is a bounded first-frame prearm; authoritative Drawer/Recents callbacks own
     * the persistent state. */
    void setGestureCaptureTarget(String target) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setGestureCaptureTarget(target));
            return;
        }
        if (workstationMode) return;
        if ("RECENTS".equals(target)) armRecentsPrearmWindow(1550L);
        else clearRecentsPrearmWindow();
        sceneState.setGestureTarget(target, System.nanoTime());
        updateDesiredScene();
        if ("APP".equals(target)) {
            final int token = ++appBackdropPrearmToken;
            appBackdropPrearmActive = true;
            lastCaptureStartNanos = 0L;
            mainHandler.postDelayed(() -> {
                if (token == appBackdropPrearmToken) appBackdropPrearmActive = false;
            }, 350L);
        }
        observationValid = false;
        requestStateCapture("gesture-target-" + target.toLowerCase(java.util.Locale.ROOT));
        mainHandler.postDelayed(() -> {
            if (!sceneState.gestureTargetExpired(System.nanoTime())) return;
            sceneState.clearGestureTarget();
            if (!overviewActive) clearRecentsPrearmWindow();
            updateDesiredScene();
            requestStateCapture("gesture-target-expired");
        }, 1550L);
    }

    private void applyBackdropTransitionBarrier(CaptureScene target, String reason) {
        boolean dropInstalled = BackdropTransitionPolicy.shouldDropInstalled(
                installedCaptureScene, target);
        if (dropInstalled) {
            CaptureScene oldScene = installedCaptureScene;
            if (CaptureSourcePolicy.sourceFor(target) == CaptureSourcePolicy.Source.WALLPAPER
                    && tryInstallWallpaperCacheImmediately(target)) {
                logI("capture-domain barrier replaced " + oldScene
                        + " with wallpaper cache reason=" + reason);
                return;
            }
            Bitmap old = capture;
            capture = null;
            captureShader = null;
            installedCaptureScene = null;
            if (old != null && old != wallpaperStripCache && !old.isRecycled()) old.recycle();
            logI("capture-domain barrier dropped stale " + oldScene + " for " + target
                    + " reason=" + reason);
        }

        boolean hasInstalledBackdrop = installedCaptureScene != null
                && captureShader != null && capture != null && !capture.isRecycled();
        if (hasInstalledBackdrop) return;

        if (BackdropTransitionPolicy.shouldRevealNativeFallback(target)) {
            if (nativeBackgroundHiddenByGlass) {
                geometrySource.setAlpha(1f);
                nativeBackgroundHiddenByGlass = false;
            }
        } else {
            // APP/RECENTS must never reveal the wallpaper-backed native Dock while the
            // first live mode-1 frame is pending. With no capture shader the glass body is
            // transparent, so the actual app/Recents surface below remains visible instead.
            if (!nativeBackgroundHiddenByGlass || geometrySource.getAlpha() != 0f) {
                geometrySource.setAlpha(0f);
                nativeBackgroundHiddenByGlass = true;
            }
        }
        invalidate();
    }

    private CroppedFrame copyWallpaperCacheFrameLocked(CaptureRequest req) {
        CroppedFrame temp = cropWallpaperTile(wallpaperStripCache, cacheStripRect,
                req.tileRect, req.dockRect, false);
        if (temp == null) return null;
        Bitmap safe = null;
        try {
            safe = temp.bitmap.copy(Bitmap.Config.ARGB_8888, false);
        } finally {
            if (temp.bitmap != wallpaperStripCache && !temp.bitmap.isRecycled()) temp.bitmap.recycle();
        }
        if (safe == null) return null;
        return new CroppedFrame(safe, temp.sampleOffsetX, temp.sampleOffsetY,
                temp.sourceWidth, temp.sourceHeight);
    }

    private boolean tryInstallWallpaperCacheImmediately(CaptureScene target) {
        CaptureRequest req = makeCaptureRequest();
        if (req == null || rotationStabilizeUntilNanos != 0) return false;
        CroppedFrame frame;
        synchronized (wallpaperCacheLock) {
            if (!wallpaperCacheReady || wallpaperStripCache == null
                    || wallpaperStripCache.isRecycled() || cacheStripRect == null
                    || cacheWallpaperTransformRevision != wallpaperTransformRevision
                    || req.rotation != cacheRotation
                    || req.displayWidth != cacheDisplayWidth
                    || req.displayHeight != cacheDisplayHeight
                    || !cacheStripRect.contains(req.stripRect)) return false;
            try {
                int immediateWallpaperId = WallpaperManager.getInstance(getContext())
                        .getWallpaperId(WallpaperManager.FLAG_SYSTEM);
                boolean sameWallpaper = immediateWallpaperId == cacheWallpaperId;
                if (!sameWallpaper) return false;
            } catch (Throwable ignored) {
            }
            frame = copyWallpaperCacheFrameLocked(req);
        }
        if (frame == null) return false;
        installCapture(frame, "domain-cache", target);
        return true;
    }

    private void updateDesiredScene() {
        // Visibility only closes a workstation Recents session; it never opens one.
        // Opening is owned exclusively by the exact showOrHideRecent button hook.
        if (workstationMode && workstationRecentsActive) {
            boolean visible = isRecentsVisible();
            if (visible) {
                workstationRecentsWasVisible = true;
            } else if (workstationRecentsWasVisible) {
                if (workstationCaptureBurst.isActive()) {
                    workstationSuspendWhenBurstSettles = true;
                } else {
                    suspendWorkstationGlass("workstation-recents-hidden");
                    return;
                }
            }
        }
        CaptureScene prev = sceneState.desired();
        if (!sceneState.refresh(System.nanoTime(), isRecentsVisible(),
                launcherLifecycleKnown, launcherResumed)) return;
        sourceDirty = true;
        if (CaptureSourcePolicy.sourceFor(prev)
                != CaptureSourcePolicy.sourceFor(sceneState.desired())) {
            lastCaptureStartNanos = 0L;
        }
        logI("Liquid capture scene=" + sceneState.desired()
                + " revision=" + sceneState.revision());
    }

    /** Hard power gate shared by every scene, including Recents.  Recents is allowed to
     * outlive the Floating Dock window by design, but there is no useful backdrop work once
     * the display is off/dozing or the device is non-interactive. */
    private boolean isDisplayInteractive() {
        Display display = geometrySource.getDisplay();
        if (display == null) return false;
        int state = display.getState();
        if (state == Display.STATE_OFF || state == Display.STATE_DOZE
                || state == Display.STATE_DOZE_SUSPEND) return false;
        return powerManager == null || powerManager.isInteractive();
    }

    /** Normal HOME/APP captures require the glass to occupy a meaningfully visible area.
     * isShown() alone is insufficient on HyperOS because the Floating Dock can collapse via
     * alpha/scale/translation while its View remains VISIBLE and attached. */
    private boolean isGlassActuallyVisible() {
        long now = System.nanoTime();
        if (!glassVisibilityDirty && glassVisibilityCheckedNanos != 0L
                && now - glassVisibilityCheckedNanos <= VISIBILITY_CACHE_NS) {
            return cachedGlassActuallyVisible;
        }
        boolean visible = attached && windowVisible && getVisibility() == View.VISIBLE && isShown()
                && getWidth() > 1 && getHeight() > 1;
        if (visible) {
            float effectiveAlpha = getAlpha();
            float effectiveScaleX = Math.abs(getScaleX());
            float effectiveScaleY = Math.abs(getScaleY());
            android.view.ViewParent parent = getParent();
            while (parent instanceof View) {
                View pv = (View) parent;
                effectiveAlpha *= pv.getAlpha();
                effectiveScaleX *= Math.abs(pv.getScaleX());
                effectiveScaleY *= Math.abs(pv.getScaleY());
                if (effectiveAlpha <= 0.01f || effectiveScaleX <= 0.02f
                        || effectiveScaleY <= 0.02f) {
                    visible = false;
                    break;
                }
                parent = pv.getParent();
            }
            if (visible) {
                tmpGlassVisibleRect.setEmpty();
                visible = getGlobalVisibleRect(tmpGlassVisibleRect) && !tmpGlassVisibleRect.isEmpty();
                if (visible) {
                    long visibleArea = (long) tmpGlassVisibleRect.width() * tmpGlassVisibleRect.height();
                    long fullArea = (long) getWidth() * getHeight();
                    visible = visibleArea >= Math.max(64L, fullArea / 20L);
                }
            }
        }
        cachedGlassActuallyVisible = visible;
        glassVisibilityCheckedNanos = now;
        glassVisibilityDirty = false;
        return visible;
    }

    private boolean isCaptureAllowed() {
        if (!runtimeGlassEnabled) return false;
        if (moduleSettingsForeground) return false;
        if (captureCircuitOpen) return false;
        // Workstation/laptop Dock has an independent background. The normal Dock glass
        // must not capture or render while that container is active.
        if (sceneState.workstationSuspended()) return false;
        // DeviceConfig mirrors notification shade and control-center expansion from SystemUI.
        // This gate must precede drag/recents exceptions: SystemUI is above both and capturing
        // while it is expanded only wastes GPU/binder work and risks sampling its animation.
        if (systemUiPanelExpanded) return false;
        // Screen-off/doze is a hard stop. Unlike Dock visibility, Recents does NOT bypass this.
        if (!isDisplayInteractive()) return false;

        // Workstation All Apps/Recents intentionally draw through a different Dock container;
        // their capture burst must not depend on the normal Floating Dock View being visible.
        if (workstationMode && (workstationCaptureBurst.isActive()
                || sceneState.allAppsActive() || workstationRecentsActive)) {
            lastAllowedNanos = System.nanoTime();
            return true;
        }

        // A real APP focus transition pre-arms only a short bounded window. This bypasses
        // Dock visibility long enough to install one or two mode-1 frames while the Dock is
        // still collapsed, but cannot turn into the normal hidden-APP capture loop.
        if (appBackdropPrearmActive && sceneState.desired() == CaptureScene.APP) {
            lastAllowedNanos = System.nanoTime();
            return true;
        }

        // Recents is intentionally special: HyperOS hides the Floating Dock window while the
        // overview remains on screen, so Recents must keep its self-sustained capture loop alive.
        // Dock dragging likewise needs the animation tail even if normal visibility is transient.
        if (dockDragging || recentsPrearmed || isRecentsVisible()) {
            lastAllowedNanos = System.nanoTime();
            return true;
        }

        // HOME/APP must correspond to a glass that is actually visible.  This prevents the
        // adaptive APP loop from continuing at probe/active FPS after the Dock has collapsed
        // off-screen while its window/View still report VISIBLE.
        if (isGlassActuallyVisible()) {
            lastAllowedNanos = System.nanoTime();
            return true;
        }
        if (stopGraceMillis <= 0) return false;
        long graceEndNanos = lastAllowedNanos + stopGraceMillis * 1_000_000L;
        return System.nanoTime() <= graceEndNanos;
    }

    /** Prepare the APP backdrop before the collapsed Dock is visible. The requests are
     * deliberately bounded to the focus transition; dynamic APP continuation still requires
     * isGlassActuallyVisible(), so this cannot recreate the hidden-Dock power regression. */
    void prearmAppBackdrop(String reason) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> prearmAppBackdrop(reason));
            return;
        }
        if (!runtimeGlassEnabled || workstationMode || sceneState.workstationSuspended()
                || systemUiPanelExpanded || !isDisplayInteractive()) return;
        updateDesiredScene();
        if (sceneState.desired() != CaptureScene.APP) return;
        final int token = ++appBackdropPrearmToken;
        appBackdropPrearmActive = true;
        resetCaptureCircuit("app-prearm-" + reason);
        appVisualSignatureValid = false;
        dynamicAppActiveUntilNanos = 0L;

        for (long delay : APP_BACKDROP_PREARM_DELAYS_MS) {
            mainHandler.postDelayed(() -> {
                if (token != appBackdropPrearmToken || !attached
                        || sceneState.desired() != CaptureScene.APP
                        || systemUiPanelExpanded || !isDisplayInteractive()) return;
                // Bypass cadence for these few transition snapshots. A later shot replaces
                // an early app-launch animation frame before the user can summon the Dock.
                lastCaptureStartNanos = 0L;
                observationValid = false;
                requestStateCapture("app-prearm-" + reason + "-" + delay);
            }, delay);
        }
        mainHandler.postDelayed(() -> {
            if (token != appBackdropPrearmToken) return;
            appBackdropPrearmActive = false;
        }, APP_BACKDROP_PREARM_WINDOW_MS);
    }

    private void armAppBackdropForGestureDown() {
        updateDesiredScene();
        if (sceneState.desired() != CaptureScene.APP
                || workstationMode || systemUiPanelExpanded || !isDisplayInteractive()) return;
        final int token = ++appBackdropPrearmToken;
        appBackdropPrearmActive = true;
        lastCaptureStartNanos = 0L;
        mainHandler.postDelayed(() -> {
            if (token == appBackdropPrearmToken) appBackdropPrearmActive = false;
        }, 350L);
    }

    void setLauncherAwayHint(boolean away) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setLauncherAwayHint(away));
            return;
        }
        sceneState.setLauncherAwayHint(away);
        updateDesiredScene();
        requestStateCapture(away ? "launcher-away-hint" : "launcher-away-cleared");
    }

    private boolean isHomeWallpaperSettleActive(CaptureScene scene) {
        return scene == CaptureScene.HOME
                && homeWallpaperSettleUntilNanos != 0L
                && System.nanoTime() < homeWallpaperSettleUntilNanos;
    }

    private void beginHomeWallpaperSettle() {
        long delayMs = LiquidDockConfig.load().glass.homeSettleDelayMs;
        final int token = ++homeWallpaperSettleToken;
        homeWallpaperCaptureEpoch++;
        homeWallpaperSettleUntilNanos = System.nanoTime() + delayMs * 1_000_000L;
        // A kick queued while APP was authoritative must not wake up after HOME is selected
        // and bypass the settle gate.  Running APP attempts are rejected by scene revision.
        mainHandler.removeCallbacks(captureKick);
        kickScheduled = false;
        sourceDirty = true;
        lastCaptureStartNanos = 0L;
        logI("HOME wallpaper settle armed delay=" + delayMs + "ms epoch="
                + homeWallpaperCaptureEpoch);
        mainHandler.postDelayed(() -> {
            if (token != homeWallpaperSettleToken) return;
            homeWallpaperSettleUntilNanos = 0L;
            lastCaptureStartNanos = 0L;
            requestStateCapture("home-wallpaper-settled");
        }, delayMs);
    }

    private void cancelHomeWallpaperSettle(String reason) {
        if (homeWallpaperSettleUntilNanos == 0L) return;
        homeWallpaperSettleToken++;
        homeWallpaperSettleUntilNanos = 0L;
        homeWallpaperCaptureEpoch++;
        logI("HOME wallpaper settle cancelled reason=" + reason);
    }

    private boolean isHomeWallpaperResultCurrent(CaptureScene scene, long requestEpoch) {
        return scene != CaptureScene.HOME
                || (requestEpoch == homeWallpaperCaptureEpoch
                    && !isHomeWallpaperSettleActive(scene));
    }

    void setForegroundOwnership(ForegroundOwnership ownership) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setForegroundOwnership(ownership));
            return;
        }
        if (ownership == ForegroundOwnership.EXTERNAL) {
            cancelHomeWallpaperSettle("external-authority");
        }
        sceneState.setForegroundOwnership(ownership);
        updateDesiredScene();
        requestStateCapture("foreground-authority-"
                + ownership.name().toLowerCase(java.util.Locale.ROOT));
    }

    boolean hasExternalForegroundAuthority() {
        return sceneState.externalAppForegroundConfirmed();
    }

    /** A HOME transition is authoritative only after the controller has passed its
     * focus + repeated task-snapshot stability gate. */
    void onAuthoritativeHomeConfirmed() {
        boolean returningFromExternal = launcherWasAway
                || sceneState.externalAppForegroundConfirmed()
                || sceneState.desired() == CaptureScene.APP;
        if (returningFromExternal) beginHomeWallpaperSettle();
        setForegroundOwnership(ForegroundOwnership.HOME);
        sceneState.setExternalAppDockInteraction(false);
        updateDesiredScene();
    }

    /** Launcher genuinely lost window focus (an app came to the front).  This is the
     *  authoritative marker for a real HOME return later. */
    void onLauncherFocusLost() {
        launcherWasAway = true;
        cancelHomeWallpaperSettle("launcher-focus-lost");
        sceneState.setLauncherAwayHint(true);
        // HyperOS can emit GestureToHome during an app-launch transition. That HOME target
        // otherwise outranks lifecycle for 1.5s and keeps serving wallpaper cache even after
        // the Launcher has definitively lost focus. Clear only HOME here. MainHook immediately
        // follows with setLauncherState(true, false), which then resolves the scene to APP.
        if (sceneState.clearGestureTargetIfHome()) {
            logI("Cleared stale HOME gesture target on launcher focus loss");
        }
    }

    /** Launcher gained window focus.  APP→HOME delay is user-configurable via
     *  liquid_home_settle_delay (default 1200 ms).  Spring-backs use 500 ms. */
    void onLauncherFocused() {
        appBackdropPrearmActive = false;
        appBackdropPrearmToken++;
        resetCaptureCircuit("launcher-focus");
        beginObservationBurst();
        boolean wasAway = launcherWasAway;
        launcherWasAway = false;
        long delay = wasAway
                ? LiquidDockConfig.load().glass.homeSettleDelayMs
                : 500L;
        mainHandler.postDelayed(() -> {
            if (!isCaptureAllowed()) return;
            requestStateCapture("focus-home");
        }, delay);
    }

    /** Public entry for MainHook: request a refresh capture (e.g. Dock Folme animation
     *  started/interrupted — the animation keeps the glass's backdrop in sync). */
    void requestCapture(String reason) {
        requestStateCapture(reason);
    }

    /** Open a rotation-stabilization window: keep re-capturing (with signature
     *  convergence) after an orientation change so a transitional black-edge frame
     *  cannot freeze the backdrop.  Called from the launcher configuration hook. */
    void beginRotationStabilize() {
        rotationStabilizeUntilNanos = System.nanoTime()
                + ROTATION_STABILIZE_WINDOW_MS * 1_000_000L;
        lastRotationSignature = -1;
        rotationStabilizeTickPending = false;
        logI("rotation stabilize window opened (" + ROTATION_STABILIZE_WINDOW_MS + "ms)");
    }

    /** After installing a frame, if rotation stabilization is active, compare content
     *  signatures and schedule another capture until the wallpaper converges. */
    private void rotationStabilizeTick(long signature) {
        if (rotationStabilizeUntilNanos == 0) return;
        if (System.nanoTime() >= rotationStabilizeUntilNanos) {
            rotationStabilizeUntilNanos = 0;
            rotationStabilizeTickPending = false;
            logI("rotation stabilize window exhausted signature=" + signature);
            return;
        }
        if (lastRotationSignature == signature) {
            rotationStabilizeUntilNanos = 0;
            rotationStabilizeTickPending = false;
            logI("rotation stabilized signature=" + signature);
            return;
        }
        lastRotationSignature = signature;
        if (rotationStabilizeTickPending) return;
        rotationStabilizeTickPending = true;
        mainHandler.postDelayed(() -> {
            rotationStabilizeTickPending = false;
            if (rotationStabilizeUntilNanos == 0) return;
            requestStateCapture("rotation-stabilize");
        }, ROTATION_STABILIZE_INTERVAL_MS);
    }

    /** Workstation/laptop mode owns a separate DockContainerView background. Keep
     *  normal LiquidDock suspended there; only the exact Recents button temporarily
     *  activates live mode-1 capture for the multitasking transition/view. */
    void setWorkstationMode(boolean enabled) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setWorkstationMode(enabled));
            return;
        }
        if (workstationMode == enabled) return;
        workstationMode = enabled;
        if (enabled) {
            suspendWorkstationGlass("workstation-enter");
            return;
        }

        workstationRecentsSessionToken++;
        workstationRecentsActive = false;
        workstationRecentsWasVisible = false;
        workstationCaptureBurst.stop();
        workstationSuspendWhenBurstSettles = false;
        cancelPendingCaptureWork();
        captureGeneration++;
        appVisualSignatureValid = false;
        dynamicAppActiveUntilNanos = 0L;
        sceneState.setWorkstationSuspended(false, System.nanoTime(), isRecentsVisible(),
                launcherLifecycleKnown, launcherResumed);
        setVisibility(VISIBLE);
        geometrySource.setAlpha(1f);
        nativeBackgroundHiddenByGlass = false;
        sourceDirty = true;
        observationValid = false;
        lastCaptureStartNanos = 0L;
        requestStateCapture("workstation-exit");
    }

    /** Called before Launcher.showOrHideRecent() only in workstation mode. */
    void onWorkstationRecentsButton() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::onWorkstationRecentsButton);
            return;
        }
        if (!workstationMode) return;
        // A second press is the exit toggle. Force a fresh frame and keep a bounded
        // workstation burst alive through the closing animation.
        if (workstationRecentsActive) {
            workstationSuspendWhenBurstSettles = true;
            startWorkstationCaptureBurst("workstation-recents-exit");
            requestStateCapture("workstation-recents-exit");
            logI("workstation Recents exit requested; adaptive capture armed");
            return;
        }

        workstationRecentsActive = true;
        workstationRecentsWasVisible = false;
        final int session = ++workstationRecentsSessionToken;
        workstationSuspendWhenBurstSettles = false;
        startWorkstationCaptureBurst("workstation-recents-enter");
        long now = System.nanoTime();
        // Exact button boundary is authoritative long enough for the overview animation
        // to become visible; once visible, normal Recents visibility owns the scene.
        sceneState.setGestureTarget("RECENTS", now);
        sourceDirty = true;
        observationValid = false;
        lastCaptureStartNanos = 0L;
        requestStateCapture("workstation-recents-enter");

        // Failed/blocked transition safety: if the panel never becomes visible, do not
        // leave the normal glass active over the workstation Dock indefinitely.
        mainHandler.postDelayed(() -> {
            if (session != workstationRecentsSessionToken
                    || !workstationMode || !workstationRecentsActive) return;
            if (isRecentsVisible()) {
                workstationRecentsWasVisible = true;
                return;
            }
            if (!workstationRecentsWasVisible)
                suspendWorkstationGlass("workstation-recents-timeout");
        }, 1800L);
    }

    private void startWorkstationCaptureBurst(String reason) {
        if (!workstationMode) return;
        resetCaptureCircuit(reason);
        workstationCaptureBurst.start();
        cancelPendingCaptureWork();
        appVisualSignatureValid = false;
        dynamicAppActiveUntilNanos = 0L;
        sceneState.setWorkstationSuspended(false, System.nanoTime(), isRecentsVisible(),
                launcherLifecycleKnown, launcherResumed);
        setVisibility(VISIBLE);
        // Never reveal the normal HotSeats background in workstation. The independent
        // workstation Dock remains underneath this glass composition.
        geometrySource.setAlpha(0f);
        nativeBackgroundHiddenByGlass = true;
        sourceDirty = true;
        observationValid = false;
        lastCaptureStartNanos = 0L;
        logI("workstation capture burst started reason=" + reason);
    }

    private void finishWorkstationCaptureBurstIfSettled() {
        if (!workstationMode || workstationCaptureBurst.isActive()) return;
        logI("workstation capture burst stable scene=" + sceneState.desired());
        if (!workstationSuspendWhenBurstSettles) return;
        // If the closing scene is still visibly active, its lifecycle callback will perform
        // the final suspension when it actually disappears.
        if (sceneState.allAppsActive() || isRecentsVisible()) return;
        suspendWorkstationGlass("workstation-background-stable");
    }

    private void suspendWorkstationGlass(String reason) {
        workstationRecentsSessionToken++;
        workstationCaptureBurst.stop();
        workstationSuspendWhenBurstSettles = false;
        cancelPendingCaptureWork();
        captureGeneration++;
        appVisualSignatureValid = false;
        dynamicAppActiveUntilNanos = 0L;
        workstationRecentsActive = false;
        workstationRecentsWasVisible = false;
        sceneState.setWorkstationSuspended(true, System.nanoTime(), isRecentsVisible(),
                launcherLifecycleKnown, launcherResumed);

        Bitmap old = capture;
        capture = null;
        captureShader = null;
        if (old != null && !old.isRecycled()) old.recycle();
        geometrySource.setAlpha(0f);
        nativeBackgroundHiddenByGlass = true;
        setVisibility(INVISIBLE);
        sourceDirty = false;
        invalidate();
        logI("workstation glass suspended reason=" + reason);
    }

    private void requestStateCapture() {
        requestStateCapture("state");
    }

    private void requestStateCapture(String reason) {
        // Dirty is a state fact, not a scheduling fact.  Preserve it even if the Home window is
        // not captureable yet; the next focus/visibility/resume transition will consume it.
        sourceDirty = true;
        updateDesiredScene();
        applyBackdropTransitionBarrier(sceneState.desired(), reason);
        if (isHomeWallpaperSettleActive(sceneState.desired())) {
            logI("HOME wallpaper capture deferred reason=" + reason);
            return;
        }
        if (!isCaptureAllowed()) {
            logCaptureGate(reason);
            return;
        }
        if (capturing || kickScheduled) {
            logI("Liquid capture coalesced reason=" + reason
                    + " capturing=" + capturing
                    + " kickScheduled=" + kickScheduled
                    + " activeAttempt=" + activeCaptureAttempt
                    + " dirty=" + sourceDirty);
            return;
        }
        kickScheduled = true;
        logI("Liquid capture scheduled reason=" + reason);
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
            logI("Liquid capture gated: " + summary);
        }
    }

    private void markCaptureHealthy() {
        captureTimeoutStreak = 0;
        captureFailureStreak = 0;
        captureFailureRetryToken++;
        captureCircuitOpen = false;
    }

    private void scheduleCaptureFailureRetry(String reason, long generation) {
        if (generation != captureGeneration || !attached) return;
        sourceDirty = true;
        int streak = ++captureFailureStreak;
        if (streak > CAPTURE_FAILURE_RETRY_DELAYS_MS.length) {
            captureCircuitOpen = true;
            logW("capture circuit opened after " + streak
                    + " callback failures; waiting for external recovery");
            return;
        }
        final int token = ++captureFailureRetryToken;
        long delay = CAPTURE_FAILURE_RETRY_DELAYS_MS[streak - 1];
        mainHandler.postDelayed(() -> {
            if (token != captureFailureRetryToken || generation != captureGeneration
                    || !attached || captureCircuitOpen || !isCaptureAllowed()) return;
            liveCapture = null;
            requestStateCapture("capture-failure-retry-" + reason);
        }, delay);
    }

    /** A tripped breaker means the current worker is presumed wedged. */
    private void resetCaptureCircuit(String reason) {
        if (!captureCircuitOpen && captureTimeoutStreak == 0 && captureFailureStreak == 0) return;
        boolean wasOpen = captureCircuitOpen;
        captureCircuitOpen = false;
        captureTimeoutStreak = 0;
        captureFailureStreak = 0;
        captureFailureRetryToken++;
        if (wasOpen && attached && captureThread != null) {
            rebuildCaptureWorker("circuit-reset-" + reason);
        }
    }

    /** Remove every queued main/worker capture task; running native work is invalidated. */
    private void cancelPendingCaptureWork() {
        mainHandler.removeCallbacks(captureKick);
        kickScheduled = false;
        sourceDirty = false;
        lastCaptureStartNanos = 0L;
        observationValid = false;
        captureGeneration++;
        capturing = false;
        captureFailureRetryToken++;
        captureFailureStreak = 0;
        Handler worker = captureHandler;
        if (worker != null) worker.removeCallbacksAndMessages(null);
        if (captureTimeout != null) mainHandler.removeCallbacks(captureTimeout);
        captureTimeout = null;
        activeCaptureAttempt = ++captureAttemptSeq;
    }

    /** Abandon a wedged capture worker: a captureDisplay() Binder call can block its
     *  HandlerThread forever (no callback, no exception) — that thread must not be
     *  waited on, it is treated as a zombie and dropped.  A fresh thread + handler +
     *  SF client take over; the next capture runs on the new worker. */
    private void rebuildCaptureWorker(String reason) {
        HandlerThread oldThread = captureThread;
        HandlerThread newThread = new HandlerThread(
                "LiquidDock-WallpaperCapture-" + (++captureWorkerId));
        newThread.start();
        captureThread = newThread;
        captureHandler = new Handler(newThread.getLooper());
        liveCapture = null;
        if (oldThread != null) {
            try {
                oldThread.quitSafely();
            } catch (Throwable ignored) {
            }
        }
        logW("capture worker rebuilt reason=" + reason
                + " worker=" + captureWorkerId);
    }

    /** Single owner of capture state: only the callback whose attempt matches
     *  activeCaptureAttempt may retire it.  A stale callback (from a wedged worker
     *  or a superseded attempt) must never touch capturing/watchdog — it owns
     *  nothing and can only recycle its own result. */
    private boolean retireCaptureAttempt(long attempt) {
        if (activeCaptureAttempt != attempt) {
            return false;
        }
        activeCaptureAttempt = 0L;
        capturing = false;
        if (captureTimeout != null) {
            mainHandler.removeCallbacks(captureTimeout);
            captureTimeout = null;
        }
        return true;
    }

    private void startCapture() {
        final Handler worker = captureHandler;
        if (worker == null || !isCaptureAllowed() || capturing) return;

        final CaptureRequest request = makeCaptureRequest();
        if (request == null) {
            logW("Liquid capture request has no valid Dock/display geometry: "
                    + "dock=" + geometrySource.getWidth() + "x" + geometrySource.getHeight());
            return;
        }

        // The Dock is a floating overlay window (type 2997) that can be summoned over any app.
        // Wallpaper-only capture is wrong there: the glass must refract the app content below.
        // Default to full-display capture; keep wallpaper mode for desktop-only setups via
        // config key "liquid_capture_fullscreen" (false = wallpaper layer only).
        final boolean useFullscreen = fullscreenCapture
                || (workstationMode && workstationCaptureBurst.isActive());

        final long generation = captureGeneration;
        updateDesiredScene();
        final CaptureScene requestScene = sceneState.desired();
        if (isHomeWallpaperSettleActive(requestScene)) {
            sourceDirty = true;
            logI("startCapture deferred by HOME wallpaper settle");
            return;
        }
        final long requestSceneRevision = sceneState.revision();
        final long requestWallpaperTransformRevision = wallpaperTransformRevision;
        final long requestHomeWallpaperCaptureEpoch = homeWallpaperCaptureEpoch;
        final boolean liveHomeBehindFreeform = useFullscreen
                && !workstationMode
                && requestScene == CaptureScene.HOME
                && freeformLayerResolver.hasVisibleFreeformTasks();
        CaptureSourcePolicy.Source selectedSource;
        if (!useFullscreen || (workstationMode && requestScene == CaptureScene.APP)) {
            selectedSource = CaptureSourcePolicy.Source.WALLPAPER;
        } else if (workstationMode) {
            selectedSource = CaptureSourcePolicy.sourceForWorkstationScene(requestScene, false);
        } else {
            selectedSource = CaptureSourcePolicy.sourceFor(
                    requestScene, false, isRecentsVisible(), liveHomeBehindFreeform);
        }
        FullDisplayExclusions selectedExclusions = FullDisplayExclusions.NONE;
        if (selectedSource == CaptureSourcePolicy.Source.FULL_DISPLAY) {
            if (!refreshDockWindowSurfaceCache("capture")) {
                if (workstationMode) {
                    selectedSource = CaptureSourcePolicy.Source.WALLPAPER;
                } else {
                    sourceDirty = true;
                    scheduleCaptureFailureRetry("dock-surface-unavailable", generation);
                    return;
                }
            }
            if (selectedSource == CaptureSourcePolicy.Source.FULL_DISPLAY
                    && dockExcludeRecovery.suspended()) {
                if (workstationMode) {
                    selectedSource = CaptureSourcePolicy.Source.WALLPAPER;
                } else {
                    sourceDirty = true;
                    logW("FULL_DISPLAY capture suspended after repeated Dock exclude EINVAL; "
                            + "waiting for a new Floating Dock generation");
                    return;
                }
            }
            if (selectedSource == CaptureSourcePolicy.Source.FULL_DISPLAY
                    && dockExcludeRecovery.includeSurfaceControl()
                    && !hasValidDockWindowSurface()) {
                dockExcludeRecovery.onInvalidArgument();
                invalidateDockWindowSurfaceHandle();
                logW("Dock SurfaceControl failed freshness/validity check; "
                        + "using fresh layer-name-only exclusion");
            }
            if (selectedSource == CaptureSourcePolicy.Source.FULL_DISPLAY) {
                selectedExclusions = resolveFullDisplayExclusions();
                if (!selectedExclusions.safe) {
                    logW("full-display capture blocked: visible freeform task has no resolvable "
                            + "SurfaceFlinger layer; wallpaper fallback");
                    selectedSource = CaptureSourcePolicy.Source.WALLPAPER;
                    selectedExclusions = FullDisplayExclusions.NONE;
                }
            }
        }
        final CaptureSourcePolicy.Source requestedSource = selectedSource;
        final FullDisplayExclusions requestFullDisplayExclusions = selectedExclusions;
        capturing = true;
        lastCaptureStartNanos = System.nanoTime();
        final long attempt = ++captureAttemptSeq;
        activeCaptureAttempt = attempt;
        // Per-attempt watchdog: a captureDisplay() Binder call on the worker thread can
        // wedge forever after rotation (no callback, no exception) — the old worker
        // thread then blocks every later capture.  Timeout abandons the attempt AND the
        // whole worker thread (zombie), then rebuilds a fresh one; it never touches
        // captureGeneration (rotation/scene context stays valid).
        if (captureTimeout != null) mainHandler.removeCallbacks(captureTimeout);
        captureTimeout = () -> {
            if (activeCaptureAttempt != attempt) return;
            captureTimeout = null;
            activeCaptureAttempt = ++captureAttemptSeq;
            capturing = false;
            kickScheduled = false;
            sourceDirty = true;
            lastCaptureStartNanos = 0L;
            int streak = ++captureTimeoutStreak;
            if (streak >= CAPTURE_TIMEOUT_BREAKER_LIMIT) {
                captureCircuitOpen = true;
                logW("capture circuit opened after " + streak
                        + " consecutive timeouts; waiting for external recovery");
                return;
            }
            rebuildCaptureWorker("capture-timeout-" + streak);
            long backoff = CAPTURE_TIMEOUT_BACKOFF_MS[Math.min(
                    streak - 1, CAPTURE_TIMEOUT_BACKOFF_MS.length - 1)];
            mainHandler.postDelayed(() -> {
                if (!attached || captureCircuitOpen || !isCaptureAllowed()) return;
                requestStateCapture("capture-timeout-retry");
            }, backoff);
        };
        mainHandler.postDelayed(captureTimeout, 600L);
        // Every mutable exclusion input is snapshotted before the worker request.
        boolean needsDockExclude = useFullscreen
                && requestedSource == CaptureSourcePolicy.Source.FULL_DISPLAY;
        final float requestCaptureScale = captureScale;
        final android.view.SurfaceControl requestDockWindowSurface =
                needsDockExclude && dockExcludeRecovery.includeSurfaceControl()
                        ? dockWindowSurface : null;
        final String requestDockWindowLayerName = dockWindowLayerName;
        final String requestDragLayerName = dragLayerName;
        final FullDisplayExclusions requestExclusions =
                needsDockExclude
                        && requestFullDisplayExclusions.layerNames == null
                        && requestDockWindowLayerName != null
                        ? new FullDisplayExclusions(
                                CaptureExclusionNames.merge(
                                        requestDockWindowLayerName,
                                        requestDragLayerName,
                                        java.util.Collections.emptyList()),
                                true)
                        : requestFullDisplayExclusions;
        final int requestWallpaperId = requestedSource == CaptureSourcePolicy.Source.WALLPAPER
                ? currentWallpaperId() : -1;
        logI("capture source=" + requestedSource + " attempt display=" + request.displayId
                + " strip=" + request.stripRect + " tile=" + request.tileRect
                + " scale=" + requestCaptureScale
                + " exclude=" + (requestDockWindowSurface != null)
                + " scene=" + requestScene + " wallpaperId=" + requestWallpaperId);

        worker.post(() -> {
            Bitmap strip = null;
            CroppedFrame cropped = null;
            Throwable failure = null;
            try {
                LiveScreenCapture client = liveCapture;
                if (client == null) {
                    client = new LiveScreenCapture(
                            requestCaptureScale, geometrySource.getContext().getClassLoader());
                    liveCapture = client;
                }
                if (useFullscreen) {
                    final FullDisplayExclusions fullDisplayExclusions =
                            requestExclusions;
                    android.view.SurfaceControl[] excludes = null;
                    if (requestedSource == CaptureSourcePolicy.Source.FULL_DISPLAY
                            && requestDockWindowSurface != null) {
                        excludes = new android.view.SurfaceControl[]{requestDockWindowSurface};
                    }
                    final CaptureRequest req = request;
                    final LiveScreenCapture captureClient = client;
                    final LiveScreenCapture.CaptureCallback captureCb = new LiveScreenCapture.CaptureCallback() {
                        @Override public void onResult(Bitmap bmp) {
                            handleCaptureResult(bmp, req, generation, attempt,
                                    requestScene, requestSceneRevision,
                                    requestWallpaperId, requestWallpaperTransformRevision,
                                    requestHomeWallpaperCaptureEpoch);
                        }
                        @Override public void onError(Throwable error) {
                            mainHandler.post(() -> {
                                if (generation != captureGeneration
                                        || activeCaptureAttempt != attempt) return;
                                liveCapture = null;
                                if (requestedSource == CaptureSourcePolicy.Source.FULL_DISPLAY
                                        && LiveScreenCapture.isInvalidArgumentStatus(error)) {
                                    dockExcludeRecovery.onInvalidArgument();
                                    invalidateDockWindowSurfaceHandle();
                                    retireCaptureAttempt(attempt);
                                    Log.e(TAG, "async FULL_DISPLAY exclude rejected; layer="
                                            + requestDockWindowLayerName
                                            + " surfaceHandle="
                                            + (requestDockWindowSurface != null), error);
                                    sourceDirty = true;
                                    lastCaptureStartNanos = 0L;
                                    if (dockExcludeRecovery.suspended()) {
                                        logW("Dock exclude name-only retry also returned EINVAL; "
                                                + "suspending FULL_DISPLAY until layer generation changes");
                                        return;
                                    }
                                    requestStateCapture("dock-exclude-einval-name-only");
                                    return;
                                }
                                invalidateDockWindowSurfaceCache();
                                retireCaptureAttempt(attempt);
                                Log.e(TAG, "async capture failed source=" + requestedSource, error);
                                scheduleCaptureFailureRetry("async-error", generation);
                            });
                        }
                    };

                    CaptureSourcePolicy.Source actualSource = requestedSource;
                    boolean wallpaperMode = actualSource == CaptureSourcePolicy.Source.WALLPAPER;
                    String[] excludeNames = actualSource == CaptureSourcePolicy.Source.FULL_DISPLAY
                            ? fullDisplayExclusions.layerNames : null;
                    if (wallpaperMode
                            && !(workstationMode && workstationCaptureBurst.isActive())
                            && tryServeWallpaperFromCache(
                            req, requestScene, requestSceneRevision, attempt,
                            requestWallpaperId, requestWallpaperTransformRevision)) {
                        return;
                    }
                    logI("capture source=" + actualSource
                            + " names=" + java.util.Arrays.toString(
                                    wallpaperMode ? new String[]{"Wallpaper BBQ wrapper"} : excludeNames)
                            + " crop=" + req.stripRect + " scale=" + requestCaptureScale
                            + " scene=" + requestScene + " revision=" + requestSceneRevision);
                    captureClient.captureScreenAsync(req.stripRect, requestCaptureScale, req.displayId,
                            wallpaperMode ? null : excludes, excludeNames,
                            wallpaperMode ? 2 : 1, captureCb);
                    return;
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
                // Stale callback: owns nothing.
                if (activeCaptureAttempt != attempt) {
                    if (frame != null) frame.recycle();
                    return;
                }
                updateDesiredScene();
                if (generation != captureGeneration
                        || !sceneState.matches(requestScene, requestSceneRevision)
                        || !isWallpaperIdentityCurrent(requestScene, requestWallpaperId)
                        || !isWallpaperTransformCurrent(requestScene, requestWallpaperTransformRevision)
                        || !isHomeWallpaperResultCurrent(requestScene,
                                requestHomeWallpaperCaptureEpoch)
                        || !isRequestOrientationCurrent(request) || !isCaptureAllowed()) {
                    if (frame != null) frame.recycle();
                    retireCaptureAttempt(attempt);
                    logI("Liquid capture result discarded: generation="
                            + (generation == captureGeneration)
                            + " allowed=" + isCaptureAllowed());
                    if (isCaptureAllowed()) requestStateCapture("stale-orientation-result");
                    return;
                }

                retireCaptureAttempt(attempt);
                if (captureFailure != null) {
                    Log.e(TAG, "HyperOS wallpaper-only capture failed", captureFailure);
                    scheduleCaptureFailureRetry("sync-error", generation);
                } else if (frame != null) {
                    nullFrameLogged = false;
                    installCapture(frame, "sync", requestScene);
                } else {
                    if (!nullFrameLogged) {
                        nullFrameLogged = true;
                        logW("HyperOS captureMode(2) wallpaper path returned no buffer");
                    }
                    scheduleCaptureFailureRetry("sync-null", generation);
                }

                // Autonomous cadence in captureKick drives the next frame; this catches any
                // state change that arrived while the capture was in flight.
                if (sourceDirty) requestStateCapture();
            });
        });
    }

    /** Shared completion path for async captures: crop off-main, validate/commit on main. */
    private void handleCaptureResult(Bitmap strip, CaptureRequest request, long generation,
                                     long attempt, CaptureScene requestScene,
                                     long requestSceneRevision,
                                     int requestWallpaperId,
                                     long requestWallpaperTransformRevision,
                                     long requestHomeWallpaperCaptureEpoch) {
        Bitmap cacheCandidate = null;
        try {
            if (activeCaptureAttempt != attempt) {
                if (strip != null && !strip.isRecycled()) strip.recycle();
                return;
            }
            // Black-frame guard: on HyperOS captureMode(2) against the wallpaper layer
            // returns status=0 with a valid-sized but PURE BLACK buffer while a non-home app
            // is in front (verified: Dock pull-up over an app).  Installing such a frame
            // freezes a black backdrop forever; discard it and keep the previous frame.
            VisualProbe visualProbe = probeBitmap(strip, blackFrameThreshold);
            if (strip == null || strip.isRecycled()) {
                throw new IllegalStateException("async capture returned no usable bitmap");
            }
            logI("frame " + strip.getWidth() + "x" + strip.getHeight()
                    + " stripRect=" + request.stripRect + " mean=" + visualProbe.meanChannel);
            if (visualProbe.nearBlack) {
                if (!strip.isRecycled()) strip.recycle();
                mainHandler.post(() -> {
                    updateDesiredScene();
                    if (activeCaptureAttempt != attempt) return;
                    if (generation != captureGeneration
                            || !sceneState.matches(requestScene, requestSceneRevision)
                            || !isWallpaperIdentityCurrent(requestScene, requestWallpaperId)
                            || !isWallpaperTransformCurrent(requestScene,
                                    requestWallpaperTransformRevision)
                        || !isHomeWallpaperResultCurrent(requestScene,
                                requestHomeWallpaperCaptureEpoch)
                            || !isRequestOrientationCurrent(request)) {
                        retireCaptureAttempt(attempt);
                        requestStateCapture("stale-black-frame");
                        return;
                    }
                    retireCaptureAttempt(attempt);
                    lastRotationSignature = -1;
                    if (blackFrameLogCount++ < 5) {
                        logW("black frame discarded attempt=" + attempt
                                + ", keeping previous backdrop");
                    }
                    if (blackFrameRetryRotation != request.rotation) {
                        blackFrameRetryRotation = request.rotation;
                        blackFrameRetryCount = 0;
                    }
                    if (blackFrameRetryCount < BLACK_FRAME_RETRY_DELAYS_MS.length) {
                        long delay = BLACK_FRAME_RETRY_DELAYS_MS[blackFrameRetryCount++];
                        sourceDirty = true;
                        mainHandler.postDelayed(() -> {
                            if (generation != captureGeneration || !isCaptureAllowed()) return;
                            liveCapture = null;
                            requestStateCapture("black-frame-retry");
                        }, delay);
                    } else {
                        scheduleCaptureFailureRetry("black-frame-exhausted", generation);
                    }
                });
                return;
            }

            if (CaptureSourcePolicy.sourceFor(requestScene)
                    == CaptureSourcePolicy.Source.WALLPAPER) {
                try {
                    cacheCandidate = strip.copy(Bitmap.Config.ARGB_8888, false);
                } catch (Throwable ignored) {
                    cacheCandidate = null;
                }
            }
            CroppedFrame cropped = cropWallpaperTile(strip, request.stripRect,
                    request.tileRect, request.dockRect);
            strip = null;
            final CroppedFrame frame = cropped;
            final Bitmap wallpaperCandidate = cacheCandidate;
            cacheCandidate = null;
            mainHandler.post(() -> {
                updateDesiredScene();
                if (activeCaptureAttempt != attempt) {
                    if (frame != null) frame.recycle();
                    recycleBitmap(wallpaperCandidate);
                    return;
                }
                if (generation != captureGeneration
                        || !sceneState.matches(requestScene, requestSceneRevision)
                        || !isWallpaperIdentityCurrent(requestScene, requestWallpaperId)
                        || !isWallpaperTransformCurrent(requestScene,
                                requestWallpaperTransformRevision)
                        || !isHomeWallpaperResultCurrent(requestScene,
                                requestHomeWallpaperCaptureEpoch)
                        || !isRequestOrientationCurrent(request)
                        || !isCaptureAllowed()) {
                    if (frame != null) frame.recycle();
                    recycleBitmap(wallpaperCandidate);
                    retireCaptureAttempt(attempt);
                    if (isCaptureAllowed()) requestStateCapture("stale-scene-result");
                    return;
                }
                retireCaptureAttempt(attempt);
                blackFrameRetryCount = 0;
                blackFrameRetryRotation = request.rotation;
                nullFrameLogged = false;
                if (wallpaperCandidate != null) {
                    commitWallpaperCache(wallpaperCandidate, request,
                            requestWallpaperId, requestWallpaperTransformRevision);
                }
                if (requestScene == CaptureScene.APP && dynamicAppCapture) {
                    updateDynamicAppActivity(visualProbe.signature);
                }
                if (frame != null) {
                    installCapture(frame, "async", requestScene);
                } else {
                    scheduleCaptureFailureRetry("async-null-crop", generation);
                    return;
                }
                if (CaptureSourcePolicy.sourceFor(requestScene)
                        == CaptureSourcePolicy.Source.WALLPAPER) wallpaperCacheReady = true;
                rotationStabilizeTick(visualProbe.signature);
                if (workstationMode && workstationCaptureBurst.isActive()) {
                    if (workstationCaptureBurst.onFrame(visualProbe.signature)) {
                        requestStateCapture("workstation-background-changing");
                    } else {
                        finishWorkstationCaptureBurstIfSettled();
                    }
                }
                // Config is hot-reloaded by the 1s ticker; no duplicate counter needed here.
                if (sourceDirty) requestStateCapture();
                if (dynamicAppCapture && requestScene == CaptureScene.APP
                        && sceneState.desired() == CaptureScene.APP
                        && isCaptureAllowed() && isGlassActuallyVisible()) {
                    requestStateCapture("dynamic-app-continue");
                }
                // Recents deliberately does not require isGlassActuallyVisible(): HyperOS hides
                // the Dock window in overview, but the live backdrop still has to follow cards.
                // requestStateCapture() still applies the global screen-power/SystemUI gates.
                if (!workstationMode && isRecentsVisible()) {
                    requestStateCapture("recents-continue");
                }
            });
        } catch (Throwable e) {
            if (strip != null && !strip.isRecycled()) strip.recycle();
            recycleBitmap(cacheCandidate);
            mainHandler.post(() -> {
                if (generation != captureGeneration || activeCaptureAttempt != attempt) return;
                retireCaptureAttempt(attempt);
                Log.e(TAG, "async capture crop failed", e);
                scheduleCaptureFailureRetry("async-crop", generation);
            });
        }
    }

    private static void recycleBitmap(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
    }

    private int currentWallpaperId() {
        try {
            return WallpaperManager.getInstance(getContext())
                    .getWallpaperId(WallpaperManager.FLAG_SYSTEM);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private boolean isWallpaperIdentityCurrent(CaptureScene scene, int requestWallpaperId) {
        if (CaptureSourcePolicy.sourceFor(scene) != CaptureSourcePolicy.Source.WALLPAPER
                || requestWallpaperId < 0) return true;
        int current = currentWallpaperId();
        return current < 0 || current == requestWallpaperId;
    }

    private boolean isWallpaperTransformCurrent(CaptureScene scene, long revision) {
        return CaptureSourcePolicy.sourceFor(scene) != CaptureSourcePolicy.Source.WALLPAPER
                || wallpaperTransformRevision == revision;
    }

    private void updateDynamicAppActivity(long signature) {
        long now = System.nanoTime();
        if (appVisualSignatureValid) {
            int difference = 0;
            long changed = lastAppVisualSignature ^ signature;
            for (int i = 0; i < 16; i++) {
                int oldValue = (int) ((lastAppVisualSignature >>> (i * 4)) & 0xF);
                int newValue = (int) ((signature >>> (i * 4)) & 0xF);
                difference += Math.abs(oldValue - newValue);
            }
            // Roughly one luminance step per sample is enough to classify video/game motion.
            if (difference >= dynamicMotionDifferenceThreshold
                    || Long.bitCount(changed) >= dynamicMotionBitThreshold) {
                dynamicAppActiveUntilNanos = now + dynamicMotionHoldNanos;
            }
        }
        lastAppVisualSignature = signature;
        appVisualSignatureValid = true;
    }

    private boolean isRequestOrientationCurrent(CaptureRequest request) {
        Display display = geometrySource.getDisplay();
        if (display == null) return false;
        display.getRealSize(tmpDisplaySize);
        int orientation = tmpDisplaySize.x >= tmpDisplaySize.y ? 1 : 0;
        return request.rotation == display.getRotation()
                && request.orientationIndex == orientation
                && request.displayWidth == tmpDisplaySize.x
                && request.displayHeight == tmpDisplaySize.y;
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
        // Use THIS glass view's own on-screen geometry for the strip, not the (possibly
        // smaller/offset) native background: the visible glass can extend above the
        // background's top (rounded inset / native layout differences), and any part of
        // the glass without captured backdrop samples renders black.
        int glassLeft = tmpDockLocation[0];
        int glassTop = tmpDockLocation[1];
        getLocationOnScreen(tmpDockLocation);
        int gx = Math.min(glassLeft, tmpDockLocation[0]);
        int gy = Math.min(glassTop, tmpDockLocation[1]);
        int gw = Math.max(geometrySource.getWidth(), getWidth());
        int gh = Math.max(geometrySource.getHeight(), getHeight());
        Rect displayRect = new Rect(0, 0, tmpDisplaySize.x, tmpDisplaySize.y);
        Rect dockRect = new Rect(gx, gy, gx + Math.max(1, gw), gy + Math.max(1, gh));
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

        // Capture only Dock + refraction bleed. Reading the entire display width here wastes
        // compositor bandwidth and can pin the GPU during Dock animations.
        Rect stripRect = new Rect(tileRect);
        if (stripRect.isEmpty()) return null;

        return new CaptureRequest(display.getDisplayId(), display.getRotation(),
                tmpDisplaySize.x, tmpDisplaySize.y,
                tmpDisplaySize.x >= tmpDisplaySize.y ? 1 : 0,
                stripRect, tileRect, dockRect);
    }

    /** Serve a transform-scoped wallpaper cache copy without sharing a recyclable bitmap. */
    private boolean tryServeWallpaperFromCache(CaptureRequest req,
                                                CaptureScene requestScene,
                                                long requestSceneRevision,
                                                long attempt,
                                                int requestWallpaperId,
                                                long requestWallpaperTransformRevision) {
        if (rotationStabilizeUntilNanos != 0) return false;
        final CroppedFrame frame;
        synchronized (wallpaperCacheLock) {
            if (!wallpaperCacheReady || wallpaperStripCache == null
                    || wallpaperStripCache.isRecycled() || cacheStripRect == null
                    || cacheWallpaperTransformRevision != requestWallpaperTransformRevision
                    || requestWallpaperTransformRevision != wallpaperTransformRevision
                    || (requestWallpaperId >= 0 && cacheWallpaperId != requestWallpaperId)
                    || !isWallpaperIdentityCurrent(requestScene, requestWallpaperId)
                    || req.rotation != cacheRotation
                    || req.displayWidth != cacheDisplayWidth
                    || req.displayHeight != cacheDisplayHeight
                    || !cacheStripRect.contains(req.stripRect)) return false;
            frame = copyWallpaperCacheFrameLocked(req);
        }
        if (frame == null) return false;
        final long generation = captureGeneration;
        mainHandler.post(() -> {
            updateDesiredScene();
            if (activeCaptureAttempt != attempt) {
                frame.recycle();
                return;
            }
            if (generation != captureGeneration
                    || !sceneState.matches(requestScene, requestSceneRevision)
                    || !isWallpaperIdentityCurrent(requestScene, requestWallpaperId)
                    || !isWallpaperTransformCurrent(requestScene, requestWallpaperTransformRevision)
                    || !isRequestOrientationCurrent(req) || !isCaptureAllowed()) {
                frame.recycle();
                retireCaptureAttempt(attempt);
                if (isCaptureAllowed()) requestStateCapture("stale-cache-result");
                return;
            }
            retireCaptureAttempt(attempt);
            installCapture(frame, "cache", requestScene);
            if (sourceDirty) requestStateCapture();
            if (!workstationMode && isRecentsVisible()) {
                requestStateCapture("recents-continue");
            }
        });
        return true;
    }

    /** Main-thread commit after request identity has passed every stale check. */
    private void commitWallpaperCache(Bitmap candidate, CaptureRequest req,
                                      int requestWallpaperId,
                                      long requestWallpaperTransformRevision) {
        if (candidate == null || candidate.isRecycled()) return;
        if (requestWallpaperTransformRevision != wallpaperTransformRevision) {
            recycleBitmap(candidate);
            return;
        }
        synchronized (wallpaperCacheLock) {
            Bitmap old = wallpaperStripCache;
            wallpaperStripCache = candidate;
            cacheStripRect = new Rect(req.stripRect);
            cacheRotation = req.rotation;
            cacheDisplayWidth = req.displayWidth;
            cacheDisplayHeight = req.displayHeight;
            cacheWallpaperTransformRevision = requestWallpaperTransformRevision;
            cacheWallpaperId = requestWallpaperId;
            if (old != null && old != candidate && old != capture && !old.isRecycled()) old.recycle();
        }
    }

    /**
     * Full-Dock luminance/motion probe.  Never judge the frame from a few isolated pixels:
     * downsample the entire captured Dock strip to at most 16x8 software pixels, then use all
     * of them for the black-frame mean.  The 64-bit motion signature keeps the historical
     * 16-nibble format by averaging the thumbnail into an 8x2 spatial grid, so existing motion
     * thresholds retain their meaning while local changes anywhere in the Dock remain visible.
     */
    private static VisualProbe probeBitmap(Bitmap bmp, int blackThreshold) {
        if (bmp == null || bmp.isRecycled()) return new VisualProbe(true, 0L, 0);
        Bitmap readable = bmp;
        try {
            int sourceW = bmp.getWidth();
            int sourceH = bmp.getHeight();
            if (sourceW <= 0 || sourceH <= 0) return new VisualProbe(true, 0L, 0);

            int probeW = Math.max(1, Math.min(16, sourceW));
            int probeH = Math.max(1, Math.min(8, sourceH));
            if (bmp.getConfig() == Bitmap.Config.HARDWARE
                    || sourceW != probeW || sourceH != probeH) {
                try {
                    // BitmapCompat handles HARDWARE input and returns a tiny readable bitmap;
                    // this is an area downsample of the whole Dock, not a local crop/probe.
                    readable = BitmapCompat.createScaledBitmap(
                            bmp, probeW, probeH, null, false);
                } catch (Throwable scaleError) {
                    // Only software bitmaps can safely use the platform fallback.  For a
                    // HARDWARE bitmap, accepting the frame is safer than allocating a full-size
                    // ARGB copy just for diagnostics.
                    if (bmp.getConfig() == Bitmap.Config.HARDWARE) {
                        logW("Unable to downsample hardware capture probe; accepting frame", scaleError);
                        return new VisualProbe(false, 0L, 0);
                    }
                    readable = Bitmap.createScaledBitmap(bmp, probeW, probeH, true);
                }
                if (readable == null) return new VisualProbe(false, 0L, 0);
            }

            int w = readable.getWidth(), h = readable.getHeight();
            long channelSum = 0L;
            int channelCount = 0;
            int[] zoneLumaSum = new int[16];
            int[] zonePixelCount = new int[16];

            for (int y = 0; y < h; y++) {
                int zoneY = Math.min(1, y * 2 / Math.max(1, h));
                for (int x = 0; x < w; x++) {
                    int c = readable.getPixel(x, y);
                    int r = c >> 16 & 0xFF;
                    int g = c >> 8 & 0xFF;
                    int b = c & 0xFF;
                    channelSum += r + g + b;
                    channelCount += 3;
                    int luminance = (r * 3 + g * 6 + b) / 10;
                    int zoneX = Math.min(7, x * 8 / Math.max(1, w));
                    int zone = zoneY * 8 + zoneX;
                    zoneLumaSum[zone] += luminance;
                    zonePixelCount[zone]++;
                }
            }

            long signature = 0L;
            for (int zone = 0; zone < 16; zone++) {
                int n = zonePixelCount[zone];
                int meanLuma = n > 0 ? zoneLumaSum[zone] / n : 0;
                int sample = Math.min(15, meanLuma >> 4);
                signature |= (long) sample << (zone * 4);
            }

            int meanChannel = channelCount > 0 ? (int) (channelSum / channelCount) : 0;
            return new VisualProbe(channelCount > 0 && meanChannel < blackThreshold,
                    signature, meanChannel);
        } catch (Throwable error) {
            logW("Unable to probe capture luminance; accepting frame", error);
            return new VisualProbe(false, 0L, 0);
        } finally {
            if (readable != bmp && readable != null && !readable.isRecycled()) {
                readable.recycle();
            }
        }
    }

    private static final class VisualProbe {
        final boolean nearBlack;
        final long signature;
        final int meanChannel;
        VisualProbe(boolean nearBlack, long signature, int meanChannel) {
            this.nearBlack = nearBlack;
            this.signature = signature;
            this.meanChannel = meanChannel;
        }
    }

    private static CroppedFrame cropWallpaperTile(Bitmap strip, Rect stripRect,
                                                   Rect tileRect, Rect dockRect) {
        return cropWallpaperTile(strip, stripRect, tileRect, dockRect, true);
    }

    private static CroppedFrame cropWallpaperTile(Bitmap strip, Rect stripRect,
                                                   Rect tileRect, Rect dockRect,
                                                   boolean recycleStrip) {
        if (strip == null || strip.isRecycled() || stripRect.isEmpty() || tileRect.isEmpty()) {
            if (recycleStrip && strip != null && !strip.isRecycled()) strip.recycle();
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
        if (recycleStrip && tile != strip && !strip.isRecycled()) strip.recycle();

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
    /** Apply MIUI system self-blur to the View's own rendered content.
     *  <strong>Deprecated for frosted glass:</strong> self-blur softens edges
     *  and highlights along with the interior.  Prefer the {@code "shader"}
     *  or {@code "material"} blur methods for crisp-edged frosted glass.
     *  This method is kept for backward compatibility but no longer called
     *  from the default blur pipeline. */
    @SuppressWarnings("unused")
    private void applySystemSelfBlur(int radius) {
        // Deprecated: self-blur softens edges.
        // Kept for potential future use with setMiViewBlurMode background blur.
    }

    private void installCapture(CroppedFrame frame, String from, CaptureScene sourceScene) {
        markCaptureHealthy();
        installedCaptureScene = sourceScene;
        // Do not make the native Dock transparent until a real wallpaper-only frame exists.
        // This avoids the fully-transparent Dock failure mode when hidden capture APIs reject.
        if (!nativeBackgroundHiddenByGlass) {
            geometrySource.setAlpha(0f);
            nativeBackgroundHiddenByGlass = true;
        }

        Bitmap old = capture;
        capture = frame.bitmap;
        captureSampleOffsetX = frame.sampleOffsetX;
        captureSampleOffsetY = frame.sampleOffsetY;
        captureSourceWidth = Math.max(1f, frame.sourceWidth);
        captureSourceHeight = Math.max(1f, frame.sourceHeight);
        captureShader = new BitmapShader(capture, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        logI("install[" + from + "] frame " + frame.bitmap.getWidth() + "x" + frame.bitmap.getHeight()
                + " src=" + frame.sourceWidth + "x" + frame.sourceHeight
                + " off=" + frame.sampleOffsetX + "," + frame.sampleOffsetY);
        invalidate();
        if (old != null && old != capture && old != wallpaperStripCache
                && !old.isRecycled()) old.recycle();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (captureShader == null || capture == null || capture.isRecycled()
                || getWidth() <= 0 || getHeight() <= 0) {
            if (drawFailLogged < 2) { drawFailLogged++;
                logW("onDraw skip: shader=" + (captureShader != null)
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
        refraction.setFloatUniform("depthEffect", glassDepthEffect);
        refraction.setFloatUniform("chromaticAberration", chromaticAberration);
        refraction.setFloatUniform("blurRadius", blurRadius);
        refraction.setFloatUniform("shaderBlurEnabled", advancedMaterialActive ? 0f : 1f);
        refraction.setFloatUniform("highlightEnabled", advancedMaterialActive ? 0f : 1f);
        refraction.setFloatUniform("highlightAlpha", glassHighlightAlpha);
        // Prismal liquid-glass model parameters (ported from styropyr0/Prismal);
        // GUI-configurable via liquid_* settings.
        float density = displayDensity;
        refraction.setFloatUniform("thickness", Math.max(1f, glassThickness * density));
        refraction.setFloatUniform("ior", Math.max(1.001f, Math.min(2f, glassIor)));
        refraction.setFloatUniform("normalStrength", Math.max(0f, Math.min(5f, glassNormalStrength)));
        refraction.setFloatUniform("liquidDome", Math.max(0f, Math.min(2f, glassLiquidDome)));
        refraction.setFloatUniform("lensRefractionPx", Math.max(0f, glassLensRefraction * density));
        refraction.setFloatUniform("highlightWidth", glassHighlightWidth);
        refraction.setFloatUniform("brightness", glassBrightness);
        refraction.setFloatUniform("specularSharp", glassSpecularSharp);
        refraction.setFloatUniform("specularStrength", glassSpecularStrength);
        refraction.setFloatUniform("rimLight", glassRimLight);
        refraction.setFloatUniform("causticStrength", glassCaustics);
        refraction.setFloatUniform("edgeBand", glassEdgeBand);
        refraction.setFloatUniform("screenOffset", captureSampleOffsetX, captureSampleOffsetY);
        float csx = capture.getWidth() / Math.max(1f, captureSourceWidth);
        float csy = capture.getHeight() / Math.max(1f, captureSourceHeight);
        refraction.setFloatUniform("captureSize", capture.getWidth(), capture.getHeight());
        refraction.setFloatUniform("captureScale", csx, csy);
        glassPaint.setShader(refraction);
        Path shape = obtainDrawShapePath();
        int bodySave = canvas.save();
        if (advancedMaterialActive) {
            // Keep the self-blurred RenderNode rectangular. DockLiquidGlassHostView clips
            // the composed child afterwards, fixing the unblurred upper-left round corner.
        } else {
            canvas.clipPath(shape);
        }
        canvas.drawRect(0, 0, getWidth(), getHeight(), glassPaint);
        tintPaint.setColor(Color.argb(tintPaint.getAlpha(),
                glassTintR, glassTintG, glassTintB));
        canvas.drawPath(shape, tintPaint);
        canvas.restoreToCount(bodySave);
    }

    private Path obtainDrawShapePath() {
        int w = getWidth(), h = getHeight();
        int radiusBits = Float.floatToIntBits(cornerRadius);
        int cpBits = Float.floatToIntBits(squircleCp);
        if (w != cachedDrawShapeW || h != cachedDrawShapeH
                || radiusBits != cachedDrawRadiusBits || cpBits != cachedDrawCpBits
                || squircle != cachedDrawSquircle) {
            cachedDrawShape.reset();
            cachedDrawShape.set(shapePath(w, h, cornerRadius));
            cachedDrawShapeW = w;
            cachedDrawShapeH = h;
            cachedDrawRadiusBits = radiusBits;
            cachedDrawCpBits = cpBits;
            cachedDrawSquircle = squircle;
        }
        return cachedDrawShape;
    }

    private Path shapePathInset(float width, float height, float radius, float inset) {
        float safeInset = Math.max(0f, Math.min(inset, Math.min(width, height) * .5f - .5f));
        RectF r = new RectF(.5f + safeInset, .5f + safeInset,
                width - .5f - safeInset, height - .5f - safeInset);
        float adjustedRadius = Math.max(0f, radius - safeInset);
        Path p = new Path();
        if (!squircle || adjustedRadius <= 1f) {
            p.addRoundRect(r, adjustedRadius, adjustedRadius, Path.Direction.CW);
            return p;
        }
        float a = Math.min(adjustedRadius, Math.min(r.width(), r.height()) * .5f);
        float c = a * squircleCp;
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
        final int rotation;
        final int displayWidth;
        final int displayHeight;
        final int orientationIndex;
        final Rect stripRect;
        final Rect tileRect;
        final Rect dockRect;

        CaptureRequest(int displayId, int rotation, int displayWidth, int displayHeight,
                       int orientationIndex, Rect stripRect, Rect tileRect, Rect dockRect) {
            this.displayId = displayId;
            this.rotation = rotation;
            this.displayWidth = displayWidth;
            this.displayHeight = displayHeight;
            this.orientationIndex = orientationIndex;
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
