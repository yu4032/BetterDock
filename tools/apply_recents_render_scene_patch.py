from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file_path = Path(path)
    text = file_path.read_text()
    count = text.count(old)
    if count != 1:
        raise RuntimeError(
            f"{path}: expected one match, found {count}\n--- old ---\n{old}"
        )
    file_path.write_text(text.replace(old, new, 1))


POLICY = "src/main/java/com/hellovoid/liquiddock/CaptureSourcePolicy.java"
MAIN = "src/main/java/com/hellovoid/liquiddock/MainHook.java"

replace_once(
    POLICY,
    """    /** Workstation All Apps/Recents are live Launcher scenes. */
    static Source sourceForWorkstationScene(CaptureScene scene, boolean localLayerAvailable) {
        if (scene == CaptureScene.ALL_APPS || scene == CaptureScene.RECENTS) {
            return localLayerAvailable ? Source.LOCAL_LAYER : Source.FULL_DISPLAY;
        }
        // Outside the two workstation-owned live scenes, keep the existing wallpaper baseline.
        return Source.WALLPAPER;
    }
""",
    """    /**
     * Workstation All Apps is a Launcher-owned overlay, so its local layer is the cleanest
     * source. Recents is different: App -> Overview is a mixed SurfaceFlinger composition
     * containing the app/remote-animation leash plus Launcher task views. Capturing only the
     * Launcher ViewRoot drops the app part of that transition, so Recents must use the composed
     * display and let DockLiquidGlassView exclude the Floating Dock surface.
     */
    static Source sourceForWorkstationScene(CaptureScene scene, boolean localLayerAvailable) {
        if (scene == CaptureScene.RECENTS) return Source.FULL_DISPLAY;
        if (scene == CaptureScene.ALL_APPS) {
            return localLayerAvailable ? Source.LOCAL_LAYER : Source.FULL_DISPLAY;
        }
        return Source.WALLPAPER;
    }
""",
)

replace_once(
    MAIN,
    """                    DockLiquidGlassView glass = liquidGlassView;
                    if (glass != null && !workstationMode)
                        glass.setOverviewActive(active, eventName);
                    if (!workstationMode) log("[DC] liquid overview active=" + active
                            + " event=" + eventName);
""",
    """                    DockLiquidGlassView glass = liquidGlassView;
                    if (glass != null)
                        glass.setOverviewActive(active, eventName);
                    log("[DC] liquid overview active=" + active
                            + " event=" + eventName + " workstation=" + workstationMode);
""",
)
