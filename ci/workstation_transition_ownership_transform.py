#!/usr/bin/env python3
"""Apply the workstation/normal-Dock ownership boundary to raw or materialized sources.

The shared-glass feature is materialized by CI patches, while API101 also builds committed raw
sources. Keep one idempotent transform for both shapes so Laptop overlay material cannot become
the normal HotSeats background owner and desktop layout restore never walks Dock hierarchies.
"""

from pathlib import Path

ROOT = Path("src/main/java/com/hellovoid/liquiddock")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one source pattern, found {count}")
    return text.replace(old, new, 1)


def patch_pipeline() -> None:
    path = ROOT / "Miuix307MaterialPipeline.java"
    text = path.read_text()

    helper_anchor = """    static boolean isInstalled() {\n        return installed;\n    }\n"""
    helper = helper_anchor + """\n    /** True only for the material owned by Launcher's ordinary mHotSeats hierarchy. */\n    static boolean isOrdinaryHotSeatsBackground(View candidate) {\n        if (candidate == null) return false;\n        Object ordinaryHotSeats = hotSeatsRef.get();\n        if (ordinaryHotSeats == null) return false;\n        return resolveBackground(ordinaryHotSeats) == candidate;\n    }\n"""
    text = replace_once(text, helper_anchor, helper, "ordinary HotSeats helper")

    old_setup = """                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));\n                        if (MainHook.isWorkstationMode()) return result;\n                        try {\n                            Object launcher = chain.getThisObject();\n                            launcherRef = new WeakReference<>(launcher);\n                            Object hotSeats = HookUtil.getField(launcher, \"mHotSeats\");\n                            hotSeatsRef = new WeakReference<>(hotSeats);\n"""
    new_setup = """                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));\n                        try {\n                            // Launcher.mHotSeats is the authoritative ordinary Dock owner. Retain\n                            // it even when setupViews completes while Laptop mode is active.\n                            Object launcher = chain.getThisObject();\n                            launcherRef = new WeakReference<>(launcher);\n                            Object hotSeats = HookUtil.getField(launcher, \"mHotSeats\");\n                            hotSeatsRef = new WeakReference<>(hotSeats);\n                            if (MainHook.isWorkstationMode()) return result;\n"""
    text = replace_once(text, old_setup, new_setup, "setupViews owner retention")

    # HotSeats.onAttachedToWindow intentionally keeps its workstation early-return before
    # hotSeatsRef assignment. Laptop overlays can reuse HotSeats classes and must not replace the
    # ordinary Launcher.mHotSeats identity captured above.
    path.write_text(text)


def patch_home_grid() -> None:
    path = ROOT / "HomeGridHook.java"
    text = path.read_text()
    anchor = """    static void setWorkstationMode(boolean enabled) {\n        workstationMode = enabled;\n        scheduleAllPageRefresh();\n    }\n"""
    replacement = anchor + """\n    static android.view.View currentWorkspace() {\n        return workspaceRef.get();\n    }\n"""
    text = replace_once(text, anchor, replacement, "Workspace owner accessor")
    path.write_text(text)


def patch_main_hook() -> None:
    path = ROOT / "MainHook.java"
    text = path.read_text()

    # This method is introduced by the newer Dock-shadow ownership path. Older raw feature source
    # does not have it; in that shape there is nothing to gate until shared-glass materialization.
    sync_old = """    static void syncDockShadow(View dockBg, LiquidDockConfig.Dock dock) {\n        if (dockBg == null || dock == null || !dock.enabled) return;\n        setOldBg(dockBg);\n"""
    sync_new = """    static void syncDockShadow(View dockBg, LiquidDockConfig.Dock dock) {\n        if (dockBg == null || dock == null || !dock.enabled) return;\n        if (Miuix307MaterialPipeline.isInstalled()\n                && !Miuix307MaterialPipeline.isOrdinaryHotSeatsBackground(dockBg)) {\n            return;\n        }\n        setOldBg(dockBg);\n"""
    if sync_new not in text and sync_old in text:
        text = text.replace(sync_old, sync_new, 1)
    elif sync_new not in text and "static void syncDockShadow" in text:
        raise SystemExit("syncDockShadow ownership gate: unrecognized method shape")

    old_root = """        View dockBg = oldBg();\n        View root = dockBg == null ? null : dockBg.getRootView();\n"""
    new_root = """        View root = HomeGridHook.currentWorkspace();\n"""
    if old_root in text:
        count = text.count(old_root)
        if count != 2:
            raise SystemExit(f"Workspace-scoped layout restore: expected two old roots, found {count}")
        text = text.replace(old_root, new_root)
    elif text.count(new_root) < 2:
        raise SystemExit("Workspace-scoped layout restore: source pattern missing")

    path.write_text(text)


def main() -> None:
    patch_pipeline()
    patch_home_grid()
    patch_main_hook()
    print("workstation transition ownership: applied")


if __name__ == "__main__":
    main()
