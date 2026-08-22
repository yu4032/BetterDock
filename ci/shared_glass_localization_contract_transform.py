from pathlib import Path

PATH = Path("src/test/java/com/hellovoid/liquiddock/config/LauncherGlassConfigContractTest.java")
source = PATH.read_text()

replacements = {
    '        assertTrue(ui.contains("\\\"文件夹\\\""));\n':
        '        assertTrue(ui.contains("R.string.liquid_folder_glass_title"));\n',
    '        assertTrue(ui.contains("\\\"小组件\\\""));\n':
        '        assertTrue(ui.contains("R.string.liquid_widget_glass_title"));\n',
}

for old, new in replacements.items():
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"launcher glass localization contract occurrence count={count}, expected 1: {old.strip()}")
    source = source.replace(old, new, 1)

PATH.write_text(source)
