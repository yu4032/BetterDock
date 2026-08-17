from pathlib import Path

ROOT = Path('.')


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 token, found {count}')
    return text.replace(old, new, 1)


def replace_method(text, signature, replacement, label):
    start = text.find(signature)
    if start < 0:
        raise SystemExit(f'{label}: signature not found: {signature}')
    brace = text.find('{', start)
    if brace < 0:
        raise SystemExit(f'{label}: opening brace not found')
    depth = 0
    i = brace
    in_string = False
    in_char = False
    escape = False
    while i < len(text):
        ch = text[i]
        if escape:
            escape = False
        elif ch == '\\' and (in_string or in_char):
            escape = True
        elif ch == '"' and not in_char:
            in_string = not in_string
        elif ch == "'" and not in_string:
            in_char = not in_char
        elif not in_string and not in_char:
            if ch == '{':
                depth += 1
            elif ch == '}':
                depth -= 1
                if depth == 0:
                    end = i + 1
                    return text[:start] + replacement + text[end:]
        i += 1
    raise SystemExit(f'{label}: closing brace not found')


# ---------------------------------------------------------------------------
# DockLiquidGlassView: 307 in-place composition must never hide its parent.
# ---------------------------------------------------------------------------
p = ROOT / 'src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java'
s = p.read_text()
s = replace_once(
    s,
    '    private boolean nativeBackgroundHiddenByGlass;\n',
    '    private boolean nativeBackgroundHiddenByGlass;\n'
    '    private boolean preserveGeometrySourceVisuals;\n',
    'glass preserve field')
s = replace_once(
    s,
    '    void setFullscreenCapture(boolean enabled) { fullscreenCapture = enabled; }\n',
    '    void setFullscreenCapture(boolean enabled) { fullscreenCapture = enabled; }\n\n'
    '    /** Keep a parent material View visible when glass is composed inside that View. */\n'
    '    void setPreserveGeometrySourceVisuals(boolean preserve) {\n'
    '        preserveGeometrySourceVisuals = preserve;\n'
    '        if (preserve) {\n'
    '            geometrySource.setAlpha(1f);\n'
    '            nativeBackgroundHiddenByGlass = false;\n'
    '        }\n'
    '    }\n',
    'glass preserve setter')
s = replace_once(
    s,
    '        if (!nativeBackgroundHiddenByGlass) {\n'
    '            geometrySource.setAlpha(0f);\n'
    '            nativeBackgroundHiddenByGlass = true;\n'
    '        }\n',
    '        if (preserveGeometrySourceVisuals) {\n'
    '            if (geometrySource.getAlpha() != 1f) geometrySource.setAlpha(1f);\n'
    '            nativeBackgroundHiddenByGlass = false;\n'
    '        } else if (!nativeBackgroundHiddenByGlass) {\n'
    '            geometrySource.setAlpha(0f);\n'
    '            nativeBackgroundHiddenByGlass = true;\n'
    '        }\n',
    'installCapture source visibility')
p.write_text(s)


# ---------------------------------------------------------------------------
# Host: allow 307 to keep stroke/foreground ownership on the vendor parent.
# ---------------------------------------------------------------------------
p = ROOT / 'src/main/java/com/hellovoid/liquiddock/DockLiquidGlassHostView.java'
s = p.read_text()
old = '''    void reloadOverlay(LiquidDockConfig.Dock dock, LiquidDockConfig.Glass glass) {\n        setGeometry(radius, dock.squircle, dock.squircleCp);\n        setHighlight(glass.highlightAlpha, glass.highlightWidth);\n        setHighlightParams(glass.normalStrength, glass.dome, glass.specularSharp,\n                glass.specularStrength, glass.rimLight, glass.caustics,\n                glass.edgeBand, glass.highlightAlpha);\n        DockStrokeRenderer.configure(this, dock, radius);\n    }\n'''
new = '''    void reloadOpticsOnly(LiquidDockConfig.Dock dock, LiquidDockConfig.Glass glass) {\n        setGeometry(radius, dock.squircle, dock.squircleCp);\n        setHighlight(glass.highlightAlpha, glass.highlightWidth);\n        setHighlightParams(glass.normalStrength, glass.dome, glass.specularSharp,\n                glass.specularStrength, glass.rimLight, glass.caustics,\n                glass.edgeBand, glass.highlightAlpha);\n    }\n\n    void reloadOverlay(LiquidDockConfig.Dock dock, LiquidDockConfig.Glass glass) {\n        reloadOpticsOnly(dock, glass);\n        DockStrokeRenderer.configure(this, dock, radius);\n    }\n'''
s = replace_once(s, old, new, 'host optics-only reload')
p.write_text(s)


# ---------------------------------------------------------------------------
# MiuixGlassHook: compose the existing host INSIDE the vendor material shell.
# ---------------------------------------------------------------------------
p = ROOT / 'src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java'
s = p.read_text()
s = s.replace(
    ' * The vendor background remains only as a geometry source. Its compositor/pass-window blur is\n'
    ' * disabled so LiquidDock\'s existing Prismal glass stack owns the actual blur and optical pass.\n',
    ' * The vendor background remains the authoritative Dock visual shell. Its compositor/pass-window\n'
    ' * blur is disabled, while LiquidDock\'s existing Prismal host is composed inside that shell.\n')

s = replace_method(s, '    static boolean isBoundTo(', '''    static boolean isBoundTo(View dockBg) {\n        if (dockBg == null || dockBg != backgroundRef) return false;\n        DockLiquidGlassHostView host = hostRef;\n        return host != null && host.getParent() == dockBg;\n    }''', 'MiuixGlassHook.isBoundTo')

s = replace_method(s, '    static boolean install(', '''    static boolean install(View dockBg, View workspace, LiquidDockConfig config,\n                           Object launcher, ClassLoader cl) {\n        if (!(dockBg instanceof ViewGroup) || config == null) return false;\n        ViewGroup materialHost = (ViewGroup) dockBg;\n        boolean nativeVisualOwner = isNativeVisualOwner(dockBg);\n\n        if (backgroundRef == dockBg && hostRef != null && hostRef.getParent() == materialHost) {\n            syncSize(dockBg);\n            syncGeometry(dockBg, config);\n            return true;\n        }\n\n        // Remove only LiquidDock's previous child host. Never replace or hide the vendor shell.\n        removeVendorGpuBlurSuppressor();\n        if (hostRef != null && hostRef.getParent() instanceof ViewGroup) {\n            ((ViewGroup) hostRef.getParent()).removeView(hostRef);\n        }\n        hostRef = null;\n        glassRef = null;\n        backgroundRef = null;\n        vendorGpuBlurLoggedFor = null;\n        compatBackgroundBlurLoggedFor = null;\n\n        // The vendor View keeps outline/MiShadow/foreground ownership, but its compositor blur\n        // must stay disabled because SurfaceFlinger would otherwise post-process the whole Dock.\n        if (nativeVisualOwner) suppressVendorGpuBlur(dockBg);\n\n        float radius = readRadius(dockBg);\n        int dockW = readDimension(dockBg, "mWidth", true);\n        int dockH = readDimension(dockBg, "mHeight", false);\n        MainHook.log(TAG + " in-place material radius=" + radius\n                + " dock size=" + dockW + "x" + dockH);\n\n        DockLiquidGlassView glass = LiquidGlassFactory.create(\n                dockBg, workspace, config.glass, config.dock,\n                config.dock.squircle, config.dock.squircleCp);\n        glass.setId(View.generateViewId());\n        glass.setFullscreenCapture(true);\n        glass.setCaptureScale(config.glass.captureScale);\n        glass.setCapturePowerLimitFps(config.glass.captureFps);\n        // This geometry source is also the parent shell; hiding it would hide glass, stroke,\n        // outline and MiShadow together. Keep the shell alpha alive after every valid capture.\n        glass.setPreserveGeometrySourceVisuals(true);\n\n        DockLiquidGlassHostView host = new DockLiquidGlassHostView(dockBg.getContext());\n        host.setId(View.generateViewId());\n        host.setLayers(glass);\n        host.setGeometry(radius, config.dock.squircle, config.dock.squircleCp);\n        host.reloadOpticsOnly(config.dock, config.glass);\n\n        FrameLayout.LayoutParams hostLp = new FrameLayout.LayoutParams(\n                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);\n        materialHost.addView(host, materialHost.getChildCount(), hostLp);\n        host.bringToFront();\n\n        backgroundRef = dockBg;\n        glassRef = glass;\n        hostRef = host;\n        if (nativeVisualOwner) suppressVendorGpuBlur(dockBg);\n        installVendorGpuBlurSuppressor(dockBg);\n        HomeOwnershipRuntime.bind(glass, glass.getContext());\n\n        // Stroke + stroke-shadow deliberately live on the vendor foreground, which Android draws\n        // after child dispatch, so they remain sharp and above the in-place glass.\n        DockStrokeRenderer.configure(dockBg, config.dock, radius);\n        MainHook.log(TAG + " Prismal composed inside native 307 material shell class="\n                + dockBg.getClass().getSimpleName());\n        return true;\n    }''', 'MiuixGlassHook.install')

s = replace_method(s, '    static void syncSize(', '''    static void syncSize(View dockBg) {\n        if (dockBg == null || dockBg != backgroundRef) return;\n        DockLiquidGlassHostView host = hostRef;\n        if (host == null || host.getParent() != dockBg) return;\n        if (isNativeVisualOwner(dockBg)) suppressVendorGpuBlur(dockBg);\n        // MATCH_PARENT follows the authoritative vendor material geometry automatically.\n        host.bringToFront();\n        host.requestLayout();\n        host.invalidate();\n    }''', 'MiuixGlassHook.syncSize')

s = replace_method(s, '    static void syncGeometry(', '''    static void syncGeometry(View dockBg, LiquidDockConfig config) {\n        if (dockBg == null || config == null || dockBg != backgroundRef) return;\n        DockLiquidGlassHostView host = hostRef;\n        if (host == null || host.getParent() != dockBg) return;\n\n        if (isNativeVisualOwner(dockBg)) suppressVendorGpuBlur(dockBg);\n\n        float radius = readRadius(dockBg);\n        host.setGeometry(radius, config.dock.squircle, config.dock.squircleCp);\n        host.reloadOpticsOnly(config.dock, config.glass);\n        DockStrokeRenderer.configure(dockBg, config.dock, radius);\n        host.bringToFront();\n        host.invalidate();\n    }''', 'MiuixGlassHook.syncGeometry')

s = s.replace(
    '     * Reassert only GPU-blur suppression before draw. Crucially, this listener never restores\n'
    '     * dockBg alpha and never touches the glass view hidden-source latch: the\n'
    '     * ordinary glass lifecycle must be free to hide the native geometry source after capture.\n',
    '     * Reassert only GPU-blur suppression before draw. The vendor shell itself stays visible;\n'
    '     * LiquidDock is a child composition and must never force the shell alpha to zero.\n')

for token in [
    'glass.setPreserveGeometrySourceVisuals(true);',
    'materialHost.addView(host, materialHost.getChildCount(), hostLp);',
    'host.reloadOpticsOnly(config.dock, config.glass);',
    'MiBlurBridge.setPassWindowBlurRadius(dockBg, 0);',
    'MiBlurBridge.clearPassWindowBlur(dockBg)',
]:
    if token not in s:
        raise SystemExit(f'MiuixGlassHook missing required token: {token}')
if 'parent.addView(host' in s:
    raise SystemExit('old sibling host insertion remains')
p.write_text(s)


# ---------------------------------------------------------------------------
# Miuix307MaterialPipeline: restore customization skipped by the early return.
# ---------------------------------------------------------------------------
p = ROOT / 'src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java'
s = p.read_text()
s = s.replace(
    ' * Keep either vendor background only as a geometry source while LiquidDock\'s Prismal glass owns\n'
    ' * the real blur/optical pass; vendor compositor blur is explicitly suppressed.\n',
    ' * Keep the live vendor background as the authoritative visual shell while LiquidDock composes\n'
    ' * Prismal inside it; vendor compositor blur is explicitly suppressed.\n')
s = replace_once(
    s,
    '            installCompatBackgroundBlurSuppression(classLoader);\n',
    '            installCompatBackgroundBlurSuppression(classLoader);\n'
    '            installDockCustomizationCompatibility(classLoader, config);\n',
    'install 307 dock customization compatibility')

helper = '''\n    /** Restore the non-glass Dock customization hooks skipped by MainHook's 307 early return. */\n    private static void installDockCustomizationCompatibility(\n            ClassLoader classLoader, LiquidDockConfig config) {\n        LiquidDockConfig.Dock dock = config.dock;\n        if (dock == null || !dock.enabled) return;\n        float density = android.content.res.Resources.getSystem().getDisplayMetrics().density;\n        float dimensionScale = dock.dimensionsDp ? density : 1f;\n        int spacing = Math.round(dock.spacing * dimensionScale);\n        int bottomOffset = Math.round(dock.bottomOffset * dimensionScale);\n\n        if (bottomOffset != 0) {\n            try {\n                Class<?> deviceConfig = Class.forName(\n                        "com.miui.home.launcher.DeviceConfig", false, classLoader);\n                HookUtil.hookMethod(deviceConfig, "getHotSeatsMarginBottom", new Class<?>[0],\n                        chain -> {\n                            Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));\n                            if (MainHook.isWorkstationMode()) return result;\n                            return (Integer) result + bottomOffset;\n                        });\n            } catch (Throwable error) {\n                MainHook.log("[DC] MiuiX 307 bottom offset hook unavailable: " + error);\n            }\n        }\n\n        if (spacing != 0) {\n            try {\n                Class<?> recyclerView = Class.forName(\n                        "androidx.recyclerview.widget.RecyclerView", false, classLoader);\n                Class<?> recyclerState = Class.forName(\n                        "androidx.recyclerview.widget.RecyclerView$State", false, classLoader);\n                HookUtil.hookMethod(classLoader,\n                        "com.miui.home.launcher.hotseats.HotSeatsListContentLayoutManager$OffsetDecoration",\n                        "getItemOffsets",\n                        chain -> {\n                            Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));\n                            if (MainHook.isWorkstationMode()) return result;\n                            android.graphics.Rect out = (android.graphics.Rect) chain.getArgs().get(0);\n                            out.left += spacing;\n                            out.right += spacing;\n                            return result;\n                        }, android.graphics.Rect.class, View.class, recyclerView, recyclerState);\n\n                Class<?> layoutManager = Class.forName(\n                        "com.miui.home.launcher.hotseats.HotSeatsListContentLayoutManager",\n                        false, classLoader);\n                HookUtil.hookMethod(layoutManager, "updateBackgroundView",\n                        new Class<?>[]{android.widget.FrameLayout.class, int.class, int.class, float.class},\n                        chain -> {\n                            if (MainHook.isWorkstationMode()) {\n                                return chain.proceed(chain.getArgs().toArray(new Object[0]));\n                            }\n                            int itemCount = (Integer) HookUtil.invoke(\n                                    chain.getThisObject(), "getItemCount");\n                            Object[] args = chain.getArgs().toArray(new Object[0]);\n                            if (itemCount > 0) args[1] = (Integer) args[1] + spacing * 2 * itemCount;\n                            return chain.proceed(args);\n                        });\n            } catch (Throwable error) {\n                MainHook.log("[DC] MiuiX 307 spacing hook unavailable: " + error);\n            }\n        }\n    }\n\n'''
marker = '    /** Native MiuiX implementation exposes explicit width/height/radius setters. */\n'
if marker not in s:
    raise SystemExit('pipeline customization insertion marker missing')
s = s.replace(marker, helper + marker, 1)

s = replace_method(s, '    private static void installMiuixGeometryHooks(', '''    private static void installMiuixGeometryHooks(\n            Class<?> backgroundClass, LiquidDockConfig config, ClassLoader classLoader) {\n        LiquidDockConfig.Dock dock = config.dock;\n        float density = android.content.res.Resources.getSystem().getDisplayMetrics().density;\n        float dimensionScale = dock.dimensionsDp ? density : 1f;\n        float cornerScale = dock.cornersDp ? density : 1f;\n        int widthOffset = dock.enabled ? Math.round(dock.widthOffset * dimensionScale) : 0;\n        int heightOffset = dock.enabled ? Math.round(dock.heightOffset * dimensionScale) : 0;\n        float blurCornerOffset = dock.enabled ? dock.blurCornerOffset * cornerScale : 0f;\n\n        HookUtil.hookMethod(backgroundClass, "setBackgroundWidth",\n                new Class<?>[]{int.class}, chain -> {\n                    Object[] args = chain.getArgs().toArray(new Object[0]);\n                    if (!MainHook.isWorkstationMode() && widthOffset != 0) {\n                        args[0] = (Integer) args[0] + widthOffset;\n                    }\n                    Object result = chain.proceed(args);\n                    View background = (View) chain.getThisObject();\n                    ensureGlassBound(background, config, classLoader);\n                    MiuixGlassHook.syncSize(background);\n                    return result;\n                });\n        HookUtil.hookMethod(backgroundClass, "setBackgroundHeight",\n                new Class<?>[]{int.class}, chain -> {\n                    Object[] args = chain.getArgs().toArray(new Object[0]);\n                    if (!MainHook.isWorkstationMode() && heightOffset != 0) {\n                        args[0] = (Integer) args[0] + heightOffset;\n                    }\n                    Object result = chain.proceed(args);\n                    View background = (View) chain.getThisObject();\n                    ensureGlassBound(background, config, classLoader);\n                    MiuixGlassHook.syncSize(background);\n                    return result;\n                });\n        HookUtil.hookMethod(backgroundClass, "setBackgroundRadius",\n                new Class<?>[]{float.class}, chain -> {\n                    Object[] args = chain.getArgs().toArray(new Object[0]);\n                    if (!MainHook.isWorkstationMode() && blurCornerOffset != 0f) {\n                        args[0] = Math.max(0f, (Float) args[0] + blurCornerOffset);\n                    }\n                    Object result = chain.proceed(args);\n                    View background = (View) chain.getThisObject();\n                    ensureGlassBound(background, config, classLoader);\n                    MiuixGlassHook.syncGeometry(background, config);\n                    return result;\n                });\n    }''', 'pipeline native geometry hooks')

s = replace_method(s, '    private static void installThemedBackgroundHooks(', '''    private static void installThemedBackgroundHooks(\n            Class<?> backgroundClass, LiquidDockConfig config, ClassLoader classLoader) {\n        LiquidDockConfig.Dock dock = config.dock;\n        float density = android.content.res.Resources.getSystem().getDisplayMetrics().density;\n        float dimensionScale = dock.dimensionsDp ? density : 1f;\n        float cornerScale = dock.cornersDp ? density : 1f;\n        int widthOffset = dock.enabled ? Math.round(dock.widthOffset * dimensionScale) : 0;\n        int heightOffset = dock.enabled ? Math.round(dock.heightOffset * dimensionScale) : 0;\n        float blurCornerOffset = dock.enabled ? dock.blurCornerOffset * cornerScale : 0f;\n\n        HookUtil.hookMethod(backgroundClass, "onAttachedToWindow", new Class<?>[0], chain -> {\n            Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));\n            View background = (View) chain.getThisObject();\n            ensureGlassBound(background, config, classLoader);\n            MiuixGlassHook.syncGeometry(background, config);\n            return result;\n        });\n        HookUtil.hookMethod(backgroundClass, "setBackgroundWidth",\n                new Class<?>[]{int.class}, chain -> {\n                    Object[] args = chain.getArgs().toArray(new Object[0]);\n                    if (!MainHook.isWorkstationMode() && widthOffset != 0) {\n                        args[0] = (Integer) args[0] + widthOffset;\n                    }\n                    Object result = chain.proceed(args);\n                    View background = (View) chain.getThisObject();\n                    ensureGlassBound(background, config, classLoader);\n                    MiuixGlassHook.syncSize(background);\n                    return result;\n                });\n        HookUtil.hookMethod(backgroundClass, "setBackgroundHeight",\n                new Class<?>[]{int.class}, chain -> {\n                    Object[] args = chain.getArgs().toArray(new Object[0]);\n                    if (!MainHook.isWorkstationMode() && heightOffset != 0) {\n                        args[0] = (Integer) args[0] + heightOffset;\n                    }\n                    Object result = chain.proceed(args);\n                    View background = (View) chain.getThisObject();\n                    ensureGlassBound(background, config, classLoader);\n                    MiuixGlassHook.syncSize(background);\n                    return result;\n                });\n        HookUtil.hookMethod(backgroundClass, "setBackgroundRadius",\n                new Class<?>[]{float.class}, chain -> {\n                    Object[] args = chain.getArgs().toArray(new Object[0]);\n                    if (!MainHook.isWorkstationMode() && blurCornerOffset != 0f) {\n                        args[0] = Math.max(0f, (Float) args[0] + blurCornerOffset);\n                    }\n                    Object result = chain.proceed(args);\n                    View background = (View) chain.getThisObject();\n                    ensureGlassBound(background, config, classLoader);\n                    MiuixGlassHook.syncGeometry(background, config);\n                    return result;\n                });\n\n        int hooked = 0;\n        Class<?> cursor = backgroundClass;\n        while (cursor != null && cursor != Object.class) {\n            for (Method method : cursor.getDeclaredMethods()) {\n                if (!"triggerMeasure".equals(method.getName())\n                        || Modifier.isStatic(method.getModifiers())) {\n                    continue;\n                }\n                HookUtil.hook(method, chain -> {\n                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));\n                    Object owner = chain.getThisObject();\n                    if (owner instanceof View) {\n                        View background = (View) owner;\n                        ensureGlassBound(background, config, classLoader);\n                        MiuixGlassHook.syncSize(background);\n                        MiuixGlassHook.syncGeometry(background, config);\n                    }\n                    return result;\n                });\n                hooked++;\n            }\n            cursor = cursor.getSuperclass();\n        }\n        MainHook.log("[DC] MiuiX 307 themed background geometry hooks installed count=" + hooked);\n    }''', 'pipeline themed geometry hooks')

s = replace_method(s, '    private static View resolveBoundHost(', '''    private static View resolveBoundHost(View background) {\n        if (background == null) return null;\n        // New in-place architecture: the LiquidDock host is a child of this exact material View.\n        if (background instanceof ViewGroup) {\n            ViewGroup material = (ViewGroup) background;\n            for (int i = 0; i < material.getChildCount(); i++) {\n                View child = material.getChildAt(i);\n                if (child instanceof DockLiquidGlassHostView) return child;\n            }\n        }\n        // Transitional fallback for a stale sibling host while an older hierarchy is detaching.\n        if (!(background.getParent() instanceof ViewGroup)) return null;\n        ViewGroup parent = (ViewGroup) background.getParent();\n        for (int i = 0; i < parent.getChildCount(); i++) {\n            View child = parent.getChildAt(i);\n            if (child instanceof DockLiquidGlassHostView) return child;\n        }\n        return null;\n    }''', 'pipeline resolveBoundHost')

for token in [
    'installDockCustomizationCompatibility(classLoader, config);',
    'widthOffset',
    'heightOffset',
    'blurCornerOffset',
    'if (background instanceof ViewGroup)',
]:
    if token not in s:
        raise SystemExit(f'pipeline missing required token: {token}')
p.write_text(s)

print('applied 307 in-place material glass architecture')
