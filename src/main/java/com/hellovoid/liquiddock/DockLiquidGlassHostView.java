package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RuntimeShader;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * Exact Dock-sized composition host.
 *
 * The glass child deliberately stays rectangular while SurfaceFlinger self-blurs its
 * RenderNode. Final round/squircle clipping and ADVANCED sharp highlight happen here,
 * after the child render pass, so highlight/stroke never enter the self-blurred RenderNode.
 */
final class DockLiquidGlassHostView extends FrameLayout {
    private static final String HIGHLIGHT_SHADER =
            "uniform float2 size;"
          + "uniform float2 offset;"
          + "uniform float4 cornerRadii;"
          + "uniform float squircleEnabled;"
          + "uniform float squircleCp;"
          + "uniform float refractionHeight;"
          + "uniform float liquidDome;"
          + "uniform float normalStrength;"
          + "uniform float specularSharp;"
          + "uniform float specularStrength;"
          + "uniform float rimLight;"
          + "uniform float causticStrength;"
          + "uniform float edgeBand;"
          + "uniform float highlightWidth;"
          + "uniform float highlightAlpha;"
          + "float radiusAt(float2 p,float4 r){if(p.x>=0){return p.y<=0?r.y:r.z;}return p.y<=0?r.x:r.w;}"
          + "float sdRound(float2 p,float2 h,float r){float2 q=abs(p)-(h-float2(r));return length(max(q,0.0))-r+min(max(q.x,q.y),0.0);}"
          + "float2 gradRound(float2 p,float2 h,float r){float2 q=abs(p)-(h-float2(r));float2 s=sign(p);"
          + "s.x=s.x==0.0?1.0:s.x;s.y=s.y==0.0?1.0:s.y;if(q.x>=0.0||q.y>=0.0)return s*normalize(max(q,0.0001));"
          + "float gx=step(q.y,q.x);return s*float2(gx,1.0-gx);}"
          // Same symmetric cubic corner used by DockShapePath: P0=(0,r),
          // P1=(0,r-c), P2=(r-c,0), P3=(r,0), c=r*squircleCp.
          + "float2 bezierPoint(float t,float r,float c){float u=1.0-t;"
          + "float2 p0=float2(0.0,r);float2 p1=float2(0.0,r-c);float2 p2=float2(r-c,0.0);float2 p3=float2(r,0.0);"
          + "return u*u*u*p0+3.0*u*u*t*p1+3.0*u*t*t*p2+t*t*t*p3;}"
          + "float2 bezierD1(float t,float r,float c){float u=1.0-t;"
          + "float2 p0=float2(0.0,r);float2 p1=float2(0.0,r-c);float2 p2=float2(r-c,0.0);float2 p3=float2(r,0.0);"
          + "return 3.0*u*u*(p1-p0)+6.0*u*t*(p2-p1)+3.0*t*t*(p3-p2);}"
          + "float2 bezierD2(float t,float r,float c){float u=1.0-t;"
          + "float2 p0=float2(0.0,r);float2 p1=float2(0.0,r-c);float2 p2=float2(r-c,0.0);float2 p3=float2(r,0.0);"
          + "return 6.0*u*(p2-2.0*p1+p0)+6.0*t*(p3-2.0*p2+p1);}"
          + "float refineBezierT(float t,float2 q,float r,float c){float2 b=bezierPoint(t,r,c);float2 d=bezierD1(t,r,c);"
          + "float2 dd=bezierD2(t,r,c);float f=dot(b-q,d);float fp=dot(d,d)+dot(b-q,dd);"
          + "float den=abs(fp)>0.0001?fp:(fp>=0.0?0.0001:-0.0001);return clamp(t-f/den,0.0,1.0);}"
          + "float3 sdBezierSquircle(float2 p,float2 h,float r,float cp){float2 ap=abs(p);float2 inset=h-ap;float2 s=sign(p);"
          + "s.x=s.x==0.0?1.0:s.x;s.y=s.y==0.0?1.0:s.y;"
          + "if(inset.x>=r||inset.y>=r){float2 d=ap-h;float sd=max(d.x,d.y);"
          + "float2 g=d.x>d.y?float2(s.x,0.0):float2(0.0,s.y);return float3(sd,g.x,g.y);}"
          + "float c=r*clamp(cp,0.05,0.95);float2 q=inset;"
          + "float t=clamp(0.5+0.5*(q.x-q.y)/max(r,1.0),0.0,1.0);"
          + "t=refineBezierT(t,q,r,c);t=refineBezierT(t,q,r,c);t=refineBezierT(t,q,r,c);t=refineBezierT(t,q,r,c);"
          + "float2 b=bezierPoint(t,r,c);float2 d=bezierD1(t,r,c);float dl=max(length(d),0.0001);"
          + "float2 nInside=float2(-d.y,d.x)/dl;float dist=length(q-b);float side=dot(q-b,nInside);"
          + "float sd=side>=0.0?-dist:dist;float2 g=s*nInside;return float3(sd,g.x,g.y);}"
          + "float3 shapeField(float2 p,float2 h,float r){if(squircleEnabled>0.5&&r>1.0)return sdBezierSquircle(p,h,r,squircleCp);"
          + "float sd=sdRound(p,h,r);float2 g=gradRound(p,h,r);return float3(sd,g.x,g.y);}"
          + "float sdShape(float2 p,float2 h,float r){return shapeField(p,h,r).x;}"
          + "float getHeightFromDist(float dist,float tw){float t=clamp(-dist/tw,0.0,1.0);return sqrt(max(0.0,2.0*t-t*t));}"
          + "float2 computeGradientHeight(float2 p,float2 halfSz,float cr,float tw){"
          + "float s=1.0;float hpx=getHeightFromDist(sdShape(p+float2(s,0.0),halfSz,cr),tw);"
          + "float hnx=getHeightFromDist(sdShape(p-float2(s,0.0),halfSz,cr),tw);"
          + "float hpy=getHeightFromDist(sdShape(p+float2(0.0,s),halfSz,cr),tw);"
          + "float hny=getHeightFromDist(sdShape(p-float2(0.0,s),halfSz,cr),tw);"
          + "return float2((hpx-hnx)*0.5,(hpy-hny)*0.5);}"
          + "half4 main(float2 coord){"
          // DockShapePath uses [.5, .5, width-.5, height-.5], so its true half-size is
          // half a pixel smaller than the View bounds. Matching that avoids a clipped halo.
          + "float2 hs=max(size*0.5-float2(0.5),float2(0.5));float2 cc=(coord+offset)-size*0.5;float2 pPx=cc;"
          + "float r=radiusAt(cc,cornerRadii);float cr=min(r,min(hs.x,hs.y));"
          + "float3 field=shapeField(cc,hs,cr);float sd=field.x;float2 gradLens=field.yz;"
          + "float minDim=min(hs.x,hs.y);"
          + "float dome=clamp(liquidDome,0.0,2.0);"
          + "float tw=max(refractionHeight*(1.0+0.38*dome),1.0);tw=min(tw,minDim*0.98);"
          + "float hSig=getHeightFromDist(sd,tw);"
          + "float2 gradHSig=computeGradientHeight(pPx,hs,cr,tw);"
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
          + "float3 Lp=normalize(float3(-0.5,-0.8,1.45));"
          + "float3 Hp=normalize(Lp+V);"
          + "float sh=max(specularSharp,1.0);float sp=1.52*max(specularStrength,0.0);"
          + "float specP=pow(max(dot(N,Hp),0.0),sh)*sp;"
          + "specP*=(0.32+0.68*height);"
          + "float bandFracR=max(edgeBand,0.005)*max(0.1,highlightWidth);"
          + "float bandR=clamp(minDim*bandFracR,0.5,min(12.0,minDim*0.1));"
          + "float shellRim=1.0-smoothstep(bandR*0.06,bandR,max(edgeDist,0.0));"
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
          + "hl=clamp(hl*highlightAlpha,0.0,1.0);"
          + "float a=max(hl.r,max(hl.g,hl.b));"
          + "return half4(hl,a);}";

    private final Path clipPath = new Path();
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RuntimeShader highlightShader;
    private DockLiquidGlassView glassView;
    private float radius;
    private boolean squircle;
    private float squircleCp = .58f;
    private boolean shapeDirty = true;
    private float highlightAlpha = 1f;
    private float highlightWidth = 1f;
    private float normalStrength = 1.15f;
    private float dome = 1f;
    private float specularSharp = 88f;
    private float specularStrength = 1.05f;
    private float rimLight = 1f;
    private float caustics = .28f;
    private float edgeBand = .032f;
    private LiquidBlurMode activeBlurBackend = LiquidBlurMode.SHADER;

    DockLiquidGlassHostView(Context context) {
        super(context);
        RuntimeShader shader = null;
        try {
            shader = new RuntimeShader(HIGHLIGHT_SHADER);
            highlightPaint.setShader(shader);
            highlightPaint.setBlendMode(BlendMode.PLUS);
        } catch (Throwable t) {
            MainHook.log("[DC] dynamic highlight shader unavailable: " + t);
        }
        highlightShader = shader;
        setWillNotDraw(true);
        setClipChildren(false);
        setClipToPadding(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setLayers(DockLiquidGlassView glass) {
        removeAllViews();
        glassView = glass;
        glass.setActiveBlurBackendListener(this::setActiveBlurBackend);
        addView(glass, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    DockLiquidGlassView glassView() {
        return glassView;
    }

    void setGeometry(float radius, boolean squircle, float cp) {
        float nextRadius = Math.max(0f, radius);
        float nextCp = Math.max(.05f, Math.min(.95f, cp));
        boolean changed = this.radius != nextRadius || this.squircle != squircle
                || this.squircleCp != nextCp;
        this.radius = nextRadius;
        this.squircle = squircle;
        this.squircleCp = nextCp;
        if (changed) shapeDirty = true;
        if (glassView != null) glassView.setGlassGeometry(this.radius, squircle, this.squircleCp);
        DockStrokeRenderer.updateRadius(this, this.radius);
        if (changed) invalidate();
    }

    void setRadius(float radius) {
        setGeometry(radius, squircle, squircleCp);
    }

    /** Refresh optical parameters without changing the geometry chosen by the owner. */
    void reloadOpticsPreservingGeometry(LiquidDockConfig.Glass glass) {
        setHighlight(glass.highlightAlpha, glass.highlightWidth);
        setHighlightParams(glass.normalStrength, glass.dome, glass.specularSharp,
                glass.specularStrength, glass.rimLight, glass.caustics,
                glass.edgeBand, glass.highlightAlpha);
    }

    void reloadOpticsOnly(LiquidDockConfig.Dock dock, LiquidDockConfig.Glass glass) {
        setGeometry(radius, dock.squircle, dock.squircleCp);
        reloadOpticsPreservingGeometry(glass);
    }

    void reloadOverlay(LiquidDockConfig.Dock dock, LiquidDockConfig.Glass glass) {
        reloadOpticsOnly(dock, glass);
        DockStrokeRenderer.configure(this, dock, radius);
    }

    private void setHighlight(float alpha, float width) {
        float nextAlpha = Math.max(0f, Math.min(2f, alpha));
        float nextWidth = Math.max(0f, width);
        if (highlightAlpha == nextAlpha && highlightWidth == nextWidth) return;
        highlightAlpha = nextAlpha;
        highlightWidth = nextWidth;
        invalidate();
    }

    private void setHighlightParams(float normalStrength, float dome, float specularSharp,
                                    float specularStrength, float rimLight, float caustics,
                                    float edgeBand, float highlightAlpha) {
        float nn = Math.max(0f, Math.min(3f, normalStrength));
        float nd = Math.max(0f, Math.min(2f, dome));
        float nsh = Math.max(1f, Math.min(400f, specularSharp));
        float nss = Math.max(0f, Math.min(5f, specularStrength));
        float nr = Math.max(0f, Math.min(3f, rimLight));
        float nc = Math.max(0f, Math.min(1f, caustics));
        float ne = Math.max(.005f, Math.min(.1f, edgeBand));
        float na = Math.max(0f, Math.min(2f, highlightAlpha));
        if (this.normalStrength == nn && this.dome == nd && this.specularSharp == nsh
                && this.specularStrength == nss && this.rimLight == nr
                && this.caustics == nc && this.edgeBand == ne && this.highlightAlpha == na) {
            return;
        }
        this.normalStrength = nn;
        this.dome = nd;
        this.specularSharp = nsh;
        this.specularStrength = nss;
        this.rimLight = nr;
        this.caustics = nc;
        this.edgeBand = ne;
        this.highlightAlpha = na;
        invalidate();
    }

    private void setActiveBlurBackend(LiquidBlurMode mode) {
        LiquidBlurMode next = mode == null ? LiquidBlurMode.SHADER : mode;
        if (activeBlurBackend == next) return;
        activeBlurBackend = next;
        invalidate();
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

    private void drawAdvancedHighlight(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w <= 1 || h <= 1 || highlightShader == null || highlightAlpha <= 0f
                || activeBlurBackend != LiquidBlurMode.ADVANCED_MATERIAL) {
            return;
        }
        highlightShader.setFloatUniform("size", w, h);
        highlightShader.setFloatUniform("offset", 0f, 0f);
        highlightShader.setFloatUniform("cornerRadii", radius, radius, radius, radius);
        highlightShader.setFloatUniform("squircleEnabled", squircle ? 1f : 0f);
        highlightShader.setFloatUniform("squircleCp", squircleCp);
        highlightShader.setFloatUniform("refractionHeight", Math.max(1f, Math.min(h * .48f, 140f)));
        highlightShader.setFloatUniform("liquidDome", dome);
        highlightShader.setFloatUniform("normalStrength", normalStrength);
        highlightShader.setFloatUniform("specularSharp", specularSharp);
        highlightShader.setFloatUniform("specularStrength", specularStrength);
        highlightShader.setFloatUniform("rimLight", rimLight);
        highlightShader.setFloatUniform("causticStrength", caustics);
        highlightShader.setFloatUniform("edgeBand", edgeBand);
        highlightShader.setFloatUniform("highlightWidth", highlightWidth);
        highlightShader.setFloatUniform("highlightAlpha", highlightAlpha);
        canvas.drawRect(0f, 0f, w, h, highlightPaint);
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        ensureClipPath();
        if (clipPath.isEmpty()) return;
        int save = canvas.save();
        canvas.clipPath(clipPath);
        super.dispatchDraw(canvas);
        drawAdvancedHighlight(canvas);
        canvas.restoreToCount(save);
    }
}
