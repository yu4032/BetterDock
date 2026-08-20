from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RENDERER = ROOT / "prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java"
ROOT_TEST = ROOT / "src/test/java/com/hellovoid/liquiddock/PrismalSingleEdgeRefractionContractTest.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


renderer = RENDERER.read_text(encoding="utf-8")
renderer = replace_once(
    renderer,
    "orientation used by upstream Prismal, executes Prismal's original 0.5x blur passes and original\n"
    " * glass vertex/fragment shaders, and returns a transparent full-frame texture containing only the\n",
    "orientation used by upstream Prismal, executes Prismal's original 0.5x blur passes and vertex\n"
    " * shader, then applies LiquidDock's narrow single-edge transmitted-refraction correction to the\n"
    " * vendored upstream fragment before compilation. It returns a transparent full-frame texture\n"
    " * containing only the\n",
    "renderer documentation",
)
renderer = replace_once(
    renderer,
    "        glassProgram = createProgram(PrismalShaderSources.VERTEX, PrismalShaderSources.FRAGMENT);\n",
    "        String glassFragment = PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT);\n"
    "        glassProgram = createProgram(PrismalShaderSources.VERTEX, glassFragment);\n",
    "runtime fragment correction",
)
RENDERER.write_text(renderer, encoding="utf-8")

ROOT_TEST.write_text(
    '''package com.hellovoid.liquiddock;\n\n'''
    '''import static org.junit.Assert.assertTrue;\n\n'''
    '''import java.nio.file.Files;\n'''
    '''import java.nio.file.Path;\n\n'''
    '''import org.junit.Test;\n\n'''
    '''/** Regression contract for the explicit runtime fork from upstream Prismal transmission. */\n'''
    '''public class PrismalSingleEdgeRefractionContractTest {\n'''
    '''    private static final Path PRISMAL = Path.of("prismal/src/main");\n\n'''
    '''    @Test\n'''
    '''    public void upstreamShaderRemainsVendoredWhileRendererCompilesCorrectedTransmission() throws Exception {\n'''
    '''        String upstream = Files.readString(PRISMAL.resolve("res/raw/prismal_fragment.glsl"));\n'''
    '''        String patch = Files.readString(PRISMAL.resolve(\n'''
    '''                "java/com/hellovoid/prismal/PrismalSingleEdgeShader.java"));\n'''
    '''        String renderer = Files.readString(PRISMAL.resolve(\n'''
    '''                "java/com/hellovoid/prismal/PrismalRenderer.java"));\n\n'''
    '''        assertTrue("vendored upstream remains available for provenance",\n'''
    '''                upstream.contains("vec2 baseOffset = lensDeltaUv + snellOff + bulgeUv;"));\n'''
    '''        assertTrue("runtime correction must collapse transmission to one edge vector",\n'''
    '''                patch.contains("vec2 edgeRefractionUv = (dLens * lensDir) / u_resolution;")\n'''
    '''                        && patch.contains("vec2 baseOffset = edgeRefractionUv;"));\n'''
    '''        assertTrue("runtime correction must remove the two extra spatial bands",\n'''
    '''                patch.contains("UPSTREAM_TRANSMITTED_BLOCK")\n'''
    '''                        && patch.contains("SINGLE_EDGE_TRANSMITTED_BLOCK"));\n'''
    '''        assertTrue("renderer must compile the corrected fragment, not the raw vendored fragment",\n'''
    '''                renderer.contains(\n'''
    '''                        "PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT)"));\n'''
    '''    }\n\n'''
    '''    @Test\n'''
    '''    public void correctionUsesTheYDownTextureBasisForChromaticSampling() throws Exception {\n'''
    '''        String patch = Files.readString(PRISMAL.resolve(\n'''
    '''                "java/com/hellovoid/prismal/PrismalSingleEdgeShader.java"));\n'''
    '''        assertTrue(patch.contains(\n'''
    '''                "vec2 dispDir = length(cKy) > 1e-3 ? normalize(cKy) : vec2(0.0, 1.0);"));\n'''
    '''        assertTrue(patch.contains("UPSTREAM_CHROMA_DIRECTION")\n'''
    '''                && patch.contains("TEXTURE_CHROMA_DIRECTION"));\n'''
    '''    }\n'''
    '''}\n''',
    encoding="utf-8",
)

print("patched", RENDERER.relative_to(ROOT))
print("patched", ROOT_TEST.relative_to(ROOT))
