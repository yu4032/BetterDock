from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TESTS = ROOT / "src/test/java/com/hellovoid/liquiddock"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


# Overscan remains real sampled pixels; only ownership moved into one immutable generation.
path = TESTS / "Miuix307EdgeOverscanContractTest.java"
text = path.read_text(encoding="utf-8")
text = replace_once(
    text,
    '''                view.contains("createPrismalGeometry()")
                        && view.contains("dockUvLeft")
                        && view.contains("dockUvBottom")
                        && view.contains("dockUvWidth * fboWidth")
                        && view.contains("dockUvHeight * fboHeight"));''',
    '''                view.contains("createPrismalGeometry(mapping)")
                        && view.contains("mapping.dockUvLeft")
                        && view.contains("mapping.dockUvBottom")
                        && view.contains("mapping.dockUvWidth * mapping.sampleWidth")
                        && view.contains("mapping.dockUvHeight * mapping.sampleHeight"));''',
    "edge overscan geometry ownership",
)
text = replace_once(
    text,
    '''        assertTrue("normalization mirror guard must use overscan-sample validity",
                view.contains("validSampleLeft, validSampleBottom, validSampleRight, validSampleTop"));
        assertTrue("final coverage/scissor must remain tied to the visible Dock",
                view.contains("producerCoverage = dock.coverage")
                        && view.contains("validDockLeft * outputWidth")
                        && view.contains("validDockBottom * outputHeight"));''',
    '''        assertTrue("normalization mirror guard must use overscan-sample validity",
                view.contains("mapping.validSampleLeft, mapping.validSampleBottom")
                        && view.contains("mapping.validSampleRight, mapping.validSampleTop"));
        assertTrue("final coverage/scissor must remain tied to the visible Dock",
                view.contains("producerCoverage = dock.coverage")
                        && view.contains("mapping.validDockLeft * mapping.visibleWidth")
                        && view.contains("mapping.validDockBottom * mapping.visibleHeight"));''',
    "edge overscan validity ownership",
)
path.write_text(text, encoding="utf-8")

# Partial coverage still clips the final visible Dock; it is now read from the captured generation.
path = TESTS / "Miuix307PrismalParityRepairTest.java"
text = path.read_text(encoding="utf-8")
text = replace_once(
    text,
    '                view.contains("producerCoverage == Miuix307BackdropMapping.Coverage.PARTIAL"));',
    '                view.contains("mapping.coverage == Miuix307BackdropMapping.Coverage.PARTIAL"));',
    "partial coverage snapshot ownership",
)
path.write_text(text, encoding="utf-8")

# Normalization, geometry, and crop must all consume the exact inset/sample dimensions computed
# by the UI mapping generation, rather than independently re-resolving them on the GL thread.
path = TESTS / "Miuix307TextureViewBackdropMappingTest.java"
text = path.read_text(encoding="utf-8")
text = replace_once(
    text,
    '''        assertTrue(mapping.contains("SamplingInsets insets = resolveSamplingInsets(visibleWidth, visibleHeight);"));''',
    '''        assertTrue(mapping.contains(
                "SamplingInsets insets = resolveSamplingInsets(visibleWidth, visibleHeight, frameParams);"));
        assertTrue(source.contains("ensureFboSizeExact(mapping.sampleWidth, mapping.sampleHeight)"));
        assertTrue(source.contains("renderNormalizationPass(mapping)"));
        assertTrue(source.contains("createPrismalGeometry(mapping)"));
        assertTrue(source.contains("renderCompositePass(prismalTexture, mapping)"));''',
    "single resolved inset generation",
)
path.write_text(text, encoding="utf-8")

# Producer allocation may be carried as snapshot metadata for generation checks/diagnostics, but
# it must never become an input to the pure window-frame content mapping helper.
path = TESTS / "Miuix307TextureViewCoordinateParityTest.java"
text = path.read_text(encoding="utf-8")
text = replace_once(
    text,
    '''        assertFalse("mSurfaceSize is producer allocation geometry, not backdrop content geometry",
                region.contains("boundSurfaceWidth") || region.contains("boundSurfaceHeight"));''',
    '''        assertTrue("snapshot may carry producer allocation only as generation metadata",
                region.contains("boundSurfaceWidth, boundSurfaceHeight")
                        && region.contains("boundBufferWidth, boundBufferHeight"));
        assertFalse("producer allocation must never feed the window-frame mapping helper",
                region.contains("Miuix307BackdropMapping.compute(\n                boundSurfaceWidth")
                        || region.contains("Miuix307BackdropMapping.compute(\n                boundSurfaceHeight")
                        || region.contains("Miuix307BackdropMapping.compute(\n                boundBufferWidth")
                        || region.contains("Miuix307BackdropMapping.compute(\n                boundBufferHeight"));''',
    "coordinate parity allocation metadata",
)
path.write_text(text, encoding="utf-8")

# Framebuffer and glass domains remain separate, now explicitly derived from one immutable mapping.
path = TESTS / "Miuix307TextureViewStrongRefractionTest.java"
text = path.read_text(encoding="utf-8")
text = replace_once(
    text,
    '''        assertTrue(source.contains("PrismalGeometry prismalGeometry = createPrismalGeometry()")
                && source.contains("prismalRenderer.render("));''',
    '''        assertTrue(source.contains("PrismalGeometry prismalGeometry = createPrismalGeometry(mapping)")
                && source.contains("prismalRenderer.render("));
        assertTrue(source.contains("mapping.dockUvWidth * mapping.sampleWidth")
                && source.contains("mapping.dockUvHeight * mapping.sampleHeight"));''',
    "strong refraction snapshot geometry",
)
path.write_text(text, encoding="utf-8")

for p in [
    "Miuix307EdgeOverscanContractTest.java",
    "Miuix307PrismalParityRepairTest.java",
    "Miuix307TextureViewBackdropMappingTest.java",
    "Miuix307TextureViewCoordinateParityTest.java",
    "Miuix307TextureViewStrongRefractionTest.java",
]:
    print("migrated", p)
