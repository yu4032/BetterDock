from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RENDERER = ROOT / "prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java"
TEST = ROOT / "src/test/java/com/hellovoid/liquiddock/PrismalModuleBoundaryContractTest.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)

renderer = RENDERER.read_text(encoding="utf-8")
renderer = replace_once(
    renderer,
    "import java.nio.FloatBuffer;\n",
    "import java.nio.FloatBuffer;\nimport java.util.HashMap;\nimport java.util.Map;\n",
    "add uniform cache imports",
)
renderer = replace_once(
    renderer,
    "    private final FloatBuffer blurQuad;\n",
    "    private final FloatBuffer blurQuad;\n"
    "    // Match upstream Prismal's renderer semantics: resolve each glass-program uniform after\n"
    "    // link, retain its location (including -1 for linker-inactive declarations), and pass\n"
    "    // that location directly to glUniform*. OpenGL deliberately ignores location -1.\n"
    "    private final Map<String, Integer> glassUniformLocations = new HashMap<>();\n",
    "add uniform cache field",
)
renderer = replace_once(
    renderer,
    "        glassProgram = createProgram(PrismalShaderSources.VERTEX, PrismalShaderSources.FRAGMENT);\n",
    "        glassProgram = createProgram(PrismalShaderSources.VERTEX, PrismalShaderSources.FRAGMENT);\n"
    "        glassUniformLocations.clear();\n",
    "clear cache after link",
)
renderer = replace_once(
    renderer,
    '        GLES20.glUniform1i(requireUniform(glassProgram, "u_backgroundTexture"), 0);\n',
    '        GLES20.glUniform1i(glassUniformLocation("u_backgroundTexture"), 0);\n',
    "background sampler official semantics",
)
renderer = replace_once(
    renderer,
    '        GLES20.glUniform1i(requireUniform(glassProgram, "u_blurredTexture"), 1);\n',
    '        GLES20.glUniform1i(glassUniformLocation("u_blurredTexture"), 1);\n',
    "blur sampler official semantics",
)
renderer = replace_once(
    renderer,
    '        GLES20.glUniform1i(requireUniform(glassProgram, "u_useBlurredTexture"), 1);\n',
    '        GLES20.glUniform1i(glassUniformLocation("u_useBlurredTexture"), 1);\n',
    "blur switch official semantics",
)
old_helpers = '''    private void uniform1f(String name, float value) {\n        GLES20.glUniform1f(requireUniform(glassProgram, name), value);\n    }\n    private void uniform1i(String name, int value) {\n        GLES20.glUniform1i(requireUniform(glassProgram, name), value);\n    }\n    private void uniform2f(String name, float x, float y) {\n        GLES20.glUniform2f(requireUniform(glassProgram, name), x, y);\n    }\n    private void uniform4f(String name, float x, float y, float z, float w) {\n        GLES20.glUniform4f(requireUniform(glassProgram, name), x, y, z, w);\n    }\n'''
new_helpers = '''    private int glassUniformLocation(String name) {\n        Integer cached = glassUniformLocations.get(name);\n        if (cached != null) return cached;\n        int location = GLES20.glGetUniformLocation(glassProgram, name);\n        glassUniformLocations.put(name, location);\n        return location;\n    }\n\n    private void uniform1f(String name, float value) {\n        GLES20.glUniform1f(glassUniformLocation(name), value);\n    }\n    private void uniform1i(String name, int value) {\n        GLES20.glUniform1i(glassUniformLocation(name), value);\n    }\n    private void uniform2f(String name, float x, float y) {\n        GLES20.glUniform2f(glassUniformLocation(name), x, y);\n    }\n    private void uniform4f(String name, float x, float y, float z, float w) {\n        GLES20.glUniform4f(glassUniformLocation(name), x, y, z, w);\n    }\n'''
renderer = replace_once(renderer, old_helpers, new_helpers, "replace strict glass uniform helpers")
renderer = replace_once(
    renderer,
    "        sourceProgram = blurHProgram = blurVProgram = glassProgram = 0;\n",
    "        sourceProgram = blurHProgram = blurVProgram = glassProgram = 0;\n"
    "        glassUniformLocations.clear();\n",
    "clear cache on close",
)
RENDERER.write_text(renderer, encoding="utf-8")

test = TEST.read_text(encoding="utf-8")
needle = '        assertTrue(renderer.contains("PrismalShaderSources.FRAGMENT"));\n'
addition = (
    needle
    + '        assertTrue(renderer.contains("glassUniformLocations"));\n'
    + '        assertTrue(renderer.contains("GLES20.glGetUniformLocation(glassProgram, name)"));\n'
    + '        assertTrue(renderer.contains("GLES20.glUniform1f(glassUniformLocation(name), value)"));\n'
    + '        assertFalse(renderer.contains("GLES20.glUniform1f(requireUniform(glassProgram, name), value)"));\n'
    + '        assertFalse(renderer.contains("requireUniform(glassProgram, \\\"u_backgroundTexture\\\")"));\n'
)
test = replace_once(test, needle, addition, "add official uniform semantics contract")
TEST.write_text(test, encoding="utf-8")

print("patched", RENDERER.relative_to(ROOT))
print("patched", TEST.relative_to(ROOT))
