#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_if_old(path, old, new, label):
    p = ROOT / path
    text = p.read_text()
    if new in text:
        return False
    if text.count(old) != 1:
        raise SystemExit(f"{path}: {label} matched {text.count(old)} times")
    p.write_text(text.replace(old, new, 1))
    return True


def main():
    changed = False
    schema_test = "src/test/java/com/hellovoid/liquiddock/config/ConfigSchemaTest.java"
    changed |= replace_if_old(
        schema_test,
        'assertComposeIntSpec(ConfigSchema.Grid.LANDSCAPE_HORIZONTAL_DISTANCE, 0, -600, 600);',
        'assertComposeIntSpec(ConfigSchema.Grid.LANDSCAPE_HORIZONTAL_DISTANCE, 0, 0, 600);',
        "landscape Edge Offset test range",
    )
    changed |= replace_if_old(
        schema_test,
        'assertComposeIntSpec(ConfigSchema.Grid.PORTRAIT_HORIZONTAL_DISTANCE, 0, -600, 600);',
        'assertComposeIntSpec(ConfigSchema.Grid.PORTRAIT_HORIZONTAL_DISTANCE, 0, 0, 600);',
        "portrait Edge Offset test range",
    )
    changed |= replace_if_old(
        schema_test,
        'assertComposeIntSpec(ConfigSchema.Grid.LANDSCAPE_ROW_GAP, 0, -200, 400);',
        'assertComposeIntSpec(ConfigSchema.Grid.LANDSCAPE_ROW_GAP, 0, 0, 400);',
        "landscape Margin test range",
    )
    changed |= replace_if_old(
        schema_test,
        'assertComposeIntSpec(ConfigSchema.Grid.PORTRAIT_ROW_GAP, 0, -200, 400);',
        'assertComposeIntSpec(ConfigSchema.Grid.PORTRAIT_ROW_GAP, 0, 0, 400);',
        "portrait Margin test range",
    )

    semantics_test = "src/test/java/com/hellovoid/liquiddock/GridSpacingSemanticsContractTest.java"
    old = '''        assertTrue(ConfigSchema.Grid.MARGINS_OFFSET.runtimeFallback());\n    }'''
    new = '''        assertTrue(ConfigSchema.Grid.MARGINS_OFFSET.runtimeFallback());\n        assertEquals(Integer.valueOf(0), ConfigSchema.Grid.LANDSCAPE_ROW_GAP.runtimeFallback());\n        assertEquals(Integer.valueOf(0), ConfigSchema.Grid.PORTRAIT_ROW_GAP.runtimeFallback());\n    }'''
    changed |= replace_if_old(
        semantics_test,
        old,
        new,
        "Margin runtime fallback regression assertions",
    )

    print("PR22 grid contract tests updated" if changed else "PR22 grid contract tests already current")


if __name__ == "__main__":
    main()
