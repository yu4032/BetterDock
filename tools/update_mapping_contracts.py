from pathlib import Path

TEST = Path("src/test/java/com/hellovoid/liquiddock")


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one match, got {count}: {old[:120]!r}")
    path.write_text(text.replace(old, new, 1))


edge = TEST / "Miuix307EdgeOverscanContractTest.java"
replace_once(
    edge,
    '''        assertTrue("normalization FBO must keep the 32dp base and support asymmetric horizontal overscan",\n                view.contains("EDGE_OVERSCAN_DP")\n                        && view.contains("horizontalOverscanPx()")\n                        && view.contains("leftOverscanPx")\n                        && view.contains("rightOverscanPx")\n                        && view.contains("uDockUvRect"));''',
    '''        assertTrue("normalization FBO must keep the 32dp base and asymmetric resolved insets",\n                view.contains("EDGE_OVERSCAN_DP")\n                        && view.contains("horizontalOverscanPx()")\n                        && view.contains("SamplingInsets")\n                        && view.contains("insets.left")\n                        && view.contains("insets.right")\n                        && view.contains("uDockUvRect"));''')
replace_once(
    edge,
    '''        assertTrue(view.contains(\n                "Math.ceil(BLUR_KERNEL_RADIUS_TEXELS / Math.max(BLUR_FBO_SCALE, 0.0001f))"));''',
    '''        assertTrue(view.contains(\n                "BLUR_KERNEL_RADIUS_TEXELS / Math.max(BLUR_FBO_SCALE, 0.0001f)"));''')

horizontal = TEST / "Miuix307HorizontalOverscanGuiContractTest.java"
replace_once(
    horizontal,
    '''        assertTrue(view.contains("leftOverscanPx") && view.contains("rightOverscanPx"));\n        assertTrue(view.contains("horizontalOverscanPx() + Math.max(0, this.leftExtraOverscanPx)"));\n        assertTrue(view.contains("horizontalOverscanPx() + Math.max(0, this.rightExtraOverscanPx)"));\n        assertTrue(view.contains("width + leftOverscanPx + rightOverscanPx"));\n        assertTrue(view.contains("hostScreen[0] - leftOverscanPx"));\n        assertTrue(view.contains("leftOverscanPx / (float) sampleWidth"));''',
    '''        assertTrue(view.contains("leftExtraOverscanPx") && view.contains("rightExtraOverscanPx"));\n        assertTrue(view.contains(\n                "Math.max(horizontalOverscanPx() + Math.max(0, leftExtraOverscanPx), opticalX)"));\n        assertTrue(view.contains(\n                "Math.max(horizontalOverscanPx() + Math.max(0, rightExtraOverscanPx), opticalX)"));\n        assertTrue(view.contains("width + insets.left + insets.right"));\n        assertTrue(view.contains("viewScreen[0] - insets.left"));\n        assertTrue(view.contains("insets.left / (float) sampleWidth"));''')

vertical = TEST / "Miuix307VerticalOverscanGuiContractTest.java"
replace_once(
    vertical,
    '''        assertTrue(view.contains("height + topOverscanPx + bottomOverscanPx"));\n        assertTrue(view.contains("bottomOverscanPx / (float) sampleHeight"));''',
    '''        assertTrue(view.contains("height + insets.top + insets.bottom"));\n        assertTrue(view.contains(\n                "Math.max(Math.max(0, topOverscanPx), opticalY)"));\n        assertTrue(view.contains(\n                "Math.max(Math.max(0, bottomOverscanPx), opticalY)"));\n        assertTrue(view.contains("insets.bottom / (float) sampleHeight"));''')

gpu = TEST / "Miuix307PassBlurGpuDemoTest.java"
replace_once(
    gpu,
    '''        assertTrue(adapter.contains("vec2 textureInputUv = orientedUv")\n                && adapter.contains("uTexMatrix * vec4(textureInputUv, 0.0, 1.0)"));''',
    '''        assertTrue(adapter.contains("compensateSurfaceTextureCropPreservingOrientation")\n                && adapter.contains("float determinant")\n                && adapter.contains("uTexMatrix * vec4(textureInputUv, 0.0, 1.0)"));''')

legacy = TEST / "Miuix307PrismalLegacyParityTest.java"
replace_once(
    legacy,
    '''        assertTrue(adapter.contains("textureScaleX") && adapter.contains("textureOffsetX"));\n        assertTrue(adapter.contains("uTexMatrix"));''',
    '''        assertTrue(adapter.contains("compensateSurfaceTextureCropPreservingOrientation")\n                && adapter.contains("orientationBias")\n                && adapter.contains("float determinant"));\n        assertTrue(adapter.contains("uTexMatrix"));''')

coord = TEST / "Miuix307TextureViewCoordinateParityTest.java"
replace_once(
    coord,
    '''        assertTrue("host screen coordinates must feed the pure window-frame mapping helper",\n                region.contains("materialHost.getLocationOnScreen(hostScreen)")\n                        && region.contains("Miuix307BackdropMapping.compute"));''',
    '''        assertTrue("TextureView screen coordinates must feed the pure window-frame mapping helper",\n                region.contains("getLocationOnScreen(viewScreen)")\n                        && region.contains("Miuix307BackdropMapping.compute"));\n        assertFalse("material parent geometry must not be mixed into the TextureView/FBO UV domain",\n                region.contains("materialHost.getLocationOnScreen")\n                        || region.contains("materialHost.getWidth()")\n                        || region.contains("materialHost.getHeight()"));''')

calibration = TEST / "Miuix307TextureViewPassBlurCalibrationTest.java"
replace_once(
    calibration,
    '''        assertTrue(adapter.contains("textureInputUv.x = (orientedUv.x - textureOffsetX) / textureScaleX")\n                && adapter.contains("uTexMatrix * vec4(textureInputUv, 0.0, 1.0)"));''',
    '''        assertTrue(adapter.contains("compensateSurfaceTextureCropPreservingOrientation")\n                && adapter.contains("orientationBias")\n                && adapter.contains("uTexMatrix * vec4(textureInputUv, 0.0, 1.0)"));''')
