from pathlib import Path


def rep(path, old, new):
    p = Path(path)
    s = p.read_text()
    n = s.count(old)
    if n != 1:
        raise SystemExit(f"{path}: expected one match, got {n}: {old[:120]!r}")
    p.write_text(s.replace(old, new, 1))


glass = "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java"
host = "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassHostView.java"
overlay = "src/main/java/com/hellovoid/liquiddock/DockStrokeOverlayView.java"

rep(glass,
'''      + "uniform float highlightWidth;"
      + "uniform float brightness;"''',
'''      + "uniform float highlightWidth;"
      + "uniform float highlightEnabled;"
      + "uniform float highlightAlpha;"
      + "uniform float brightness;"''')

rep(glass,
'''      + "float specP=pow(max(dot(N,Hp),0.0),sh)*sp;"
      + "specP*=(0.32+0.68*height);"
      + "color+=specP*float3(0.99,0.993,1.0);"
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
      + "return half4(color,1.0);}";''',
'''      + "float specP=pow(max(dot(N,Hp),0.0),sh)*sp;"
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
      + "return half4(color,1.0);}";''')

rep(glass,
'''    private LiquidBlurMode requestedBlurMode = LiquidBlurMode.SHADER;
    private LiquidBlurMode activeBlurBackend = LiquidBlurMode.SHADER;
    private boolean advancedMaterialActive;''',
'''    interface ActiveBlurBackendListener {
        void onActiveBlurBackendChanged(LiquidBlurMode mode);
    }

    private LiquidBlurMode requestedBlurMode = LiquidBlurMode.SHADER;
    private LiquidBlurMode activeBlurBackend = LiquidBlurMode.SHADER;
    private ActiveBlurBackendListener activeBlurBackendListener;
    private boolean advancedMaterialActive;''')

rep(glass,
'''    private float glassHighlightWidth = 1.0f;
    // Glass tint color''',
'''    private float glassHighlightWidth = 1.0f;
    private float glassHighlightAlpha = 1.0f;
    // Glass tint color''')

rep(glass,
'''    void setBlurMode(LiquidBlurMode mode) {
        LiquidBlurMode next = mode == null ? LiquidBlurMode.SHADER : mode;
        if (requestedBlurMode == next
                && (next != LiquidBlurMode.ADVANCED_MATERIAL
                    || advancedMaterialActive || advancedMaterialUnavailableForProcess)) {
            return;
        }
        requestedBlurMode = next;
        updateBlurBackend();
    }

    void setBlurRadiusPx''',
'''    void setBlurMode(LiquidBlurMode mode) {
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

    void setBlurRadiusPx''')

rep(glass,
'''    private void updateBlurBackend() {
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
        activeBlurBackend = LiquidBlurBackendPolicy.activeBackend(requestedBlurMode, applied);
        advancedMaterialActive = activeBlurBackend == LiquidBlurMode.ADVANCED_MATERIAL;
        if (!advancedMaterialActive && requestedBlurMode == LiquidBlurMode.ADVANCED_MATERIAL) {
            MiBlurBridge.clearContentBlur(this);
        }
        invalidate();
    }''',
'''    private void updateBlurBackend() {
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
    }''')

rep(glass,
'''        MiBlurBridge.clearContentBlur(this);
        advancedMaterialActive = false;
        activeBlurBackend = LiquidBlurMode.SHADER;''',
'''        MiBlurBridge.clearContentBlur(this);
        setActiveBlurBackendState(LiquidBlurMode.SHADER);''')

rep(glass,
'''    void setAppearance(float depthEffect, float brightness, float specularSharp,
                       float specularStrength, float rimLight, float caustics, float edgeBand) {
        float nd = Math.max(0f, Math.min(1f, depthEffect));
        float nb = Math.max(0.5f, Math.min(2f, brightness));
        float ns = Math.max(1f, Math.min(400f, specularSharp));
        float nst = Math.max(0f, Math.min(5f, specularStrength));
        float nr = Math.max(0f, Math.min(3f, rimLight));
        float nc = Math.max(0f, Math.min(1f, caustics));
        float ne = Math.max(0.005f, Math.min(0.1f, edgeBand));
        if (nd == glassDepthEffect && nb == glassBrightness && ns == glassSpecularSharp
                && nst == glassSpecularStrength && nr == glassRimLight
                && nc == glassCaustics && ne == glassEdgeBand) return;
        glassDepthEffect = nd;
        glassBrightness = nb;
        glassSpecularSharp = ns;
        glassSpecularStrength = nst;
        glassRimLight = nr;
        glassCaustics = nc;
        glassEdgeBand = ne;
        invalidate();
    }''',
'''    void setAppearance(float depthEffect, float brightness, float specularSharp,
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
    }''')

rep(glass,
'''            setAppearance(cfg.depthEffect, cfg.brightness, cfg.specularSharp,
                    cfg.specularStrength, cfg.rimLight, cfg.caustics, cfg.edgeBand);''',
'''            setAppearance(cfg.depthEffect, cfg.brightness, cfg.specularSharp,
                    cfg.specularStrength, cfg.rimLight, cfg.caustics, cfg.edgeBand,
                    cfg.highlightAlpha);''')

rep(glass,
'''        refraction.setFloatUniform("blurRadius", blurRadius);
        refraction.setFloatUniform("shaderBlurEnabled", advancedMaterialActive ? 0f : 1f);''',
'''        refraction.setFloatUniform("blurRadius", blurRadius);
        refraction.setFloatUniform("shaderBlurEnabled", advancedMaterialActive ? 0f : 1f);
        refraction.setFloatUniform("highlightEnabled", advancedMaterialActive ? 0f : 1f);
        refraction.setFloatUniform("highlightAlpha", glassHighlightAlpha);''')

rep(host,
'''        glassView = glass;
        overlayView = overlay;
        addView(glass, new LayoutParams(''',
'''        glassView = glass;
        overlayView = overlay;
        glass.setActiveBlurBackendListener(mode -> {
            if (overlayView != null) overlayView.setActiveBlurBackend(mode);
        });
        addView(glass, new LayoutParams(''')

rep(overlay,
'''    private float highlightWidth = 1f;
    private final float density;''',
'''    private float highlightWidth = 1f;
    private LiquidBlurMode activeBlurBackend = LiquidBlurMode.SHADER;
    private final float density;''')

rep(overlay,
'''    void setHighlight(float alpha, float width) {
        highlightAlpha = Math.max(0f, Math.min(2f, alpha));
        highlightWidth = Math.max(0f, width);
        invalidate();
    }

    void reload''',
'''    void setHighlight(float alpha, float width) {
        highlightAlpha = Math.max(0f, Math.min(2f, alpha));
        highlightWidth = Math.max(0f, width);
        invalidate();
    }

    void setActiveBlurBackend(LiquidBlurMode mode) {
        LiquidBlurMode next = mode == null ? LiquidBlurMode.SHADER : mode;
        if (activeBlurBackend == next) return;
        activeBlurBackend = next;
        invalidate();
    }

    void reload''')

print("task1 dynamic highlight routing patch applied")
