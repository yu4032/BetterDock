from pathlib import Path

p = Path("src/main/java/com/hellovoid/liquiddock/MainHook.java")
text = p.read_text()

old = "boolean grid8x4 = grid.enabled, dp = grid.dp, offsets = grid.offsets;"
new = "boolean customGridEnabled = grid.enabled, dp = grid.dp, offsets = grid.offsets;"
if text.count(old) != 1:
    raise SystemExit(f"expected one grid master anchor, found {text.count(old)}")
text = text.replace(old, new, 1)

old = "HomeGridHook.install(classLoader, grid8x4,"
new = "HomeGridHook.install(classLoader, customGridEnabled,"
if text.count(old) != 1:
    raise SystemExit(f"expected one HomeGridHook install anchor, found {text.count(old)}")
text = text.replace(old, new, 1)

old = """            Math.round(grid.landscapeIndicatorY * gridScale),
            Math.round(grid.portraitIndicatorY * gridScale));
        HomeGridHook.setWorkstationHorizontalOffset"""
new = """            Math.round(grid.landscapeIndicatorY * gridScale),
            Math.round(grid.portraitIndicatorY * gridScale));
        HomeGridProfileOverlayHook.install(classLoader, customGridEnabled, grid.profile);
        HomeGridHook.setWorkstationHorizontalOffset"""
if text.count(old) != 1:
    raise SystemExit(f"expected one overlay insertion anchor, found {text.count(old)}")
text = text.replace(old, new, 1)

p.write_text(text)
print("Task 4 MainHook integration applied")
