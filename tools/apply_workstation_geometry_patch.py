from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one match, found {count}\n--- old ---\n{old}")
    p.write_text(text.replace(old, new, 1))


MAIN = "src/main/java/com/hellovoid/liquiddock/MainHook.java"

replace_once(
    MAIN,
    """        installWorkstationDockHooks(classLoader, config.workstation);\n        if (!config.dock.resizeAnimation)\n""",
    """        installWorkstationDockHooks(classLoader, config.workstation);\n        WorkstationDockGeometryHook.install(classLoader, config.workstation);\n        if (!config.dock.resizeAnimation)\n""",
)

replace_once(
    MAIN,
    """        int widthOffset = Math.round(config.dockWidthOffset * scale);\n        int iconTopOffset = Math.round(config.iconTopOffset * scale);\n        int iconBottomOffset = Math.round(config.iconBottomOffset * scale);\n        try {\n            HookUtil.hookMethod(cl,\n                    \"com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2\",\n                    \"setBackgroundWidth\",\n                    chain -> {\n                        Object[] args = chain.getArgs().toArray(new Object[0]);\n                        if (workstationMode && widthOffset != 0) args[0] = (Integer) args[0] + widthOffset;\n                        return chain.proceed(args);\n                    }, int.class);\n            log(\"[DC] workstation Dock width hook offset=\" + widthOffset);\n        } catch (Throwable e) { log(\"[DC] workstation Dock hook unavailable: \" + e); }\n        try {\n""",
    """        int iconTopOffset = Math.round(config.iconTopOffset * scale);\n        int iconBottomOffset = Math.round(config.iconBottomOffset * scale);\n        try {\n""",
)

replace_once(
    MAIN,
    """        workstationMode = enabled;\n        HomeGridHook.setWorkstationMode(enabled);\n        log(\"[DC] Mingou workstation mode changed=\" + enabled);\n""",
    """        workstationMode = enabled;\n        HomeGridHook.setWorkstationMode(enabled);\n        WorkstationDockGeometryHook.onWorkstationModeChanged(enabled);\n        log(\"[DC] Mingou workstation mode changed=\" + enabled);\n""",
)

replace_once(
    MAIN,
    """            if (shadowView != null) {\n                if (workstationMode) { shadowView.setVisibility(View.GONE); return; }\n                if (!anim && bgW != lastShadowW) {\n                    lastShadowW = bgW;\n                    syncShadowGeometry();\n                    shadowView.post(MainHook::syncShadowGeometry);\n                }\n            }\n""",
    """            if (shadowView != null) {\n                if (workstationMode) { shadowView.setVisibility(View.GONE); return; }\n                if (!anim) {\n                    boolean sizeChanged = bgW != lastShadowW;\n                    lastShadowW = bgW;\n                    // Position can change without a width change (startup/translation/layout).\n                    // Always align X/Y once the Dock settles; only size changes need a posted\n                    // second pass after LayoutParams are applied.\n                    syncShadowGeometry();\n                    if (sizeChanged) shadowView.post(MainHook::syncShadowGeometry);\n                }\n            }\n""",
)
