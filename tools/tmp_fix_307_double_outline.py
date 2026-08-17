from pathlib import Path

ROOT = Path('.')


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1, found {count}')
    return text.replace(old, new, 1)

# DockStrokeRenderer: 307 in-place mode replaces vendor foreground instead of wrapping it.
p = ROOT / 'src/main/java/com/hellovoid/liquiddock/DockStrokeRenderer.java'
s = p.read_text()
old = '''    static void configure(View host, LiquidDockConfig.Dock config, float radius) {\n        if (host == null) return;\n\n        synchronized (INSTALLED) {\n            StrokeDrawable installed = INSTALLED.get(host);\n\n            if (config == null || !config.strokeEnabled) {\n                if (installed != null && host.getForeground() == installed) {\n                    host.setForeground(installed.baseForeground());\n                }\n                INSTALLED.remove(host);\n                return;\n            }\n\n            Style style = Style.from(config, host);\n            Drawable current = host.getForeground();\n\n            if (installed == null && current instanceof StrokeDrawable) {\n                installed = (StrokeDrawable) current;\n                INSTALLED.put(host, installed);\n            }\n\n            if (installed == null) {\n                installed = new StrokeDrawable(current);\n                INSTALLED.put(host, installed);\n                host.setForeground(installed);\n            }\n\n            installed.setStyle(style);\n            installed.setRadius(radius);\n            host.invalidate();\n        }\n    }\n'''
new = '''    static void configure(View host, LiquidDockConfig.Dock config, float radius) {\n        configureInternal(host, config, radius, true);\n    }\n\n    /** 307 in-place glass owns the visual edge, so do not redraw the vendor foreground below it. */\n    static void configureReplacingForeground(\n            View host, LiquidDockConfig.Dock config, float radius) {\n        configureInternal(host, config, radius, false);\n    }\n\n    /** Match the glass clip to the same radius basis used by the configured custom stroke. */\n    static float resolveConfiguredRadius(\n            View host, LiquidDockConfig.Dock config, float nativeBlurRadius) {\n        float radius = Math.max(0f, nativeBlurRadius);\n        if (host == null || config == null || !config.enabled) return radius;\n        float density = host.getResources().getDisplayMetrics().density;\n        float cornerScale = config.cornersDp ? density : 1f;\n        return Math.max(0f, radius\n                + (config.cornerOffset - config.blurCornerOffset) * cornerScale);\n    }\n\n    private static void configureInternal(\n            View host, LiquidDockConfig.Dock config, float radius,\n            boolean preserveExistingForeground) {\n        if (host == null) return;\n\n        synchronized (INSTALLED) {\n            StrokeDrawable installed = INSTALLED.get(host);\n\n            if (config == null || !config.strokeEnabled) {\n                if (installed != null && host.getForeground() == installed) {\n                    host.setForeground(preserveExistingForeground\n                            ? installed.baseForeground() : null);\n                }\n                INSTALLED.remove(host);\n                return;\n            }\n\n            Style style = Style.from(config, host);\n            Drawable current = host.getForeground();\n\n            if (installed == null && current instanceof StrokeDrawable) {\n                installed = (StrokeDrawable) current;\n                INSTALLED.put(host, installed);\n            }\n\n            if (installed == null) {\n                installed = new StrokeDrawable(\n                        preserveExistingForeground ? current : null);\n                INSTALLED.put(host, installed);\n                host.setForeground(installed);\n            } else if (!preserveExistingForeground) {\n                installed.setBaseForeground(null);\n            }\n\n            installed.setStyle(style);\n            installed.setRadius(radius);\n            host.invalidate();\n        }\n    }\n'''
s = replace_once(s, old, new, 'configure block')
s = replace_once(s,
    '        private final Drawable baseForeground;\n',
    '        private Drawable baseForeground;\n',
    'base foreground mutability')
s = replace_once(s,
    '''        Drawable baseForeground() {\n            return baseForeground;\n        }\n''',
    '''        Drawable baseForeground() {\n            return baseForeground;\n        }\n\n        void setBaseForeground(Drawable baseForeground) {\n            this.baseForeground = baseForeground;\n            invalidateSelf();\n        }\n''',
    'base foreground setter')
p.write_text(s)

# MiuixGlassHook: glass uses custom radius, vendor foreground is replaced rather than stacked.
p = ROOT / 'src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java'
s = p.read_text()
s = replace_once(s,
    '''        float radius = readRadius(dockBg);\n        int dockW = readDimension(dockBg, "mWidth", true);\n        int dockH = readDimension(dockBg, "mHeight", false);\n        MainHook.log(TAG + " in-place material radius=" + radius\n                + " dock size=" + dockW + "x" + dockH);\n''',
    '''        float nativeRadius = readRadius(dockBg);\n        float glassRadius = DockStrokeRenderer.resolveConfiguredRadius(\n                dockBg, config.dock, nativeRadius);\n        int dockW = readDimension(dockBg, "mWidth", true);\n        int dockH = readDimension(dockBg, "mHeight", false);\n        MainHook.log(TAG + " in-place material nativeRadius=" + nativeRadius\n                + " glassRadius=" + glassRadius\n                + " dock size=" + dockW + "x" + dockH);\n''',
    'install radius block')
s = replace_once(s,
    '        host.setGeometry(radius, config.dock.squircle, config.dock.squircleCp);\n',
    '        host.setGeometry(glassRadius, config.dock.squircle, config.dock.squircleCp);\n',
    'install host radius')
s = replace_once(s,
    '        DockStrokeRenderer.configure(dockBg, config.dock, radius);\n',
    '        DockStrokeRenderer.configureReplacingForeground(dockBg, config.dock, nativeRadius);\n',
    'install replacing foreground')
s = replace_once(s,
    '''        float radius = readRadius(dockBg);\n        host.setGeometry(radius, config.dock.squircle, config.dock.squircleCp);\n        host.reloadOpticsOnly(config.dock, config.glass);\n        DockStrokeRenderer.configure(dockBg, config.dock, radius);\n''',
    '''        float nativeRadius = readRadius(dockBg);\n        float glassRadius = DockStrokeRenderer.resolveConfiguredRadius(\n                dockBg, config.dock, nativeRadius);\n        host.setGeometry(glassRadius, config.dock.squircle, config.dock.squircleCp);\n        host.reloadOpticsOnly(config.dock, config.glass);\n        DockStrokeRenderer.configureReplacingForeground(\n                dockBg, config.dock, nativeRadius);\n''',
    'sync radius + foreground')
for required in [
    'configureReplacingForeground(dockBg, config.dock, nativeRadius)',
    'resolveConfiguredRadius(',
    'glassRadius=',
]:
    if required not in s:
        raise SystemExit(f'missing MiuixGlassHook token: {required}')
p.write_text(s)

print('applied 307 double-outline fix')
