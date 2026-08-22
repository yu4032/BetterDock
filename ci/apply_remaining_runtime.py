from pathlib import Path

P = Path("prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java")
C = Path("src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java")
S = Path("src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java")


def once(text, old, new, label):
    n = text.count(old)
    if n != 1:
        raise RuntimeError(f"{label}: expected 1 match, got {n}")
    return text.replace(old, new, 1)


r = P.read_text()
r = once(
    r,
    "String glassFragment = PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT);",
    "String glassFragment = PrismalComponentGateShader.apply(\n"
    "                PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT));",
    "shader composition",
)
old = '''    public void drawGlass(PrismalGeometry geometry, PrismalParams params) {
        if (geometry == null) throw new IllegalArgumentException("geometry == null");
        if (!glassFrameBegun) {
            throw new IllegalStateException("beginGlassFrame must be called before drawGlass");
        }
        if (geometry.framebufferWidth != width || geometry.framebufferHeight != height) {
            throw new IllegalArgumentException("geometry framebuffer does not match prepared backdrop");
        }
        if (params == null) params = PrismalParams.builder().build();
        renderGlassNode(geometry, params, !legacySingleDraw || glassDrawCount > 0);
        glassDrawCount++;
    }
'''
new = '''    public void drawGlass(PrismalGeometry geometry, PrismalParams params) {
        drawGlass(geometry, params, PrismalHighlightProfile.ALL_ENABLED);
    }

    /** Append one glass node with a renderer-scoped highlight selection. */
    public void drawGlass(PrismalGeometry geometry, PrismalParams params, PrismalHighlightProfile highlightProfile) {
        if (geometry == null) throw new IllegalArgumentException("geometry == null");
        if (!glassFrameBegun) {
            throw new IllegalStateException("beginGlassFrame must be called before drawGlass");
        }
        if (geometry.framebufferWidth != width || geometry.framebufferHeight != height) {
            throw new IllegalArgumentException("geometry framebuffer does not match prepared backdrop");
        }
        if (params == null) params = PrismalParams.builder().build();
        if (highlightProfile == null) highlightProfile = PrismalHighlightProfile.ALL_ENABLED;
        renderGlassNode(geometry, params, highlightProfile, !legacySingleDraw || glassDrawCount > 0);
        glassDrawCount++;
    }
'''
r = once(r, old, new, "draw overload")
r = once(
    r,
    "private void renderGlassNode(PrismalGeometry g, PrismalParams p, boolean composite) {",
    "private void renderGlassNode(PrismalGeometry g, PrismalParams p,\n"
    "                                 PrismalHighlightProfile highlights, boolean composite) {",
    "node signature",
)
r = once(
    r,
    '        uniform1i("u_showNormals", p.showNormals ? 1 : 0);\n',
    '''        uniform1i("u_showNormals", p.showNormals ? 1 : 0);

        uniform1f("u_componentSkyHaze", highlights.skyHaze ? 1f : 0f);
        uniform1f("u_componentSpecular", highlights.specular ? 1f : 0f);
        uniform1f("u_componentLitRim", highlights.litRim ? 1f : 0f);
        uniform1f("u_componentOppositeRim", highlights.oppositeRim ? 1f : 0f);
        uniform1f("u_componentCornerRim", highlights.cornerRim ? 1f : 0f);
        uniform1f("u_componentFaceSheen", highlights.faceSheen ? 1f : 0f);
        uniform1f("u_componentPlainHighlight", highlights.plainHighlight ? 1f : 0f);
        uniform1f("u_componentCaustics", highlights.caustics ? 1f : 0f);
        uniform1f("u_componentPressGlow", highlights.pressGlow ? 1f : 0f);
''',
    "component uniforms",
)
P.write_text(r)

c = C.read_text()
c = once(
    c,
    "import com.hellovoid.liquiddock.config.ConfigSchema;\n",
    "import com.hellovoid.liquiddock.config.ConfigSchema;\n"
    "import com.hellovoid.prismal.PrismalHighlightProfile;\n",
    "config import",
)
c = once(
    c,
    "        final boolean prismalShowNormals;\n",
    "        final boolean prismalShowNormals;\n"
    "        final PrismalHighlightProfile launcherHighlightProfile;\n",
    "config profile field",
)
c = once(
    c,
    "            enabled = c.b(ConfigSchema.Glass.ENABLED.name(),\n"
    "                    ConfigSchema.Glass.ENABLED.runtimeFallback());\n",
    "            enabled = c.b(ConfigSchema.Glass.ENABLED.name(),\n"
    "                    ConfigSchema.Glass.ENABLED.runtimeFallback());\n"
    "            launcherHighlightProfile = LauncherHighlightPreferences.read(c);\n",
    "config profile read",
)
C.write_text(c)

s = S.read_text()
s = once(
    s,
    "import com.hellovoid.prismal.PrismalGeometry;\n",
    "import com.hellovoid.prismal.PrismalGeometry;\n"
    "import com.hellovoid.prismal.PrismalHighlightProfile;\n",
    "session import",
)
s = once(
    s,
    "    private volatile PrismalParams prismalParams;\n",
    "    private volatile PrismalParams prismalParams;\n"
    "    private volatile PrismalHighlightProfile launcherHighlightProfile =\n"
    "            PrismalHighlightProfile.ALL_ENABLED;\n",
    "session profile field",
)
s = once(
    s,
    "        prismalParams = Miuix307PrismalAdapter.toPortable(optical);\n",
    "        prismalParams = Miuix307PrismalAdapter.toPortable(optical);\n"
    "        launcherHighlightProfile = glassConfig != null\n"
    "                ? glassConfig.launcherHighlightProfile\n"
    "                : PrismalHighlightProfile.ALL_ENABLED;\n",
    "session profile refresh",
)
s = once(
    s,
    "            prismalRenderer.drawGlass(prismalGeometry, params);\n",
    "            prismalRenderer.drawGlass(prismalGeometry, params, launcherHighlightProfile);\n",
    "session profiled draw",
)
S.write_text(s)

for name in (
    "Miuix307MaterialPipeline.java",
    "MiuixGlassHook.java",
    "Miuix307ZeroCopyRenderer.java",
    "WorkstationDockGeometryHook.java",
):
    t = Path("src/main/java/com/hellovoid/liquiddock", name).read_text()
    if "LauncherHighlightPreferences" in t or "launcher_surface_component_" in t:
        raise RuntimeError(f"protected owner changed: {name}")

print("runtime transform applied")
