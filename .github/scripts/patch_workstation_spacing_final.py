from pathlib import Path


def replace_once(path, old, new, label):
    p = Path(path)
    text = p.read_text()
    if new in text:
        print(f"{label}: already patched")
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one old block, found {count}")
    p.write_text(text.replace(old, new, 1))
    print(f"{label}: patched")


replace_once(
    "src/main/java/com/hellovoid/liquiddock/HomeGridHook.java",
    '''            int rowGap = baseHeightGap + (workstation ? 0
                    : (portrait ? portraitRowGap : landscapeRowGap));

            int availableWidth = Math.max(countX, width - left - right);
            int availableHeight = height - top - bottom
                - rowGap * Math.max(0, countY - 1);
            int cellSize = Math.min(baseCell, Math.min(
                Math.max(1, availableWidth / countX),
                Math.max(1, availableHeight / countY)));
            int widthGap = countX > 1
                ? Math.max(0, availableWidth - cellSize * countX) / (countX - 1) : 0;
            HookUtil.setIntField(cellLayout, "mCellPaddingLeft", left);
            HookUtil.setIntField(cellLayout, "mCellPaddingTop", top);
            HookUtil.setIntField(cellLayout, "mCellWidth", cellSize);
            HookUtil.setIntField(cellLayout, "mCellHeight", cellSize);
            HookUtil.setIntField(cellLayout, "mWidthGap", widthGap);
            HookUtil.setIntField(cellLayout, "mHeightGap", rowGap);
''',
    '''            int rowGap = baseHeightGap + (workstation ? 0
                    : (portrait ? portraitRowGap : landscapeRowGap));

            int availableWidth = Math.max(countX, width - left - right);
            int allAppsInnerHeight = Math.max(countY, height - top - bottom);
            int availableHeight = workstationAllApps
                    ? allAppsInnerHeight
                    : allAppsInnerHeight - rowGap * Math.max(0, countY - 1);
            int cellSize = Math.min(baseCell, Math.min(
                Math.max(1, availableWidth / countX),
                Math.max(1, availableHeight / countY)));
            int widthGap = countX > 1
                ? Math.max(0, availableWidth - cellSize * countX) / (countX - 1) : 0;
            int heightGap = rowGap;
            if (workstationAllApps && countY > 1) {
                // Absolute top/bottom spacing means the last row must end at height-bottom,
                // not merely fit somewhere inside it. Distribute the remaining inner span
                // between rows just as the horizontal path already does for left/right.
                heightGap = Math.max(0, allAppsInnerHeight - cellSize * countY)
                        / (countY - 1);
            }
            HookUtil.setIntField(cellLayout, "mCellPaddingLeft", left);
            HookUtil.setIntField(cellLayout, "mCellPaddingTop", top);
            HookUtil.setIntField(cellLayout, "mCellWidth", cellSize);
            HookUtil.setIntField(cellLayout, "mCellHeight", cellSize);
            HookUtil.setIntField(cellLayout, "mWidthGap", widthGap);
            HookUtil.setIntField(cellLayout, "mHeightGap", heightGap);
''',
    "HomeGrid true edge spacing",
)

replace_once(
    "src/main/java/com/hellovoid/liquiddock/MainHook.java",
    '''        HomeGridHook.setWorkstationHorizontalOffset(Math.round(
                config.workstation.gridHorizontalOffset * gridScale));
        HomeGridHook.setWorkstationAllAppsOffsets(
                Math.round(config.workstation.allAppsLandscapeHorizontalOffset * gridScale),
                Math.round(config.workstation.allAppsLandscapeVerticalOffset * gridScale),
                Math.round(config.workstation.allAppsPortraitHorizontalOffset * gridScale),
                Math.round(config.workstation.allAppsPortraitVerticalOffset * gridScale));
''',
    '''        HomeGridHook.setWorkstationHorizontalOffset(Math.round(
                config.workstation.gridHorizontalOffset * gridScale));
        // All Apps controls are absolute edge spacing in dp. They must not inherit the
        // ordinary grid_margins_dp unit switch, otherwise the same spacing setting changes
        // meaning when the normal desktop grid unit mode changes.
        float workstationAllAppsScale = android.content.res.Resources.getSystem().getDisplayMetrics().density;
        HomeGridHook.setWorkstationAllAppsOffsets(
                Math.round(config.workstation.allAppsLandscapeHorizontalOffset * workstationAllAppsScale),
                Math.round(config.workstation.allAppsLandscapeVerticalOffset * workstationAllAppsScale),
                Math.round(config.workstation.allAppsPortraitHorizontalOffset * workstationAllAppsScale),
                Math.round(config.workstation.allAppsPortraitVerticalOffset * workstationAllAppsScale));
''',
    "MainHook All Apps dp scale",
)

schema = Path("src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java")
text = schema.read_text()
for key in [
    "workstation_all_apps_landscape_horizontal_offset",
    "workstation_all_apps_landscape_vertical_offset",
    "workstation_all_apps_portrait_horizontal_offset",
    "workstation_all_apps_portrait_vertical_offset",
]:
    old = f'                "{key}", 0, 0, 0, -240, 240,'
    new = f'                "{key}", 0, 0, 0, 0, 240,'
    if new in text:
        continue
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"ConfigSchema {key}: expected exactly one old line, found {count}")
    text = text.replace(old, new, 1)
schema.write_text(text)
print("ConfigSchema absolute spacing ranges: patched")

compose = Path("src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt")
text = compose.read_text()
replacements = [
    (
        '    "workstation_all_apps_landscape_horizontal_offset" -> "仅调整工作台所有应用横屏图标区的水平位置"',
        '    "workstation_all_apps_landscape_horizontal_offset" -> "直接设置工作台所有应用横屏图标区左右间距；不叠加系统默认位置"',
    ),
    (
        '    "workstation_all_apps_landscape_vertical_offset" -> "仅调整工作台所有应用横屏图标区的垂直位置"',
        '    "workstation_all_apps_landscape_vertical_offset" -> "直接设置工作台所有应用横屏图标区上下间距；不叠加系统默认位置"',
    ),
    (
        '    "workstation_all_apps_portrait_horizontal_offset" -> "仅调整工作台所有应用竖屏图标区的水平位置"',
        '    "workstation_all_apps_portrait_horizontal_offset" -> "直接设置工作台所有应用竖屏图标区左右间距；不叠加系统默认位置"',
    ),
    (
        '    "workstation_all_apps_portrait_vertical_offset" -> "仅调整工作台所有应用竖屏图标区的垂直位置"',
        '    "workstation_all_apps_portrait_vertical_offset" -> "直接设置工作台所有应用竖屏图标区上下间距；不叠加系统默认位置"',
    ),
    (
        '    IntSpec(ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_HORIZONTAL_OFFSET, "所有应用 · 横屏水平偏移"),',
        '    IntSpec(ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_HORIZONTAL_OFFSET, "所有应用 · 横屏水平间距"),',
    ),
    (
        '    IntSpec(ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_VERTICAL_OFFSET, "所有应用 · 横屏垂直偏移"),',
        '    IntSpec(ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_VERTICAL_OFFSET, "所有应用 · 横屏垂直间距"),',
    ),
    (
        '    IntSpec(ConfigSchema.Workstation.ALL_APPS_PORTRAIT_HORIZONTAL_OFFSET, "所有应用 · 竖屏水平偏移"),',
        '    IntSpec(ConfigSchema.Workstation.ALL_APPS_PORTRAIT_HORIZONTAL_OFFSET, "所有应用 · 竖屏水平间距"),',
    ),
    (
        '    IntSpec(ConfigSchema.Workstation.ALL_APPS_PORTRAIT_VERTICAL_OFFSET, "所有应用 · 竖屏垂直偏移"),',
        '    IntSpec(ConfigSchema.Workstation.ALL_APPS_PORTRAIT_VERTICAL_OFFSET, "所有应用 · 竖屏垂直间距"),',
    ),
]
for old, new in replacements:
    if new in text:
        continue
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Compose replacement failed: {old!r}, found {count}")
    text = text.replace(old, new, 1)
compose.write_text(text)
print("Compose All Apps spacing labels/summaries: patched")
