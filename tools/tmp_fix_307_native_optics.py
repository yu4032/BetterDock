from pathlib import Path

ROOT = Path('.')


def once(text, old, new, label):
    n = text.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected 1, found {n}')
    return text.replace(old, new, 1)

# Host: add an optics reload that cannot mutate the caller-owned geometry.
p = ROOT / 'src/main/java/com/hellovoid/liquiddock/DockLiquidGlassHostView.java'
s = p.read_text()
old = '''    void reloadOpticsOnly(LiquidDockConfig.Dock dock, LiquidDockConfig.Glass glass) {\n        setGeometry(radius, dock.squircle, dock.squircleCp);\n        setHighlight(glass.highlightAlpha, glass.highlightWidth);\n        setHighlightParams(glass.normalStrength, glass.dome, glass.specularSharp,\n                glass.specularStrength, glass.rimLight, glass.caustics,\n                glass.edgeBand, glass.highlightAlpha);\n    }\n\n    void reloadOverlay(LiquidDockConfig.Dock dock, LiquidDockConfig.Glass glass) {\n        reloadOpticsOnly(dock, glass);\n        DockStrokeRenderer.configure(this, dock, radius);\n    }\n'''
new = '''    /** Refresh optical parameters without changing the geometry chosen by the owner. */\n    void reloadOpticsPreservingGeometry(LiquidDockConfig.Glass glass) {\n        setHighlight(glass.highlightAlpha, glass.highlightWidth);\n        setHighlightParams(glass.normalStrength, glass.dome, glass.specularSharp,\n                glass.specularStrength, glass.rimLight, glass.caustics,\n                glass.edgeBand, glass.highlightAlpha);\n    }\n\n    void reloadOpticsOnly(LiquidDockConfig.Dock dock, LiquidDockConfig.Glass glass) {\n        setGeometry(radius, dock.squircle, dock.squircleCp);\n        reloadOpticsPreservingGeometry(glass);\n    }\n\n    void reloadOverlay(LiquidDockConfig.Dock dock, LiquidDockConfig.Glass glass) {\n        reloadOpticsOnly(dock, glass);\n        DockStrokeRenderer.configure(this, dock, radius);\n    }\n'''
s = once(s, old, new, 'host optics block')
p.write_text(s)

# 307: native material geometry owns refraction/highlight. Custom stroke geometry is separate.
p = ROOT / 'src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java'
s = p.read_text()
s = once(s,
'''    private static View compatBackgroundBlurLoggedFor;\n''',
'''    private static View compatBackgroundBlurLoggedFor;\n    private static View transparentMaterialOwner;\n    private static GradientDrawable transparentMaterialBody;\n    private static float transparentMaterialRadius = Float.NaN;\n    private static View materialBodyLoggedFor;\n''',
'body fields')

s = once(s,
'''        vendorGpuBlurLoggedFor = null;\n        compatBackgroundBlurLoggedFor = null;\n''',
'''        vendorGpuBlurLoggedFor = null;\n        compatBackgroundBlurLoggedFor = null;\n        transparentMaterialOwner = null;\n        transparentMaterialBody = null;\n        transparentMaterialRadius = Float.NaN;\n        materialBodyLoggedFor = null;\n''',
'reset body state')

s = once(s,
'''        float nativeRadius = readRadius(dockBg);\n        float glassRadius = DockStrokeRenderer.resolveConfiguredRadius(\n                dockBg, config.dock, nativeRadius);\n        int dockW = readDimension(dockBg, "mWidth", true);\n        int dockH = readDimension(dockBg, "mHeight", false);\n        MainHook.log(TAG + " in-place material nativeRadius=" + nativeRadius\n                + " glassRadius=" + glassRadius\n                + " dock size=" + dockW + "x" + dockH);\n''',
'''        float nativeRadius = readRadius(dockBg);\n        suppressVendorMaterialBody(dockBg, nativeRadius);\n        int dockW = readDimension(dockBg, "mWidth", true);\n        int dockH = readDimension(dockBg, "mHeight", false);\n        MainHook.log(TAG + " in-place material nativeOpticsRadius=" + nativeRadius\n                + " dock size=" + dockW + "x" + dockH);\n''',
'install radius ownership')

s = once(s,
'''        DockLiquidGlassView glass = LiquidGlassFactory.create(\n                dockBg, workspace, config.glass, config.dock,\n                config.dock.squircle, config.dock.squircleCp);\n''',
'''        DockLiquidGlassView glass = LiquidGlassFactory.create(\n                dockBg, workspace, config.glass, config.dock,\n                false, SQUIRCLE_CP);\n''',
'factory native round shape')

s = once(s,
'''        host.setGeometry(glassRadius, config.dock.squircle, config.dock.squircleCp);\n        host.reloadOpticsOnly(config.dock, config.glass);\n''',
'''        host.setGeometry(nativeRadius, false, SQUIRCLE_CP);\n        host.reloadOpticsPreservingGeometry(config.glass);\n''',
'install host native geometry')

s = once(s,
'''        if (isNativeVisualOwner(dockBg)) suppressVendorGpuBlur(dockBg);\n        // MATCH_PARENT follows the authoritative vendor material geometry automatically.\n''',
'''        if (isNativeVisualOwner(dockBg)) {\n            suppressVendorGpuBlur(dockBg);\n            suppressVendorMaterialBody(dockBg, readRadius(dockBg));\n        }\n        // MATCH_PARENT follows the authoritative vendor material geometry automatically.\n''',
'sync size material suppression')

old_sync = '''        float nativeRadius = readRadius(dockBg);\n        float glassRadius = DockStrokeRenderer.resolveConfiguredRadius(\n                dockBg, config.dock, nativeRadius);\n        host.setGeometry(glassRadius, config.dock.squircle, config.dock.squircleCp);\n        host.reloadOpticsOnly(config.dock, config.glass);\n        DockStrokeRenderer.configureReplacingForeground(\n                dockBg, config.dock, nativeRadius);\n'''
new_sync = '''        float nativeRadius = readRadius(dockBg);\n        suppressVendorMaterialBody(dockBg, nativeRadius);\n        host.setGeometry(nativeRadius, false, SQUIRCLE_CP);\n        host.reloadOpticsPreservingGeometry(config.glass);\n        DockStrokeRenderer.configureReplacingForeground(\n                dockBg, config.dock, nativeRadius);\n'''
s = once(s, old_sync, new_sync, 'sync native optics')

old_listener = '''        ViewTreeObserver.OnPreDrawListener listener = () -> {\n            if (backgroundRef == dockBg) suppressVendorGpuBlur(dockBg);\n            return true;\n        };\n'''
new_listener = '''        ViewTreeObserver.OnPreDrawListener listener = () -> {\n            if (backgroundRef == dockBg) {\n                suppressVendorGpuBlur(dockBg);\n                suppressVendorMaterialBody(dockBg, readRadius(dockBg));\n            }\n            return true;\n        };\n'''
s = once(s, old_listener, new_listener, 'predraw body suppression')

s = once(s,
'''        dockBg.post(() -> {\n            if (backgroundRef == dockBg) suppressVendorGpuBlur(dockBg);\n        });\n''',
'''        dockBg.post(() -> {\n            if (backgroundRef == dockBg) {\n                suppressVendorGpuBlur(dockBg);\n                suppressVendorMaterialBody(dockBg, readRadius(dockBg));\n            }\n        });\n''',
'post body suppression')

# Insert material-body suppression before readDimension.
marker = '''    private static int readDimension(View dockBg, String fieldName, boolean width) {\n'''
helper = '''    /**\n     * Keep the vendor View itself alive for layout, outline and MiShadow, but remove the\n     * opaque/material body that otherwise creates a second visible Dock edge around Prismal.\n     * The vendor's private radius state remains untouched and continues to drive optics.\n     */\n    private static void suppressVendorMaterialBody(View dockBg, float nativeRadius) {\n        if (dockBg == null || !isNativeVisualOwner(dockBg)) return;\n        float radius = Math.max(0f, nativeRadius);\n        if (transparentMaterialOwner != dockBg || transparentMaterialBody == null) {\n            transparentMaterialOwner = dockBg;\n            transparentMaterialBody = new GradientDrawable();\n            transparentMaterialBody.setShape(GradientDrawable.RECTANGLE);\n            transparentMaterialBody.setColor(android.graphics.Color.TRANSPARENT);\n            transparentMaterialRadius = Float.NaN;\n        }\n        if (Float.compare(transparentMaterialRadius, radius) != 0) {\n            transparentMaterialRadius = radius;\n            transparentMaterialBody.setCornerRadius(radius);\n        }\n        if (dockBg.getBackground() != transparentMaterialBody) {\n            dockBg.setBackground(transparentMaterialBody);\n        }\n        if (materialBodyLoggedFor != dockBg) {\n            materialBodyLoggedFor = dockBg;\n            MainHook.log(TAG + " vendor material body transparent; native optics radius="\n                    + radius + " class=" + dockBg.getClass().getSimpleName());\n        }\n    }\n\n'''
if marker not in s:
    raise SystemExit('readDimension marker missing')
s = s.replace(marker, helper + marker, 1)

# Theme fallback has a real mCornerRadius; it is the authoritative optics radius.
old_read = '''    private static float readRadius(View dockBg) {\n        try {\n            Field field = findField(dockBg.getClass(), "mBackground");\n            field.setAccessible(true);\n            Object value = field.get(dockBg);\n            if (value instanceof GradientDrawable) {\n                float radius = ((GradientDrawable) value).getCornerRadius();\n                if (radius >= 0f) return radius;\n            }\n        } catch (Throwable ignored) {}\n\n        Drawable drawable = dockBg.getBackground();\n'''
new_read = '''    private static float readRadius(View dockBg) {\n        if (dockBg != null && COMPAT_BACKGROUND_CLASS.equals(dockBg.getClass().getName())) {\n            try {\n                Field field = findField(dockBg.getClass(), "mCornerRadius");\n                field.setAccessible(true);\n                Object value = field.get(dockBg);\n                if (value instanceof Number) {\n                    return Math.max(0f, ((Number) value).floatValue());\n                }\n            } catch (Throwable ignored) {}\n        }\n\n        try {\n            Field field = findField(dockBg.getClass(), "mBackground");\n            field.setAccessible(true);\n            Object value = field.get(dockBg);\n            if (value instanceof GradientDrawable) {\n                float radius = ((GradientDrawable) value).getCornerRadius();\n                if (radius >= 0f) return radius;\n            }\n        } catch (Throwable ignored) {}\n\n        Drawable drawable = dockBg.getBackground();\n'''
s = once(s, old_read, new_read, 'authoritative radius reader')

for token in [
    'host.setGeometry(nativeRadius, false, SQUIRCLE_CP);',
    'reloadOpticsPreservingGeometry(config.glass)',
    'mCornerRadius',
    'vendor material body transparent; native optics radius=',
    'setPassWindowBlurRadius(dockBg, 0)',
    'clearPassWindowBlur(dockBg)',
]:
    if token not in s:
        raise SystemExit(f'missing required token: {token}')
if 'resolveConfiguredRadius(' in s:
    raise SystemExit('custom stroke radius still drives 307 optics')
p.write_text(s)

print('applied native optics + transparent material body fix')
