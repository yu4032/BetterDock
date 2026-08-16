from pathlib import Path

path = Path("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java")
text = path.read_text()

helper_anchor = '''    private FullDisplayExclusions resolveFullDisplayExclusions() {\n'''
helper = '''    private static android.view.SurfaceControl[] appendCaptureExcludeLayer(\n            android.view.SurfaceControl[] existing, android.view.SurfaceControl extra) {\n        if (extra == null) return existing;\n        try {\n            if (!extra.isValid()) return existing;\n        } catch (Throwable ignored) {\n            return existing;\n        }\n        if (existing == null || existing.length == 0) {\n            return new android.view.SurfaceControl[]{extra};\n        }\n        for (android.view.SurfaceControl layer : existing) {\n            if (layer == extra) return existing;\n        }\n        android.view.SurfaceControl[] result = java.util.Arrays.copyOf(\n                existing, existing.length + 1);\n        result[existing.length] = extra;\n        return result;\n    }\n\n'''

if "appendCaptureExcludeLayer(" not in text:
    count = text.count(helper_anchor)
    if count != 1:
        raise SystemExit(f"expected one helper anchor, found {count}")
    text = text.replace(helper_anchor, helper + helper_anchor, 1)

old = '''                    android.view.SurfaceControl[] excludes = null;\n                    if ((requestedSource == CaptureSourcePolicy.Source.FULL_DISPLAY\n                            || (workstationMode\n                                && requestedSource == CaptureSourcePolicy.Source.LOCAL_LAYER))\n                            && dockWindowSurface != null) {\n                        excludes = new android.view.SurfaceControl[]{dockWindowSurface};\n                    }\n'''
new = '''                    android.view.SurfaceControl[] excludes = null;\n                    if ((requestedSource == CaptureSourcePolicy.Source.FULL_DISPLAY\n                            || (workstationMode\n                                && requestedSource == CaptureSourcePolicy.Source.LOCAL_LAYER))\n                            && dockWindowSurface != null) {\n                        excludes = new android.view.SurfaceControl[]{dockWindowSurface};\n                    }\n                    boolean appHomeActivityLeashExcluded = false;\n                    if (!workstationMode\n                            && requestedSource == CaptureSourcePolicy.Source.FULL_DISPLAY\n                            && sceneState.appHomeHandoffPending()) {\n                        android.view.SurfaceControl homeActivityLeash =\n                                AppHomeAnimationLayerExclusion.currentValidSurface();\n                        if (homeActivityLeash != null) {\n                            excludes = appendCaptureExcludeLayer(excludes, homeActivityLeash);\n                            appHomeActivityLeashExcluded = true;\n                        }\n                    }\n                    logI("APP HOME capture Home-activity excluded="\n                            + appHomeActivityLeashExcluded);\n'''

if "appHomeActivityLeashExcluded" not in text:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected one capture exclusion block, found {count}")
    text = text.replace(old, new, 1)

path.write_text(text)
print("APP HOME Home-activity leash exclusion patch applied")
