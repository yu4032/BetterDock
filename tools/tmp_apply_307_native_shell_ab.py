from pathlib import Path

hook_path = Path('src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java')
glass_path = Path('src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java')

hook = hook_path.read_text()
glass = glass_path.read_text()

# Revert the prior squircle-only experiment so shell visibility/order is the only A/B variable.
factory_true = 'dockBg, workspace, config.glass, config.dock, true, SQUIRCLE_CP);'
factory_false = 'dockBg, workspace, config.glass, config.dock, false, SQUIRCLE_CP);'
if hook.count(factory_true) != 1:
    raise SystemExit(f'factory squircle true token count={hook.count(factory_true)}')
hook = hook.replace(factory_true, factory_false, 1)

host_true = 'host.setGeometry(radius, true, SQUIRCLE_CP);'
host_false = 'host.setGeometry(radius, false, SQUIRCLE_CP);'
if hook.count(host_true) != 2:
    raise SystemExit(f'host squircle true token count={hook.count(host_true)}')
hook = hook.replace(host_true, host_false)

# Preserve the vendor background as a visual shell only; Prismal remains the blur owner.
needle = '        glass.setCapturePowerLimitFps(config.glass.captureFps);\n'
insert = needle + '        glass.setPreserveGeometrySourceVisuals(true);\n'
if hook.count(needle) != 1:
    raise SystemExit(f'capture fps token count={hook.count(needle)}')
if 'glass.setPreserveGeometrySourceVisuals(true);' in hook:
    raise SystemExit('preserve call already present')
hook = hook.replace(needle, insert, 1)

old_order = '''        int bgIdx = parent.indexOfChild(dockBg);\n        int insertIndex = bgIdx < 0 ? parent.getChildCount()\n                : Math.min(parent.getChildCount(), bgIdx + 1);\n        parent.addView(host, insertIndex, hostLp);\n'''
new_order = '''        int bgIdx = parent.indexOfChild(dockBg);\n        // Insert Prismal at the vendor background's current index. addView() shifts dockBg\n        // upward, leaving the native View as the top visual shell while its GPU blur stays off.\n        int insertIndex = bgIdx < 0 ? parent.getChildCount()\n                : Math.max(0, Math.min(parent.getChildCount(), bgIdx));\n        parent.addView(host, insertIndex, hostLp);\n'''
if hook.count(old_order) != 1:
    raise SystemExit(f'old host-order block count={hook.count(old_order)}')
hook = hook.replace(old_order, new_order, 1)

# Add an opt-out to the generic glass lifecycle; default remains unchanged for all other paths.
field = '    private boolean nativeBackgroundHiddenByGlass;\n'
field_new = field + '    private boolean preserveGeometrySourceVisuals;\n'
if glass.count(field) != 1:
    raise SystemExit(f'native hidden field count={glass.count(field)}')
if 'private boolean preserveGeometrySourceVisuals;' in glass:
    raise SystemExit('preserve field already present')
glass = glass.replace(field, field_new, 1)

setter = '    void setFullscreenCapture(boolean enabled) { fullscreenCapture = enabled; }\n'
setter_new = setter + '''\n    /** 307-only A/B: keep the native Dock View visible as a non-blur visual shell. */\n    void setPreserveGeometrySourceVisuals(boolean preserve) {\n        preserveGeometrySourceVisuals = preserve;\n        if (preserve) {\n            geometrySource.setAlpha(1f);\n            nativeBackgroundHiddenByGlass = false;\n        }\n    }\n'''
if glass.count(setter) != 1:
    raise SystemExit(f'fullscreen setter count={glass.count(setter)}')
glass = glass.replace(setter, setter_new, 1)

old_hide = '''        if (!nativeBackgroundHiddenByGlass) {\n            geometrySource.setAlpha(0f);\n            nativeBackgroundHiddenByGlass = true;\n        }\n'''
new_hide = '''        if (preserveGeometrySourceVisuals) {\n            if (geometrySource.getAlpha() != 1f) geometrySource.setAlpha(1f);\n            nativeBackgroundHiddenByGlass = false;\n        } else if (!nativeBackgroundHiddenByGlass) {\n            geometrySource.setAlpha(0f);\n            nativeBackgroundHiddenByGlass = true;\n        }\n'''
if glass.count(old_hide) != 1:
    raise SystemExit(f'installCapture hide block count={glass.count(old_hide)}')
glass = glass.replace(old_hide, new_hide, 1)

# Safety assertions: keep the ownership fix intact and avoid scope creep.
for required in [
    'suppressVendorGpuBlur(dockBg)',
    'MiBlurBridge.setPassWindowBlurRadius(dockBg, 0)',
    'MiBlurBridge.clearPassWindowBlur(dockBg)',
    'glass.setPreserveGeometrySourceVisuals(true);',
]:
    if required not in hook:
        raise SystemExit(f'missing required hook token: {required}')
if 'config.dock, true, SQUIRCLE_CP' in hook or 'host.setGeometry(radius, true, SQUIRCLE_CP)' in hook:
    raise SystemExit('squircle-only experiment token remains')

hook_path.write_text(hook)
glass_path.write_text(glass)
print('applied 307 native visual-shell A/B')
