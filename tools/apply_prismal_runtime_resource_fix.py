from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MODULE = ROOT / "prismal/src/main"
RENDERER = MODULE / "java/com/hellovoid/prismal/PrismalRenderer.java"
SOURCES = MODULE / "java/com/hellovoid/prismal/PrismalShaderSources.java"
VIEW = ROOT / "src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java"
BOUNDARY_TEST = ROOT / "src/test/java/com/hellovoid/liquiddock/PrismalModuleBoundaryContractTest.java"

SHADERS = {
    "VERTEX": MODULE / "res/raw/prismal_vertex.glsl",
    "FRAGMENT": MODULE / "res/raw/prismal_fragment.glsl",
    "BLUR_VERTEX": MODULE / "res/raw/prismal_blur_vertex.glsl",
    "BLUR_H": MODULE / "res/raw/prismal_blur_h.glsl",
    "BLUR_V": MODULE / "res/raw/prismal_blur_v.glsl",
}


def java_string(text: str) -> str:
    return (
        '"'
        + text.replace('\\', '\\\\')
              .replace('"', '\\"')
              .replace('\r', '\\r')
              .replace('\n', '\\n')
              .replace('\t', '\\t')
        + '"'
    )


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


# Generate compile-time shader source constants from the checked-in upstream GLSL files.
parts = [
    "package com.hellovoid.prismal;\n\n",
    "/** Compile-time Prismal shader sources generated from the checked-in upstream GLSL files. */\n",
    "final class PrismalShaderSources {\n",
]
for name, path in SHADERS.items():
    source = path.read_text(encoding="utf-8")
    parts.append(f"    static final String {name} = {java_string(source)};\n")
parts.append("\n    private PrismalShaderSources() {}\n}\n")
SOURCES.write_text("".join(parts), encoding="utf-8")

renderer = RENDERER.read_text(encoding="utf-8")
for line in [
    "import android.content.Context;\n",
    "import android.content.res.Resources;\n",
    "import java.io.ByteArrayOutputStream;\n",
    "import java.io.IOException;\n",
    "import java.io.InputStream;\n",
    "import java.nio.charset.StandardCharsets;\n",
]:
    renderer = replace_once(renderer, line, "", f"remove import {line.strip()}")

renderer = replace_once(
    renderer,
    "    private final Resources resources;\n",
    "",
    "remove Resources field",
)
renderer = replace_once(
    renderer,
    "    public PrismalRenderer(Context context) {\n"
    "        if (context == null) throw new IllegalArgumentException(\"context == null\");\n"
    "        resources = context.getResources();\n"
    "        fullQuad = floatBuffer(FULL_QUAD);\n"
    "        glassQuad = floatBuffer(GLASS_QUAD);\n"
    "        blurQuad = floatBuffer(BLUR_QUAD);\n"
    "    }\n",
    "    public PrismalRenderer() {\n"
    "        fullQuad = floatBuffer(FULL_QUAD);\n"
    "        glassQuad = floatBuffer(GLASS_QUAD);\n"
    "        blurQuad = floatBuffer(BLUR_QUAD);\n"
    "    }\n",
    "replace Context constructor",
)
renderer = replace_once(
    renderer,
    "        String blurVertex = readRaw(R.raw.prismal_blur_vertex);\n"
    "        sourceProgram = createProgram(SOURCE_VERTEX, SOURCE_FRAGMENT);\n"
    "        blurHProgram = createProgram(blurVertex, readRaw(R.raw.prismal_blur_h));\n"
    "        blurVProgram = createProgram(blurVertex, readRaw(R.raw.prismal_blur_v));\n"
    "        glassProgram = createProgram(readRaw(R.raw.prismal_vertex), readRaw(R.raw.prismal_fragment));\n",
    "        sourceProgram = createProgram(SOURCE_VERTEX, SOURCE_FRAGMENT);\n"
    "        blurHProgram = createProgram(PrismalShaderSources.BLUR_VERTEX, PrismalShaderSources.BLUR_H);\n"
    "        blurVProgram = createProgram(PrismalShaderSources.BLUR_VERTEX, PrismalShaderSources.BLUR_V);\n"
    "        glassProgram = createProgram(PrismalShaderSources.VERTEX, PrismalShaderSources.FRAGMENT);\n",
    "replace raw shader loading",
)
read_raw_pattern = re.compile(
    r"\n    private String readRaw\(int id\) \{.*?\n    \}\n",
    re.DOTALL,
)
renderer, count = read_raw_pattern.subn("\n", renderer, count=1)
if count != 1:
    raise SystemExit(f"remove readRaw: expected one method, found {count}")

renderer = renderer.replace(
    "It has no dependency on View, SurfaceTexture, OES, Dock, Xposed, or HyperOS.</p>",
    "It has no dependency on View, SurfaceTexture, OES, Dock, Xposed, HyperOS, Context, or Resources.</p>",
)
RENDERER.write_text(renderer, encoding="utf-8")

view = VIEW.read_text(encoding="utf-8")
view = replace_once(
    view,
    "if (prismalRenderer == null) prismalRenderer = new PrismalRenderer(getContext());",
    "if (prismalRenderer == null) prismalRenderer = new PrismalRenderer();",
    "switch PrismalRenderer to context-free constructor",
)
VIEW.write_text(view, encoding="utf-8")

boundary = BOUNDARY_TEST.read_text(encoding="utf-8")
needle = '        assertFalse(renderer.contains("Miuix307"));\n'
replacement = (
    needle
    + '        assertFalse(renderer.contains("android.content.Context"));\n'
    + '        assertFalse(renderer.contains("android.content.res.Resources"));\n'
    + '        assertFalse(renderer.contains("openRawResource"));\n'
    + '        assertTrue(renderer.contains("PrismalShaderSources.FRAGMENT"));\n'
)
boundary = replace_once(boundary, needle, replacement, "strengthen context-free module contract")
BOUNDARY_TEST.write_text(boundary, encoding="utf-8")

print("generated", SOURCES.relative_to(ROOT))
print("patched", RENDERER.relative_to(ROOT))
print("patched", VIEW.relative_to(ROOT))
print("patched", BOUNDARY_TEST.relative_to(ROOT))
