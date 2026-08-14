from pathlib import Path

p = Path("src/main/java/com/hellovoid/liquiddock/WorkstationWallpaperOnlyHook.java")
text = p.read_text(encoding="utf-8")

# The old post-layout vertical translation existed only because All Apps had one merged
# vertical offset. HomeGridHook now owns exact absolute top/bottom spacing and recomputes
# mHeightGap, so keeping this second translation would both fail compilation and double-move rows.
old_call = "        installAllAppsVerticalOffset(classLoader);\n"
if text.count(old_call) != 1:
    raise RuntimeError(f"expected one obsolete vertical-offset install call, found {text.count(old_call)}")
text = text.replace(old_call, "", 1)

marker = "    /**\n     * HomeGridHook already applies as much of the requested All Apps Y translation as\n"
pos = text.find(marker)
if pos < 0:
    raise RuntimeError("obsolete All Apps vertical-offset method marker not found")
# It is intentionally the final method in this helper.
text = text[:pos].rstrip() + "\n}\n"
text = text.replace("import android.content.res.Configuration;\n", "")
text = text.replace("import android.view.ViewGroup;\n", "")
text = text.replace("    private static final String CELL_LAYOUT =\n            \"com.miui.home.launcher.CellLayout\";\n", "")
p.write_text(text, encoding="utf-8")
print("removed obsolete workstation merged-vertical post-layout translation")
