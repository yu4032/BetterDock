from pathlib import Path
import subprocess

# Reuse the reviewed split script blob, then tighten legacy compatibility before build.
base = subprocess.check_output([
    "git", "cat-file", "blob", "fc983882427ef608b4f1b06caa0fc8e8a2cec2ff"
], text=True)
exec(compile(base, "apply_divider_split_base.py", "exec"), {"__name__": "__main__"})

path = Path("src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java")
text = path.read_text(encoding="utf-8")
old = '''            // Historical storage is tenths of dp. Normalize it here so the Hook only
            // sees real dp and never knows about dock_dimensions_dp.
            widthDp = Math.max(0f, c.f("dock_divider_width_dp", 10) / 10f);
            heightPercent = clamp(c.f("dock_divider_height_scale", 60), 0f, 100f);
            yOffsetDp = c.f("dock_divider_y_offset", 0) / 10f;
            colorR = channel(c.i("dock_divider_color_r", 255));
            colorG = channel(c.i("dock_divider_color_g", 255));
            colorB = channel(c.i("dock_divider_color_b", 255));
            alpha = channel(c.i("dock_divider_alpha", 128));
'''
new = '''            // Historical storage is tenths of dp. Normalize it here so the Hook only
            // sees real dp and never knows about dock_dimensions_dp. Missing fields must
            // stay zero in legacy mode because zero meant "do not override system".
            float widthDefault = explicitMode ? 10f : 0f;
            float heightDefault = explicitMode ? 60f : 0f;
            int colorDefault = explicitMode ? 255 : 0;
            int alphaDefault = explicitMode ? 128 : 0;
            widthDp = Math.max(0f, c.f("dock_divider_width_dp", widthDefault) / 10f);
            heightPercent = clamp(c.f("dock_divider_height_scale", heightDefault), 0f, 100f);
            yOffsetDp = c.f("dock_divider_y_offset", 0) / 10f;
            colorR = channel(c.i("dock_divider_color_r", colorDefault));
            colorG = channel(c.i("dock_divider_color_g", colorDefault));
            colorB = channel(c.i("dock_divider_color_b", colorDefault));
            alpha = channel(c.i("dock_divider_alpha", alphaDefault));
'''
if old not in text:
    raise SystemExit("expected Divider defaults block not found")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
