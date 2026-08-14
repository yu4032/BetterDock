from pathlib import Path
import re


def replace_once(path, old, new):
    p = Path(path)
    s = p.read_text()
    n = s.count(old)
    if n != 1:
        raise SystemExit(f"{path}: expected one match, got {n}: {old[:100]!r}")
    p.write_text(s.replace(old, new, 1))


def regex_once(path, pattern, replacement):
    p = Path(path)
    s = p.read_text()
    out, n = re.subn(pattern, replacement, s, count=1, flags=re.S)
    if n != 1:
        raise SystemExit(f"{path}: regex expected one match, got {n}: {pattern[:100]!r}")
    p.write_text(out)


glass = "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java"
factory = "src/main/java/com/hellovoid/liquiddock/LiquidGlassFactory.java"
main = "src/main/java/com/hellovoid/liquiddock/MainHook.java"

# --- DockLiquidGlassView: one shader, runtime backend toggle ---
replace_once(glass,
'''      + "uniform float blurRadius;"\n      + "uniform float2 screenOffset;"''',
'''      + "uniform float blurRadius;"\n      + "uniform float shaderBlurEnabled;"\n      + "uniform float2 screenOffset;"''')
replace_once(glass,
'''      + "half4 blurred(float2 p){"\n      + "if(blurRadius<=0.5){return source(p);}"''',
'''      + "half4 blurred(float2 p){"\n      + "if(shaderBlurEnabled < 0.5 || blurRadius<=0.5){return source(p);}"''')

replace_once(glass,
'''    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);\n    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);\n    private final int blurRadius;''',
'''    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);\n    private int blurRadius;\n    private LiquidBlurMode requestedBlurMode = LiquidBlurMode.SHADER;\n    private LiquidBlurMode activeBlurBackend = LiquidBlurMode.SHADER;\n    private boolean advancedMaterialActive;\n    private boolean advancedMaterialUnavailableForProcess;''')
replace_once(glass,
'''    // Canvas stroke highlight opacity multiplier (liquid_highlight_alpha)\n    private float glassHighlightAlpha = 1.0f;\n''', '')
replace_once(glass,
'''    private LinearGradient cachedHighlightGradient;\n    private int cachedHighlightW = -1, cachedHighlightH = -1, cachedHighlightAlphaBits;\n''', '')
replace_once(glass, '    private final int captureBleedPx;\n', '    private int captureBleedPx;\n')

# Replace the old glass-owned stroke/RenderNode outline block with backend control.
regex_once(glass,
    r'''    void setGlassGeometry\(float radius, boolean useSquircle, float cp\) \{.*?\n    \}\n\n    /\*\* Called by MainHook's Launcher lifecycle hooks\.''',
'''    void setGlassGeometry(float radius, boolean useSquircle, float cp) {
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
        activeBlurBackend = LiquidBlurBackendPolicy.activeBackend(requestedBlurMode, applied);
        advancedMaterialActive = activeBlurBackend == LiquidBlurMode.ADVANCED_MATERIAL;
        if (!advancedMaterialActive && requestedBlurMode == LiquidBlurMode.ADVANCED_MATERIAL) {
            MiBlurBridge.clearContentBlur(this);
        }
        invalidate();
    }

    /** Called by MainHook's Launcher lifecycle hooks.''')

# Constructor no longer clips this RenderNode; the host performs final clipping.
replace_once(glass,
'''        setWillNotDraw(false);\n        applyRoundedOutline();''',
'''        setWillNotDraw(false);\n        setClipToOutline(false);''')

# Clear compositor state on detach.
replace_once(glass,
'''    @Override protected void onDetachedFromWindow() {\n        attached = false;''',
'''    @Override protected void onDetachedFromWindow() {\n        attached = false;\n        MiBlurBridge.clearContentBlur(this);''')

# Hot reload: radius/backend update + delegate crisp overlay appearance to host.
replace_once(glass,
'''            setTintColor(cfg.tintR, cfg.tintG, cfg.tintB);\n            tintPaint.setAlpha(cfg.tintAlpha);\n            setHighlightWidth(cfg.highlightWidth);\n            setHighlightAlpha(cfg.highlightAlpha);\n            setDockStrokeConfig(fullConfig.dock);\n            setAppearance(cfg.depthEffect, cfg.brightness, cfg.specularSharp,''',
'''            setTintColor(cfg.tintR, cfg.tintG, cfg.tintB);\n            tintPaint.setAlpha(cfg.tintAlpha);\n            setHighlightWidth(cfg.highlightWidth);\n            float blurScale = cfg.dimensionsDp ? displayDensity : 1f;\n            setBlurRadiusPx(Math.round(cfg.blur * blurScale));\n            setBlurMode(cfg.blurMode);\n            if (getParent() instanceof DockLiquidGlassHostView) {\n                ((DockLiquidGlassHostView) getParent()).reloadOverlay(fullConfig.dock, cfg);\n            }\n            setAppearance(cfg.depthEffect, cfg.brightness, cfg.specularSharp,''')

# Remove the now-dead highlight alpha setter if present.
p = Path(glass)
s = p.read_text()
s, n = re.subn(r'''\n    void setHighlightAlpha\(float alpha\) \{.*?\n    \}\n''', '\n', s, count=1, flags=re.S)
if n not in (0, 1):
    raise SystemExit(f"unexpected setHighlightAlpha count {n}")
p.write_text(s)

# Shader uniform and final drawing: advanced child stays rectangular; host clips after blur.
replace_once(glass,
'''        refraction.setFloatUniform("blurRadius", blurRadius);''',
'''        refraction.setFloatUniform("blurRadius", blurRadius);\n        refraction.setFloatUniform("shaderBlurEnabled", advancedMaterialActive ? 0f : 1f);''')
regex_once(glass,
    r'''        Path shape = obtainDrawShapePath\(\);\n        canvas\.save\(\);\n        canvas\.clipPath\(shape\);\n        canvas\.drawRect\(0, 0, getWidth\(\), getHeight\(\), glassPaint\);\n        tintPaint\.setColor\(Color\.argb\(tintPaint\.getAlpha\(\),\n                glassTintR, glassTintG, glassTintB\)\);\n        canvas\.drawPath\(shape, tintPaint\);\n        canvas\.restore\(\);\n        // Draw the highlight.*?\n        canvas\.restore\(\);''',
'''        Path shape = obtainDrawShapePath();
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
        canvas.restoreToCount(bodySave);''')

# Remove cached Canvas-highlight gradient helper; the overlay owns it now.
p = Path(glass)
s = p.read_text()
s, n = re.subn(r'''\n    private LinearGradient obtainHighlightGradient\(\) \{.*?\n    \}\n\n    private Path shapePathInset''',
                  '\n    private Path shapePathInset', s, count=1, flags=re.S)
if n != 1:
    raise SystemExit(f"expected one obtainHighlightGradient block, got {n}")
p.write_text(s)

# --- Factory: requested mode applies to body; Canvas highlight/stroke belong to overlay. ---
replace_once(factory,
'''        view.setAppearance(config.depthEffect, config.brightness, config.specularSharp,\n                config.specularStrength, config.rimLight, config.caustics, config.edgeBand);\n        view.setHighlightAlpha(config.highlightAlpha);\n        view.setDockStrokeConfig(dockConfig);\n        view.setRecentsPrearmDistanceDp(config.recentsPrearmDistance);''',
'''        view.setAppearance(config.depthEffect, config.brightness, config.specularSharp,\n                config.specularStrength, config.rimLight, config.caustics, config.edgeBand);\n        view.setBlurMode(config.blurMode);\n        view.setRecentsPrearmDistanceDp(config.recentsPrearmDistance);''')

# --- MainHook: one assembly helper shared by both setupViews branches. ---
replace_once(main,
'''    private static DockLiquidGlassView liquidGlassView;''',
'''    private static DockLiquidGlassView liquidGlassView;\n    private static DockLiquidGlassHostView liquidGlassHostView;''')

# Both legacy checks now validate the host's actual attachment, not glass->host parentage.
p = Path(main)
s = p.read_text()
old = 'if (liquidGlassView != null && liquidGlassView.getParent() != null)'
if s.count(old) != 2:
    raise SystemExit(f"MainHook: expected 2 glass attachment checks, got {s.count(old)}")
s = s.replace(old, 'if (liquidGlassHostView != null && liquidGlassHostView.getParent() != null)')
p.write_text(s)

# Liquid-only creation + insertion.
replace_once(main,
'''                            liquidGlassView = LiquidGlassFactory.create(vBg, workspace,\n                                    config.glass, strokeCfg, false, 0.58f);\n                            liquidGlassView.setId(View.generateViewId());''',
'''                            int bgIndex = parent.indexOfChild(vBg);\n                            liquidGlassView = installLiquidGlassLayer(parent, Math.max(0, bgIndex), gv,\n                                    vBg, workspace, config, false, 0.58f);''')
replace_once(main,
'''                            int bgIndex = parent.indexOfChild(vBg);\n                            parent.addView(liquidGlassView, Math.max(0, bgIndex),\n                                    new FrameLayout.LayoutParams(1, 1, gv));\n                            syncAll(vBg);''',
'''                            syncAll(vBg);''')

# Full creation + insertion.
replace_once(main,
'''                                liquidGlassView = LiquidGlassFactory.create(oldBg, workspace, current.glass, c2, c2.squircle, sqCp);\n                                liquidGlassView.setId(View.generateViewId());''',
'''                                int bgIdx = parent.indexOfChild(oldBg);\n                                liquidGlassView = installLiquidGlassLayer(parent, Math.max(0, bgIdx), gv,\n                                        oldBg, workspace, current, c2.squircle, sqCp);''')
replace_once(main,
'''                                int bgIdx = parent.indexOfChild(oldBg);\n                                parent.addView(liquidGlassView, Math.max(0, bgIdx),\n                                        new FrameLayout.LayoutParams(1, 1, gv));\n''', '')

# Hide the complete layer in workstation mode so the sharp overlay cannot be orphaned.
replace_once(main,
'''                                if (liquidGlassView != null)\n                                    liquidGlassView.setWorkstationMode(true);\n                                return r;''',
'''                                if (liquidGlassView != null)\n                                    liquidGlassView.setWorkstationMode(true);\n                                if (liquidGlassHostView != null)\n                                    liquidGlassHostView.setVisibility(View.GONE);\n                                return r;''')

# Shared assembly helper before lifecycle hooks.
replace_once(main,
'''    // ── lifecycle / capture hooks ────────────────────────────────────''',
'''    private static DockLiquidGlassView installLiquidGlassLayer(
            ViewGroup parent, int insertIndex, int gravity,
            View background, View workspace, LiquidDockConfig config,
            boolean squircle, float squircleCp) {
        DockLiquidGlassView glass = LiquidGlassFactory.create(background, workspace,
                config.glass, config.dock, squircle, squircleCp);
        glass.setId(View.generateViewId());

        DockStrokeOverlayView overlay = new DockStrokeOverlayView(parent.getContext());
        overlay.setId(View.generateViewId());
        DockLiquidGlassHostView host = new DockLiquidGlassHostView(parent.getContext());
        host.setId(View.generateViewId());
        host.setLayers(glass, overlay);

        float radius = bgR;
        try {
            Object value = HookUtil.getField(background, "mCornerRadius");
            if (value instanceof Float) radius = (Float) value;
        } catch (Throwable ignored) {}
        overlay.reload(config.dock, config.glass, radius);
        host.setGeometry(radius, squircle, squircleCp);
        overlay.setGeometry(radius, squircle, squircleCp);

        parent.addView(host, insertIndex,
                new FrameLayout.LayoutParams(1, 1, gravity));
        liquidGlassHostView = host;
        liquidGlassView = glass;
        return glass;
    }

    // ── lifecycle / capture hooks ────────────────────────────────────''')

# syncAll now sizes/updates the host. Keep a legacy glass fallback for safety.
regex_once(main,
    r'''            if \(liquidGlassView != null\) \{\n                ViewGroup\.LayoutParams glp = liquidGlassView\.getLayoutParams\(\);\n                if \(glp != null\) \{ if \(glp\.width != bgW \|\| glp\.height != bgH\) \{\n                    glp\.width = bgW; glp\.height = bgH; liquidGlassView\.setLayoutParams\(glp\); \} \}\n                liquidGlassView\.setGlassRadius\(bgR\);\n                liquidGlassView\.invalidate\(\);\n            \}''',
'''            if (liquidGlassHostView != null) {
                liquidGlassHostView.setVisibility(workstationMode ? View.GONE : View.VISIBLE);
                ViewGroup.LayoutParams hlp = liquidGlassHostView.getLayoutParams();
                if (hlp != null && (hlp.width != bgW || hlp.height != bgH)) {
                    hlp.width = bgW;
                    hlp.height = bgH;
                    liquidGlassHostView.setLayoutParams(hlp);
                }
                liquidGlassHostView.setRadius(bgR);
                liquidGlassHostView.invalidate();
            } else if (liquidGlassView != null) {
                // Compatibility fallback for a stale pre-host instance during Launcher setup.
                ViewGroup.LayoutParams glp = liquidGlassView.getLayoutParams();
                if (glp != null && (glp.width != bgW || glp.height != bgH)) {
                    glp.width = bgW;
                    glp.height = bgH;
                    liquidGlassView.setLayoutParams(glp);
                }
                liquidGlassView.setGlassRadius(bgR);
                liquidGlassView.invalidate();
            }''')

# The fast-return must account for host-only state.
replace_once(main,
'''        if (liquidGlassView == null && shadowView == null) return;''',
'''        if (liquidGlassHostView == null && liquidGlassView == null && shadowView == null) return;''')

# Production contract checks before writing files are already performed by the replacements.
for path, needles in {
    glass: [
        'uniform float shaderBlurEnabled',
        'shaderBlurEnabled < 0.5',
        'MiBlurBridge.applyContentBlur',
        'LiquidBlurBackendPolicy.activeBackend',
        'if (advancedMaterialActive)',
    ],
    main: ['DockLiquidGlassHostView', 'installLiquidGlassLayer('],
}.items():
    text = Path(path).read_text()
    for needle in needles:
        if needle not in text:
            raise SystemExit(f"{path}: required contract missing: {needle}")
if 'setDockStrokeConfig(fullConfig.dock)' in Path(glass).read_text():
    raise SystemExit('glass still owns hot-reloaded foreground stroke')
