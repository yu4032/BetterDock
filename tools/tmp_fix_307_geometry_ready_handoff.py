from pathlib import Path

ROOT = Path('.')


def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, found {count}')
    return text.replace(old, new, 1)


# MiuixGlassHook: expose one authoritative readiness predicate and make install fail-safe.
p = ROOT / 'src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java'
s = p.read_text()

old_bound = '''    static boolean isBoundTo(View dockBg) {\n        if (dockBg == null || dockBg != backgroundRef) return false;\n        DockLiquidGlassHostView host = hostRef;\n        return host != null && host.getParent() == dockBg;\n    }\n'''
new_bound = old_bound + '''\n    /**\n     * A 307 material View exists before its real Dock geometry is committed. During Launcher\n     * startup the themed BlurBackground2 can be attached with width=0 and mCornerRadius=0, then\n     * receive its final geometry through the normal radius/measure callbacks. Never hand Prismal\n     * ownership to that placeholder state: doing so creates a transient second inner outline.\n     */\n    static boolean hasReadyNativeGeometry(View dockBg) {\n        if (dockBg == null || !isNativeVisualOwner(dockBg)) return false;\n        if (!dockBg.isAttachedToWindow() || !(dockBg.getParent() instanceof ViewGroup)) {\n            return false;\n        }\n        if (dockBg.getWidth() <= 0 || dockBg.getHeight() <= 0) return false;\n        float radius = readRadius(dockBg);\n        return !Float.isNaN(radius) && !Float.isInfinite(radius) && radius > 0.5f;\n    }\n\n    static float readNativeOpticsRadius(View dockBg) {\n        return readRadius(dockBg);\n    }\n'''
s = once(s, old_bound, new_bound, 'geometry readiness helper')

old_install_gate = '''        if (backgroundRef == dockBg && hostRef != null && hostRef.getParent() == materialHost) {\n            syncSize(dockBg);\n            syncGeometry(dockBg, config);\n            return true;\n        }\n\n        // Remove only LiquidDock's previous child host. Never replace or hide the vendor shell.\n'''
new_install_gate = '''        if (backgroundRef == dockBg && hostRef != null && hostRef.getParent() == materialHost) {\n            syncSize(dockBg);\n            syncGeometry(dockBg, config);\n            return true;\n        }\n\n        // Belt-and-suspenders guard: the coordinator normally filters this already, but direct\n        // callers must never make the placeholder 0-radius material state visible either.\n        if (!hasReadyNativeGeometry(dockBg)) return false;\n\n        // Remove only LiquidDock's previous child host. Never replace or hide the vendor shell.\n'''
s = once(s, old_install_gate, new_install_gate, 'install geometry guard')
p.write_text(s)


# Coordinator: defer the visual handoff until the vendor View has real attached geometry.
p = ROOT / 'src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java'
s = p.read_text()

old_fields = '''    private static boolean hierarchyRebindPosted;\n    private static ViewTreeObserver hierarchyRecoveryObserver;\n    private static ViewTreeObserver.OnGlobalLayoutListener hierarchyRecoveryListener;\n'''
new_fields = '''    private static boolean hierarchyRebindPosted;\n    private static ViewTreeObserver hierarchyRecoveryObserver;\n    private static ViewTreeObserver.OnGlobalLayoutListener hierarchyRecoveryListener;\n    // Log only once while one vendor instance is still in its startup placeholder geometry.\n    private static View geometryDeferredLoggedFor;\n'''
s = once(s, old_fields, new_fields, 'pipeline deferred field')

s = once(s,
'''                            if (!ensureGlassBound(background, config, classLoader)) {\n                                MainHook.log("[DC] MiuiX 307 real glass install returned false");\n                            }\n''',
'''                            if (!ensureGlassBound(background, config, classLoader)) {\n                                MainHook.log("[DC] MiuiX 307 real glass handoff pending");\n                            }\n''',
'setupViews deferred wording')

old_ensure_head = '''        if (background == null || !isSupportedBackground(background)) return false;\n        if (MiuixGlassHook.isBoundTo(background)) {\n            Miuix307DragCaptureHook.bind(background);\n            observeBoundHierarchy(background, config, classLoader);\n            return true;\n        }\n\n        // Remove observers before MiuixGlassHook replaces an old host so our own controlled\n'''
new_ensure_head = '''        if (background == null || !isSupportedBackground(background)) return false;\n        if (MiuixGlassHook.isBoundTo(background)) {\n            geometryDeferredLoggedFor = null;\n            Miuix307DragCaptureHook.bind(background);\n            observeBoundHierarchy(background, config, classLoader);\n            return true;\n        }\n\n        // setupViews/onAttachedToWindow run before BlurBackground2 has committed its real radius.\n        // Preserve the untouched vendor material during that placeholder phase. Existing vendor\n        // setBackgroundRadius/triggerMeasure callbacks naturally retry this method once geometry\n        // is valid, so no fixed-delay polling is needed.\n        if (!MiuixGlassHook.hasReadyNativeGeometry(background)) {\n            if (geometryDeferredLoggedFor != background) {\n                geometryDeferredLoggedFor = background;\n                MainHook.log("[DC] MiuiX 307 Prismal handoff deferred; native geometry not ready"\n                        + " class=" + background.getClass().getSimpleName()\n                        + " size=" + background.getWidth() + "x" + background.getHeight()\n                        + " radius=" + MiuixGlassHook.readNativeOpticsRadius(background));\n            }\n            return false;\n        }\n        if (geometryDeferredLoggedFor == background) {\n            MainHook.log("[DC] MiuiX 307 native geometry ready; committing Prismal handoff"\n                    + " size=" + background.getWidth() + "x" + background.getHeight()\n                    + " radius=" + MiuixGlassHook.readNativeOpticsRadius(background));\n            geometryDeferredLoggedFor = null;\n        }\n\n        // Remove observers before MiuixGlassHook replaces an old host so our own controlled\n'''
s = once(s, old_ensure_head, new_ensure_head, 'ensureGlassBound geometry gate')
p.write_text(s)

combined = (ROOT / 'src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java').read_text() + p.read_text()
for token in [
    'hasReadyNativeGeometry(View dockBg)',
    'readNativeOpticsRadius(View dockBg)',
    'Prismal handoff deferred; native geometry not ready',
    'native geometry ready; committing Prismal handoff',
    'if (!hasReadyNativeGeometry(dockBg)) return false;',
]:
    if token not in combined:
        raise SystemExit(f'missing required token: {token}')

print('applied 307 geometry-ready Prismal handoff gate')
