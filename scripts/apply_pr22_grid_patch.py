#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def file(path):
    return ROOT / path


def sub_once(path, pattern, replacement, flags=0, label=None):
    p = file(path)
    text = p.read_text()
    new, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count != 1:
        raise SystemExit(f"{path}: {label or pattern!r} matched {count} times")
    p.write_text(new)


def main():
    home_path = "src/main/java/com/hellovoid/liquiddock/HomeGridHook.java"
    if "HomeGridGeometryPolicy.compute(" in file(home_path).read_text():
        print("PR22 grid spacing patch already applied")
        return

    schema = "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java"
    sub_once(schema,
        r'("grid_margins_dp",\s*true,\s*)false(,\s*true,\s*ConfigKey\.ExportMode\.ALWAYS)',
        r'\1true\2', label="MARGINS_DP runtime fallback")
    sub_once(schema,
        r'("grid_margins_offset",\s*true,\s*)false(,\s*true,\s*ConfigKey\.ExportMode\.ALWAYS)',
        r'\1true\2', label="MARGINS_OFFSET runtime fallback")
    sub_once(schema,
        r'("grid_landscape_horizontal_distance",\s*0,\s*0,\s*0,\s*)-600(,\s*600)',
        r'\g<1>0\2', label="landscape Edge Offset range")
    sub_once(schema,
        r'("grid_portrait_horizontal_distance",\s*0,\s*0,\s*0,\s*)-600(,\s*600)',
        r'\g<1>0\2', label="portrait Edge Offset range")
    sub_once(schema,
        r'("grid_landscape_row_gap",\s*0,\s*null,\s*0,\s*)-200(,\s*400)',
        r'\g<1>0\2', label="landscape Margin range")
    sub_once(schema,
        r'("grid_portrait_row_gap",\s*0,\s*null,\s*0,\s*)-200(,\s*400)',
        r'\g<1>0\2', label="portrait Margin range")
    p = file(schema)
    text = p.read_text()
    old_comment = "// Runtime defaults for row gaps are compatibility-dependent and stay in LiquidDockConfig."
    if old_comment not in text:
        raise SystemExit(f"{schema}: row-gap comment anchor missing")
    p.write_text(text.replace(old_comment,
        "// Row-gap keys now represent actual inter-cell Margin; zero selects automatic spacing.", 1))

    config = "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java"
    sub_once(config,
        r'landscapeRowGap = c\.f\("grid_landscape_row_gap",\s*offsets \? 0 : \(dp \? 1 : 3\)\);',
        'landscapeRowGap = c.f(ConfigSchema.Grid.LANDSCAPE_ROW_GAP.name(),\n'
        '                    ConfigSchema.Grid.LANDSCAPE_ROW_GAP.runtimeFallback());',
        label="landscape Margin fallback")
    sub_once(config,
        r'portraitRowGap = c\.f\("grid_portrait_row_gap",\s*offsets \? 0 : \(dp \? 1 : 3\)\);',
        'portraitRowGap = c.f(ConfigSchema.Grid.PORTRAIT_ROW_GAP.name(),\n'
        '                    ConfigSchema.Grid.PORTRAIT_ROW_GAP.runtimeFallback());',
        label="portrait Margin fallback")

    main_replacement = '''        LiquidDockConfig.Grid grid = config.grid;
        boolean customGridEnabled = grid.enabled, dp = grid.dp;
        float gridScale = dp ? android.content.res.Resources.getSystem().getDisplayMetrics().density : 1f;
        float landEdge = Math.max(0f, grid.landscapeHorizontal);
        float portEdge = Math.max(0f, grid.portraitHorizontal);
        float landMargin = grid.landscapeRowGap;
        float portMargin = grid.portraitRowGap;
        DockDividerHook.install(classLoader);
        HomeGridHook.install(classLoader, customGridEnabled,
            Math.round(landEdge * gridScale), Math.round(landEdge * gridScale),
            0, 0,
            Math.round(portEdge * gridScale), Math.round(portEdge * gridScale),
            0, 0,
            Math.round(landMargin * gridScale), Math.round(portMargin * gridScale),
            Math.round(grid.landscapeIndicatorY * gridScale),
            Math.round(grid.portraitIndicatorY * gridScale));'''
    sub_once(
        "src/main/java/com/hellovoid/liquiddock/MainHook.java",
        r'        LiquidDockConfig\.Grid grid = config\.grid;\n.*?Math\.round\(grid\.portraitIndicatorY \* gridScale\)\);',
        main_replacement, flags=re.S, label="MainHook normal grid setup")

    sub_once(home_path,
        r'import android\.content\.res\.Configuration;\n',
        'import android.content.res.Configuration;\nimport android.graphics.Insets;\nimport android.view.WindowInsets;\n',
        label="HomeGrid imports")

    p = file(home_path)
    text = p.read_text()
    workstation_pattern = re.compile(
        r'(\s*boolean workstation = workstationAllApps\s*\n'
        r'\s*\|\| workstationMode \|\| MainHook\.isWorkstationMode\(\);)')
    matches = list(workstation_pattern.finditer(text))
    if len(matches) != 1:
        raise SystemExit(f"{home_path}: workstation anchor matched {len(matches)} times")
    insertion = '''

            if (!workstation) {
                int dockBarHeight = 0;
                try {
                    dockBarHeight = Math.max(0, (Integer) HookUtil.invoke(
                            config, "getDockBarHeight"));
                } catch (Throwable ignored) {}
                int[] safe = normalWorkspaceSafeInsets(layout, dockBarHeight);
                int edgeOffset = Math.max(0,
                        portrait ? portraitLeft : landscapeLeft);
                int configuredMargin = portrait ? portraitRowGap : landscapeRowGap;
                int screenWidth = width;
                android.view.View root = layout.getRootView();
                if (root != null && root.getWidth() > 0) screenWidth = root.getWidth();
                int margin = configuredMargin > 0
                        ? configuredMargin
                        : HomeGridGeometryPolicy.resolveMarginPx(screenWidth, 0);
                HomeGridGeometryPolicy.Result geometry = HomeGridGeometryPolicy.compute(
                        width, height, countX, countY,
                        safe[0], safe[1], safe[2], safe[3],
                        edgeOffset, margin);
                HookUtil.setIntField(cellLayout, "mCellPaddingLeft", geometry.left);
                HookUtil.setIntField(cellLayout, "mCellPaddingTop", geometry.top);
                HookUtil.setIntField(cellLayout, "mCellWidth", geometry.cellWidth);
                HookUtil.setIntField(cellLayout, "mCellHeight", geometry.cellHeight);
                HookUtil.setIntField(cellLayout, "mWidthGap", geometry.widthGap);
                HookUtil.setIntField(cellLayout, "mHeightGap", geometry.heightGap);
                return;
            }'''
    p.write_text(workstation_pattern.sub(lambda m: m.group(1) + insertion, text, count=1))

    helper = '''    private static int[] normalWorkspaceSafeInsets(
            android.view.View layout, int dockBarHeight) {
        int left = 0;
        int top = 0;
        int right = 0;
        int bottom = Math.max(0, dockBarHeight);
        try {
            WindowInsets windowInsets = layout.getRootWindowInsets();
            android.view.View root = layout.getRootView();
            if (windowInsets == null || root == null) {
                return new int[]{left, top, right, bottom};
            }
            Insets statusBars = windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.statusBars());
            Insets systemBars = windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars());
            int[] layoutScreen = new int[2];
            int[] rootScreen = new int[2];
            layout.getLocationOnScreen(layoutScreen);
            root.getLocationOnScreen(rootScreen);

            left = Math.max(0,
                    rootScreen[0] + systemBars.left - layoutScreen[0]);
            right = Math.max(0,
                    layoutScreen[0] + layout.getWidth()
                            - (rootScreen[0] + root.getWidth() - systemBars.right));
            top = Math.max(0,
                    rootScreen[1] + statusBars.top - layoutScreen[1]);
            int systemBottom = Math.max(0,
                    layoutScreen[1] + layout.getHeight()
                            - (rootScreen[1] + root.getHeight() - systemBars.bottom));
            bottom = Math.max(bottom, systemBottom);
        } catch (Throwable e) {
            MainHook.log("[DC] workspace system inset resolve failed: " + e);
        }
        return new int[]{left, top, right, bottom};
    }

    private static boolean isLaptopAllApps(Object cellLayout) {'''
    sub_once(home_path,
        r'    private static boolean isLaptopAllApps\(Object cellLayout\) \{',
        helper, label="normal Workspace safe-inset helper")

    compose_path = "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"
    p = file(compose_path)
    text = p.read_text()
    summaries = {
        "grid_landscape_horizontal_distance": "Edge Offset 只调整左右边缘，不改变纵向位置",
        "grid_landscape_top_distance": "兼容旧配置；当前普通桌面纵向区域由状态栏与 Dock 自动计算",
        "grid_landscape_bottom_distance": "兼容旧配置；当前普通桌面纵向区域由状态栏与 Dock 自动计算",
        "grid_portrait_horizontal_distance": "Edge Offset 只调整左右边缘，不改变纵向位置",
        "grid_portrait_top_distance": "兼容旧配置；当前普通桌面纵向区域由状态栏与 Dock 自动计算",
        "grid_portrait_bottom_distance": "兼容旧配置；当前普通桌面纵向区域由状态栏与 Dock 自动计算",
        "grid_landscape_row_gap": "Margin 是图标单元实际间距；0 表示自动使用屏幕宽度的 0.9%",
        "grid_portrait_row_gap": "Margin 是图标单元实际间距；0 表示自动使用屏幕宽度的 0.9%",
    }
    for key, value in summaries.items():
        pattern = re.compile(rf'(^\s*"{re.escape(key)}"\s*->\s*)"[^"]*"', re.M)
        text, count = pattern.subn(lambda m, value=value: m.group(1) + '"' + value + '"', text, count=1)
        if count != 1:
            raise SystemExit(f"{compose_path}: summary {key} matched {count} times")
    p.write_text(text)

    grid_specs = '''private val gridSpecs = listOf(
    IntSpec(ConfigSchema.Grid.LANDSCAPE_HORIZONTAL_DISTANCE, "横屏 Edge Offset"),
    IntSpec(ConfigSchema.Grid.LANDSCAPE_ROW_GAP, "横屏 Margin"),
    IntSpec(ConfigSchema.Grid.PORTRAIT_HORIZONTAL_DISTANCE, "竖屏 Edge Offset"),
    IntSpec(ConfigSchema.Grid.PORTRAIT_ROW_GAP, "竖屏 Margin"),
    IntSpec(ConfigSchema.Grid.LANDSCAPE_INDICATOR_Y, "横屏指示器 Y"),
    IntSpec(ConfigSchema.Grid.PORTRAIT_INDICATOR_Y, "竖屏指示器 Y"),
)
private val dockSpecs'''
    sub_once(compose_path,
        r'private val gridSpecs = listOf\(.*?\n\)\nprivate val dockSpecs',
        grid_specs, flags=re.S, label="Compose gridSpecs")

    print("PR22 grid spacing source patch applied")


if __name__ == "__main__":
    main()
