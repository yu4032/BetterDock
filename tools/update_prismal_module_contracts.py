from pathlib import Path


def replace(path, old, new):
    p = Path(path)
    s = p.read_text()
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one match, got {count}: {old[:100]!r}")
    p.write_text(s.replace(old, new, 1))

# GUI/config still feeds LiquidDock's compatibility parameter object, but rendering ownership moved
# to the portable module. Legacy fields may remain in app config; they are intentionally not mapped.
replace(
    "src/test/java/com/hellovoid/liquiddock/Miuix307GlassCustomizationContractTest.java",
    '        assertTrue(view.contains("Miuix307PrismalMaterial.blurSigma"));',
    '        assertTrue(view.contains("portablePrismalParams")\n'
    '                && view.contains("prismalRenderer.render("));'
)

replace(
    "src/test/java/com/hellovoid/liquiddock/Miuix307PassBlurGpuDemoTest.java",
    '        assertTrue(view.contains("renderNormalizationPass"));\n'
    '        assertTrue(view.contains("renderBlurPasses"));\n'
    '        assertTrue(view.contains("renderMaterialPass"));',
    '        assertTrue(view.contains("renderNormalizationPass"));\n'
    '        assertTrue(view.contains("prismalRenderer.render("));\n'
    '        assertTrue(view.contains("renderCompositePass"));'
)
replace(
    "src/test/java/com/hellovoid/liquiddock/Miuix307PassBlurGpuDemoTest.java",
    '        String prismal = Files.readString(MAIN.resolve("Miuix307PrismalShader.java"));',
    '        String prismal = Files.readString(Path.of("prismal/src/main/res/raw/prismal_fragment.glsl"));'
)
replace(
    "src/test/java/com/hellovoid/liquiddock/Miuix307PassBlurGpuDemoTest.java",
    '        assertTrue(view.contains("Miuix307PrismalShader.FRAGMENT_SHADER"));',
    '        assertTrue(view.contains("prismalRenderer.render("));'
)

old = '''    @Test
    public void textureViewUsesHalfResolutionTwoPassGaussianAndClearsTargets() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        String shaders = Files.readString(MAIN.resolve("Miuix307PassBlurShaders.java"));

        assertTrue(view.contains("BLUR_FBO_SCALE = 0.5f"));
        assertTrue(view.contains("rawFramebuffer"));
        assertTrue(view.contains("blurFramebufferH"));
        assertTrue(view.contains("blurFramebufferV"));
        assertTrue(view.contains("renderNormalizationPass"));
        assertTrue(view.contains("renderBlurPasses"));
        assertTrue(view.contains("renderMaterialPass"));
        assertTrue(view.contains("glClearColor(0f, 0f, 0f, 0f)"));
        assertTrue(view.contains("GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)"));
        assertTrue(shaders.contains("GAUSSIAN_BLUR_FRAGMENT"));
        assertTrue(shaders.contains("uDirection"));
        assertTrue(shaders.contains("uSigma"));
    }
'''
new = '''    @Test
    public void portablePrismalOwnsHalfResolutionTwoPassGaussianAndClearsTargets() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        String renderer = Files.readString(Path.of(
                "prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java"));
        String blurH = Files.readString(Path.of("prismal/src/main/res/raw/prismal_blur_h.glsl"));
        String blurV = Files.readString(Path.of("prismal/src/main/res/raw/prismal_blur_v.glsl"));

        assertTrue(view.contains("rawFramebuffer") && view.contains("renderNormalizationPass"));
        assertTrue(view.contains("prismalRenderer.render("));
        assertTrue(renderer.contains("BLUR_FBO_SCALE = 0.5f"));
        assertTrue(renderer.contains("blurFramebufferH") && renderer.contains("blurFramebufferV"));
        assertTrue(renderer.contains("sourceFramebuffer") && renderer.contains("outputFramebuffer"));
        assertTrue(renderer.contains("glClearColor(0f, 0f, 0f, 0f)"));
        assertTrue(renderer.contains("GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)"));
        assertTrue(blurH.contains("for (float i = -15.0; i <= 15.0; i += 1.0)"));
        assertTrue(blurV.contains("for (float i = -15.0; i <= 15.0; i += 1.0)"));
    }
'''
replace(
    "src/test/java/com/hellovoid/liquiddock/Miuix307PrismalParityRepairTest.java",
    old, new
)

replace(
    "src/test/java/com/hellovoid/liquiddock/Miuix307TextureViewPassBlurCalibrationTest.java",
    '        String shader = Files.readString(MAIN.resolve("Miuix307PrismalShader.java"));',
    '        String shader = Files.readString(Path.of("prismal/src/main/res/raw/prismal_fragment.glsl"));'
)
replace(
    "src/test/java/com/hellovoid/liquiddock/Miuix307TextureViewPassBlurCalibrationTest.java",
    '        assertTrue(view.contains("Miuix307PrismalShader.FRAGMENT_SHADER"));',
    '        assertTrue(view.contains("prismalRenderer.render("));'
)

old = '''    @Test
    public void drawUploadsTextureViewSizeAndUpstreamUniformsForPixelStableOptics() throws Exception {
        String source = view();
        String params = material();
        assertTrue(source.contains("Miuix307PrismalMaterial.applyUniforms(")
                && source.contains("outputWidth")
                && source.contains("outputHeight"));
        assertTrue(params.contains("uniform2f(program, \\"u_resolution\\", width, height)"));
        assertTrue(params.contains("uniform2f(program, \\"u_glassSize\\", width, height)"));
    }
'''
# Source text contains Java-escaped quotes, not Python-escaped backslashes after parsing.
old = old.replace('\\\\"', '\\"')
new = '''    @Test
    public void portableRendererKeepsFramebufferAndGlassDomainsSeparateForPixelStableOptics() throws Exception {
        String source = view();
        String renderer = Files.readString(Path.of(
                "prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java"));
        assertTrue(source.contains("PrismalGeometry prismalGeometry = createPrismalGeometry()")
                && source.contains("prismalRenderer.render("));
        assertTrue(renderer.contains("uniform2f(\\"u_resolution\\", width, height)"));
        assertTrue(renderer.contains("uniform2f(\\"u_mousePos\\", g.centerX, height - g.centerY)"));
        assertTrue(renderer.contains("uniform2f(\\"u_glassSize\\", g.glassWidth, g.glassHeight)"));
    }
'''
replace(
    "src/test/java/com/hellovoid/liquiddock/Miuix307TextureViewStrongRefractionTest.java",
    old, new
)

print("updated Prismal module contracts")
