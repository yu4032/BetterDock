from pathlib import Path

TARGET = Path('/tmp/resolve_api101_merge.py')
text = TARGET.read_text()


def replace_once(old: str, new: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f'expected one resolver match, found {count}: {old[:120]!r}')
    text = text.replace(old, new, 1)


replace_once(
    r'''        if (stateChanged && workstationMode) {\n            workstationSuspendWhenBurstSettles = !active;\n            startWorkstationCaptureBurst(active ? "all-apps-enter" : "all-apps-exit");\n        }''',
    r'''        if (stateChanged && workstationMode) {\n            workstationSuspendWhenBurstSettles = !active;\n            if (active) {\n                startWorkstationCaptureBurst("all-apps-enter");\n            } else {\n                startWorkstationCaptureBurst("all-apps-exit");\n            }\n        }''')

replace_once(
    r'''        final String requestDockWindowLayerName = dockWindowLayerName;\n        final String requestDragLayerName = dragLayerName;\n        final int requestWallpaperId = requestedSource == CaptureSourcePolicy.Source.WALLPAPER\n                ? currentWallpaperId() : -1;''',
    r'''        final String requestDockWindowLayerName = dockWindowLayerName;\n        final String requestDragLayerName = dragLayerName;\n        final FullDisplayExclusions requestExclusions =\n                needsDockExclude\n                        && requestFullDisplayExclusions.layerNames == null\n                        && requestDockWindowLayerName != null\n                        ? new FullDisplayExclusions(\n                                CaptureExclusionNames.merge(\n                                        requestDockWindowLayerName,\n                                        requestDragLayerName,\n                                        java.util.Collections.emptyList()),\n                                true)\n                        : requestFullDisplayExclusions;\n        final int requestWallpaperId = requestedSource == CaptureSourcePolicy.Source.WALLPAPER\n                ? currentWallpaperId() : -1;''')

replace_once(
    r'''                    final FullDisplayExclusions fullDisplayExclusions =\n                            requestFullDisplayExclusions;''',
    r'''                    final FullDisplayExclusions fullDisplayExclusions =\n                            requestExclusions;''')

anchor = "if (ROOT / recents_test).exists():\n"
index = text.find(anchor)
if index < 0:
    raise RuntimeError('Recents confirmation test adaptation anchor missing')
index += len(anchor)
adaptation = '''    replace_once(recents_test,\n''' + "'''" + '''        int end = glass.indexOf("/** Never render a HOME wallpaper frame", start);\\n''' + "'''" + ''',\n''' + "'''" + '''        int end = glass.indexOf("void setForegroundOwnership(ForegroundOwnership ownership)", start);\\n''' + "'''" + ''')\n'''
text = text[:index] + adaptation + text[index:]

TARGET.write_text(text)
