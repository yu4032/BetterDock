#!/usr/bin/env python3
from pathlib import Path

path = Path('prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java')
text = path.read_text()


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected one anchor, found {count}')
    text = text.replace(old, new, 1)


replace_once(
    '''    private int blurWidth;\n    private int blurHeight;\n''',
    '''    private int blurWidth;\n    private int blurHeight;\n    private boolean backdropPrepared;\n    private boolean glassFrameBegun;\n    private int glassDrawCount;\n    private boolean legacySingleDraw;\n''',
    'batch state')

old_render = '''    public int render(int backgroundTexture2D, PrismalGeometry geometry, PrismalParams params) {\n        if (backgroundTexture2D <= 0) throw new IllegalArgumentException("background texture <= 0");\n        if (geometry == null) throw new IllegalArgumentException("geometry == null");\n        if (params == null) params = PrismalParams.builder().build();\n        ensurePrograms();\n        ensureTargets(geometry.framebufferWidth, geometry.framebufferHeight);\n\n        int[] previousFbo = new int[1];\n        int[] previousViewport = new int[4];\n        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, previousFbo, 0);\n        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, previousViewport, 0);\n        try {\n            renderSourceAdapter(backgroundTexture2D);\n            renderBlur(params);\n            renderGlass(geometry, params);\n            int error = GLES20.glGetError();\n            if (error != GLES20.GL_NO_ERROR) {\n                throw new IllegalStateException("Prismal GLES error=0x" + Integer.toHexString(error));\n            }\n            return outputTexture;\n        } finally {\n            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, previousFbo[0]);\n            GLES20.glViewport(previousViewport[0], previousViewport[1],\n                    previousViewport[2], previousViewport[3]);\n            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);\n        }\n    }\n\n'''
new_render = '''    public int render(int backgroundTexture2D, PrismalGeometry geometry, PrismalParams params) {\n        if (backgroundTexture2D <= 0) throw new IllegalArgumentException("background texture <= 0");\n        if (geometry == null) throw new IllegalArgumentException("geometry == null");\n        if (params == null) params = PrismalParams.builder().build();\n        // Keep the existing Dock entry point and optics model. Batch rendering only splits the\n        // same source/blur/draw sequence so Launcher can reuse one prepared backdrop.\n        ensurePrograms();\n        ensureTargets(geometry.framebufferWidth, geometry.framebufferHeight);\n\n        int[] previousFbo = new int[1];\n        int[] previousViewport = new int[4];\n        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, previousFbo, 0);\n        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, previousViewport, 0);\n        try {\n            prepareBackdrop(backgroundTexture2D, geometry.framebufferWidth,\n                    geometry.framebufferHeight, params);\n            beginGlassFrame();\n            legacySingleDraw = true;\n            try {\n                drawGlass(geometry, params);\n            } finally {\n                legacySingleDraw = false;\n            }\n            int error = GLES20.glGetError();\n            if (error != GLES20.GL_NO_ERROR) {\n                throw new IllegalStateException("Prismal GLES error=0x" + Integer.toHexString(error));\n            }\n            return outputTexture;\n        } finally {\n            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, previousFbo[0]);\n            GLES20.glViewport(previousViewport[0], previousViewport[1],\n                    previousViewport[2], previousViewport[3]);\n            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);\n        }\n    }\n\n    /** Prepare one normalized/blurred backdrop for one or more glass nodes. */\n    public void prepareBackdrop(int backgroundTexture2D, int framebufferWidth, int framebufferHeight,\n                                PrismalParams params) {\n        if (backgroundTexture2D <= 0) throw new IllegalArgumentException("background texture <= 0");\n        if (framebufferWidth <= 0 || framebufferHeight <= 0) {\n            throw new IllegalArgumentException("framebuffer dimensions <= 0");\n        }\n        if (params == null) params = PrismalParams.builder().build();\n        ensurePrograms();\n        ensureTargets(framebufferWidth, framebufferHeight);\n        renderSourceAdapter(backgroundTexture2D);\n        renderBlur(params);\n        backdropPrepared = true;\n        glassFrameBegun = false;\n        glassDrawCount = 0;\n    }\n\n    /** Clear the transparent scene output once before appending glass nodes. */\n    public void beginGlassFrame() {\n        if (!backdropPrepared) {\n            throw new IllegalStateException("prepareBackdrop must be called before beginGlassFrame");\n        }\n        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, outputFramebuffer);\n        GLES20.glViewport(0, 0, width, height);\n        GLES20.glDisable(GLES20.GL_BLEND);\n        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);\n        GLES20.glClearColor(0f, 0f, 0f, 0f);\n        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);\n        glassFrameBegun = true;\n        glassDrawCount = 0;\n    }\n\n    /** Append one glass node using the currently prepared backdrop. */\n    public void drawGlass(PrismalGeometry geometry, PrismalParams params) {\n        if (geometry == null) throw new IllegalArgumentException("geometry == null");\n        if (!glassFrameBegun) {\n            throw new IllegalStateException("beginGlassFrame must be called before drawGlass");\n        }\n        if (geometry.framebufferWidth != width || geometry.framebufferHeight != height) {\n            throw new IllegalArgumentException("geometry framebuffer does not match prepared backdrop");\n        }\n        if (params == null) params = PrismalParams.builder().build();\n        renderGlassNode(geometry, params, !legacySingleDraw || glassDrawCount > 0);\n        glassDrawCount++;\n    }\n\n'''
replace_once(old_render, new_render, 'legacy render delegation')

replace_once(
    '''    private void renderGlass(PrismalGeometry g, PrismalParams p) {\n        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, outputFramebuffer);\n        GLES20.glViewport(0, 0, width, height);\n        GLES20.glDisable(GLES20.GL_BLEND);\n        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);\n        GLES20.glClearColor(0f, 0f, 0f, 0f);\n        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);\n        GLES20.glUseProgram(glassProgram);\n''',
    '''    private void renderGlassNode(PrismalGeometry g, PrismalParams p, boolean composite) {\n        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, outputFramebuffer);\n        GLES20.glViewport(0, 0, width, height);\n        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);\n        if (composite) {\n            GLES20.glEnable(GLES20.GL_BLEND);\n            GLES20.glBlendFuncSeparate(\n                    GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA,\n                    GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);\n        } else {\n            GLES20.glDisable(GLES20.GL_BLEND);\n        }\n        GLES20.glUseProgram(glassProgram);\n''',
    'glass node body')

replace_once(
    '''        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);\n        GLES20.glDisableVertexAttribArray(position);\n    }\n\n    private void bindInterleavedQuad''',
    '''        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);\n        GLES20.glDisableVertexAttribArray(position);\n        GLES20.glDisable(GLES20.GL_BLEND);\n    }\n\n    private void bindInterleavedQuad''',
    'blend cleanup')

replace_once(
    '''        width = height = blurWidth = blurHeight = 0;\n    }\n''',
    '''        width = height = blurWidth = blurHeight = 0;\n        backdropPrepared = false;\n        glassFrameBegun = false;\n        glassDrawCount = 0;\n        legacySingleDraw = false;\n    }\n''',
    'batch state release')

for token in ('enum Mode', 'LAUNCHER_COMPACT', 'PrismalLauncherCompactShader'):
    if token in text:
        raise SystemExit(f'forbidden launcher optics token present: {token}')
if 'PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT)' not in text:
    raise SystemExit('single-edge optics source changed unexpectedly')

path.write_text(text)
