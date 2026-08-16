from pathlib import Path

path = Path("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java")
text = path.read_text()

old = """        final boolean useFullscreen = fullscreenCapture
                || (workstationMode && workstationCaptureBurst.isActive());
"""
new = """        // DIAGNOSTIC ONLY: force the composed-display path so HOME can be compared
        // against vendor captureMode(2) without a config-dependent source change.
        final boolean useFullscreen = true;
"""
if text.count(old) != 1:
    raise SystemExit(f"expected one useFullscreen block, got {text.count(old)}")
text = text.replace(old, new, 1)

old = """        final CaptureSourcePolicy.Source requestedSource = selectedSource;
"""
new = """        // DIAGNOSTIC ONLY: HOME normally resolves to WALLPAPER (vendor captureMode 2).
        // Force FULL_DISPLAY here while retaining the existing Floating Dock exclusions.
        // If the live->HOME positional jump disappears, mode 2 is in a different coordinate
        // space from the composed display rather than the problem being capture timing/cache.
        if (!workstationMode && requestScene == CaptureScene.HOME) {
            selectedSource = CaptureSourcePolicy.Source.FULL_DISPLAY;
            logI("DIAGNOSTIC HOME source forced to FULL_DISPLAY mode-1");
        }
        final CaptureSourcePolicy.Source requestedSource = selectedSource;
"""
if text.count(old) != 1:
    raise SystemExit(f"expected one requestedSource line, got {text.count(old)}")
text = text.replace(old, new, 1)

path.write_text(text)
print("Applied HOME mode-1 coordinate-space diagnostic")
