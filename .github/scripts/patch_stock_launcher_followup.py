from pathlib import Path

# Remove the obsolete merged-vertical post-layout translation. Exact top/bottom spacing is
# now owned by HomeGridHook through mCellPaddingTop + mHeightGap.
p = Path("src/main/java/com/hellovoid/liquiddock/WorkstationWallpaperOnlyHook.java")
text = p.read_text(encoding="utf-8")
old_call = "        installAllAppsVerticalOffset(classLoader);\n"
if text.count(old_call) != 1:
    raise RuntimeError(f"expected one obsolete vertical-offset install call, found {text.count(old_call)}")
text = text.replace(old_call, "", 1)
marker = "    /**\n     * HomeGridHook already applies as much of the requested All Apps Y translation as\n"
pos = text.find(marker)
if pos < 0:
    raise RuntimeError("obsolete All Apps vertical-offset method marker not found")
text = text[:pos].rstrip() + "\n}\n"
text = text.replace("import android.content.res.Configuration;\n", "")
text = text.replace("import android.view.ViewGroup;\n", "")
text = text.replace("    private static final String CELL_LAYOUT =\n            \"com.miui.home.launcher.CellLayout\";\n", "")
p.write_text(text, encoding="utf-8")

# Preserve the entire ConfigCodec regression suite. Only its exhaustive default-export
# contract changes because four new current top/bottom spacing keys are ALWAYS-exported.
t = Path("src/test/java/com/hellovoid/liquiddock/config/ConfigCodecTest.java")
test = t.read_text(encoding="utf-8")
old_size = "        assertEquals(100, exported.size());\n"
if test.count(old_size) != 1:
    raise RuntimeError(f"expected one historical default count assertion, found {test.count(old_size)}")
test = test.replace(old_size, "        assertEquals(104, exported.size());\n", 1)
old_anchor = "        assertEquals(Boolean.FALSE, exported.get(\"workstation_dock_customization\"));\n        assertFalse(exported.containsKey(\"dock_divider_enabled\"));\n"
new_anchor = "        assertEquals(Boolean.FALSE, exported.get(\"workstation_dock_customization\"));\n        assertEquals(0, exported.get(\"workstation_all_apps_landscape_top_spacing\"));\n        assertEquals(0, exported.get(\"workstation_all_apps_landscape_bottom_spacing\"));\n        assertEquals(0, exported.get(\"workstation_all_apps_portrait_top_spacing\"));\n        assertEquals(0, exported.get(\"workstation_all_apps_portrait_bottom_spacing\"));\n        assertFalse(exported.containsKey(\"dock_divider_enabled\"));\n"
if test.count(old_anchor) != 1:
    raise RuntimeError(f"expected one ConfigCodec default anchor, found {test.count(old_anchor)}")
test = test.replace(old_anchor, new_anchor, 1)
t.write_text(test, encoding="utf-8")
print("removed obsolete workstation merged-vertical translation; minimally updated ConfigCodec defaults")
