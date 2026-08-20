package com.hellovoid.prismal;

import android.content.Context;
import android.content.res.Resources;
import android.opengl.GLES20;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Reusable OpenGL ES 2.0 Prismal renderer.
 *
 * <p>The caller owns EGL/current-context lifetime and supplies a normal GL_TEXTURE_2D framebuffer
 * texture in GL-native bottom-left orientation. This class standardizes that texture into the
 * orientation used by upstream Prismal, executes Prismal's original 0.5x blur passes and original
 * glass vertex/fragment shaders, and returns a transparent full-frame texture containing only the
 * rendered glass. It has no dependency on View, SurfaceTexture, OES, Dock, Xposed, or HyperOS.</p>
 */
public final class PrismalRenderer implements AutoCloseable {
    private static final float BLUR_FBO_SCALE = 0.5f;

    private static final float[] FULL_QUAD = new float[]{
            -1f, -1f, 0f, 0f,
             1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
             1f,  1f, 1f, 1f
    };
    private static final float[] GLASS_QUAD = new float[]{
            -0.5f, -0.5f,
             0.5f, -0.5f,
            -0.5f,  0.5f,
            -0.5f,  0.5f,
             0.5f, -0.5f,
             0.5f,  0.5f
    };
    private static final float[] BLUR_QUAD = new float[]{
            -1f, -1f,
             1f, -1f,
            -1f,  1f,
            -1f,  1f,
             1f, -1f,
             1f,  1f
    };

    // Boundary adapter only. It converts a conventional FBO texture (v=0 visual bottom) into the
    // same row orientation Prismal receives from GLUtils.texImage2D(Bitmap) (v=0 visual top).
    private static final String SOURCE_VERTEX = """
            attribute vec2 aPosition;
            attribute vec2 aUv;
            varying vec2 vUv;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                vUv = aUv;
            }
            """;
    private static final String SOURCE_FRAGMENT = """
            precision highp float;
            uniform sampler2D uTexture;
            varying vec2 vUv;
            void main() {
                gl_FragColor = texture2D(uTexture, vec2(vUv.x, 1.0 - vUv.y));
            }
            """;

    private final Resources resources;
    private final FloatBuffer fullQuad;
    private final FloatBuffer glassQuad;
    private final FloatBuffer blurQuad;

    private int sourceProgram;
    private int blurHProgram;
    private int blurVProgram;
    private int glassProgram;

    private int sourceTexture;
    private int sourceFramebuffer;
    private int blurTextureH;
    private int blurFramebufferH;
    private int blurTextureV;
    private int blurFramebufferV;
    private int outputTexture;
    private int outputFramebuffer;
    private int width;
    private int height;
    private int blurWidth;
    private int blurHeight;

    public PrismalRenderer(Context context) {
        if (context == null) throw new IllegalArgumentException("context == null");
        resources = context.getResources();
        fullQuad = floatBuffer(FULL_QUAD);
        glassQuad = floatBuffer(GLASS_QUAD);
        blurQuad = floatBuffer(BLUR_QUAD);
    }

    /**
     * Render one frame. The returned texture is owned by this renderer and remains valid until
     * resize/release. Pixels outside the glass quad remain transparent.
     */
    public int render(int backgroundTexture2D, PrismalGeometry geometry, PrismalParams params) {
        if (backgroundTexture2D <= 0) throw new IllegalArgumentException("background texture <= 0");
        if (geometry == null) throw new IllegalArgumentException("geometry == null");
        if (params == null) params = PrismalParams.builder().build();
        ensurePrograms();
        ensureTargets(geometry.framebufferWidth, geometry.framebufferHeight);

        int[] previousFbo = new int[1];
        int[] previousViewport = new int[4];
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, previousFbo, 0);
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, previousViewport, 0);
        try {
            renderSourceAdapter(backgroundTexture2D);
            renderBlur(params);
            renderGlass(geometry, params);
            int error = GLES20.glGetError();
            if (error != GLES20.GL_NO_ERROR) {
                throw new IllegalStateException("Prismal GLES error=0x" + Integer.toHexString(error));
            }
            return outputTexture;
        } finally {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, previousFbo[0]);
            GLES20.glViewport(previousViewport[0], previousViewport[1],
                    previousViewport[2], previousViewport[3]);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        }
    }

    public int outputTexture() { return outputTexture; }
    public int framebufferWidth() { return width; }
    public int framebufferHeight() { return height; }

    private void ensurePrograms() {
        if (sourceProgram != 0 && blurHProgram != 0 && blurVProgram != 0 && glassProgram != 0) {
            return;
        }
        String blurVertex = readRaw(R.raw.prismal_blur_vertex);
        sourceProgram = createProgram(SOURCE_VERTEX, SOURCE_FRAGMENT);
        blurHProgram = createProgram(blurVertex, readRaw(R.raw.prismal_blur_h));
        blurVProgram = createProgram(blurVertex, readRaw(R.raw.prismal_blur_v));
        glassProgram = createProgram(readRaw(R.raw.prismal_vertex), readRaw(R.raw.prismal_fragment));
        if (sourceProgram == 0 || blurHProgram == 0 || blurVProgram == 0 || glassProgram == 0) {
            throw new IllegalStateException("Prismal shader program creation failed");
        }
    }

    private void ensureTargets(int nextWidth, int nextHeight) {
        if (width == nextWidth && height == nextHeight && outputTexture != 0) return;
        releaseTargets();
        width = Math.max(1, nextWidth);
        height = Math.max(1, nextHeight);
        blurWidth = Math.max(1, (int) (width * BLUR_FBO_SCALE));
        blurHeight = Math.max(1, (int) (height * BLUR_FBO_SCALE));

        sourceTexture = createTexture(width, height);
        sourceFramebuffer = createFramebuffer(sourceTexture);
        blurTextureH = createTexture(blurWidth, blurHeight);
        blurFramebufferH = createFramebuffer(blurTextureH);
        blurTextureV = createTexture(blurWidth, blurHeight);
        blurFramebufferV = createFramebuffer(blurTextureV);
        outputTexture = createTexture(width, height);
        outputFramebuffer = createFramebuffer(outputTexture);
    }

    private void renderSourceAdapter(int inputTexture) {
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, sourceFramebuffer);
        GLES20.glViewport(0, 0, width, height);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(sourceProgram);
        bindInterleavedQuad(sourceProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTexture);
        GLES20.glUniform1i(requireUniform(sourceProgram, "uTexture"), 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindInterleavedQuad(sourceProgram);
    }

    private void renderBlur(PrismalParams p) {
        float sigma = Math.max(p.blurRadiusPx * BLUR_FBO_SCALE, 0.5f);
        renderBlurPass(blurHProgram, sourceTexture, blurFramebufferH, sigma);
        renderBlurPass(blurVProgram, blurTextureH, blurFramebufferV, sigma);
    }

    private void renderBlurPass(int program, int inputTexture, int framebuffer, float sigma) {
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);
        GLES20.glViewport(0, 0, blurWidth, blurHeight);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(program);
        int position = requireAttrib(program, "a_position");
        blurQuad.position(0);
        GLES20.glEnableVertexAttribArray(position);
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, blurQuad);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTexture);
        GLES20.glUniform1i(requireUniform(program, "u_texture"), 0);
        GLES20.glUniform2f(requireUniform(program, "u_texelSize"),
                1f / Math.max(1, blurWidth), 1f / Math.max(1, blurHeight));
        GLES20.glUniform1f(requireUniform(program, "u_sigma"), sigma);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
        GLES20.glDisableVertexAttribArray(position);
    }

    private void renderGlass(PrismalGeometry g, PrismalParams p) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, outputFramebuffer);
        GLES20.glViewport(0, 0, width, height);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(glassProgram);

        int position = requireAttrib(glassProgram, "a_position");
        glassQuad.position(0);
        GLES20.glEnableVertexAttribArray(position);
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, glassQuad);

        uniform2f("u_resolution", width, height);
        uniform2f("u_mousePos", g.centerX, height - g.centerY);
        uniform2f("u_glassSize", g.glassWidth, g.glassHeight);
        uniform4f("u_cornerRadii", g.topLeftRadius, g.topRightRadius,
                g.bottomRightRadius, g.bottomLeftRadius);
        uniform1f("u_refractionInset", p.refractionInsetPx);
        uniform1f("u_sminSmoothing", p.sminSmoothingPx);
        uniform1f("u_edgeRefractionFalloff", p.edgeRefractionFalloff);
        uniform1f("u_ior", p.ior);
        uniform1f("u_glassThickness", p.glassThicknessPx);
        uniform1f("u_normalStrength", p.normalStrength);
        uniform1f("u_displacementScale", p.displacementScale);
        uniform1f("u_heightTransitionWidth", p.heightTransitionWidthPx);

        float minGlassDim = Math.min(g.glassWidth, g.glassHeight);
        float domeBoost = 1f + 0.55f * clamp(p.liquidDome, 0f, 2f);
        float refractionHeight = p.heightTransitionWidthPx * domeBoost;
        float lensPx = refractionHeight * 2f * p.displacementScale * p.lensRefractionScale;
        uniform1f("u_lensRefractionPx", clamp(lensPx, 4f, Math.max(4f, minGlassDim * 0.85f)));
        uniform1f("u_lensDepthEffect", p.lensDepthEffect);

        uniform1f("u_chromaticAberration", Math.max(0f, p.chromaticAberration));
        uniform1f("u_dispersionR", p.dispersionR);
        uniform1f("u_dispersionB", p.dispersionB);
        uniform1f("u_vibrancy", p.vibrancy);
        uniform1f("u_plainHighlight", p.plainHighlight);
        uniform1f("u_liquidDome", p.liquidDome);
        uniform1f("u_fresnelReflect", p.fresnelReflect);
        uniform1f("u_brightness", p.brightness);
        uniform4f("u_glassColor", p.tintR, p.tintG, p.tintB, p.tintA);
        uniform1f("u_highlightWidth", p.highlightWidth);
        uniform2f("u_lightDir", p.lightDirX, p.lightDirY);
        uniform1f("u_specular", p.specular);
        uniform1f("u_shininess", p.shininess);
        uniform1f("u_rimStrength", p.rimStrength);
        uniform4f("u_shadowColor", p.shadowR, p.shadowG, p.shadowB, p.shadowA);
        uniform1f("u_shadowSoftness", p.shadowSoftness);
        uniform1f("u_causticIntensity", p.causticIntensity);
        uniform1f("u_transmittance", p.transmittance);
        uniform2f("u_backdropSampleScale", p.backdropScaleX, p.backdropScaleY);
        uniform1f("u_parallaxScale", p.parallaxScale);
        uniform1f("u_pressProgress", p.pressProgress);
        uniform1f("u_backdropPinch", p.backdropPinch);
        uniform2f("u_glowCenter", p.glowCenterX, p.glowCenterY);
        uniform1f("u_glowStrength", p.glowStrength);
        uniform1i("u_showNormals", p.showNormals ? 1 : 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sourceTexture);
        GLES20.glUniform1i(requireUniform(glassProgram, "u_backgroundTexture"), 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTextureV);
        GLES20.glUniform1i(requireUniform(glassProgram, "u_blurredTexture"), 1);
        GLES20.glUniform1i(requireUniform(glassProgram, "u_useBlurredTexture"), 1);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
        GLES20.glDisableVertexAttribArray(position);
    }

    private void bindInterleavedQuad(int program) {
        int position = requireAttrib(program, "aPosition");
        int uv = requireAttrib(program, "aUv");
        fullQuad.position(0);
        GLES20.glEnableVertexAttribArray(position);
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false,
                4 * Float.BYTES, fullQuad);
        fullQuad.position(2);
        GLES20.glEnableVertexAttribArray(uv);
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false,
                4 * Float.BYTES, fullQuad);
    }

    private void unbindInterleavedQuad(int program) {
        int position = GLES20.glGetAttribLocation(program, "aPosition");
        int uv = GLES20.glGetAttribLocation(program, "aUv");
        if (position >= 0) GLES20.glDisableVertexAttribArray(position);
        if (uv >= 0) GLES20.glDisableVertexAttribArray(uv);
    }

    private int createTexture(int w, int h) {
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        return ids[0];
    }

    private int createFramebuffer(int texture) {
        int[] ids = new int[1];
        GLES20.glGenFramebuffers(1, ids, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, ids[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, texture, 0);
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Prismal framebuffer incomplete=0x"
                    + Integer.toHexString(status));
        }
        return ids[0];
    }

    private int createProgram(String vertexSource, String fragmentSource) {
        int vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertex);
        GLES20.glAttachShader(program, fragment);
        GLES20.glLinkProgram(program);
        int[] linked = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
        GLES20.glDeleteShader(vertex);
        GLES20.glDeleteShader(fragment);
        if (linked[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            throw new IllegalStateException("Prismal program link failed: " + log);
        }
        return program;
    }

    private int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException("Prismal shader compile failed: " + log);
        }
        return shader;
    }

    private String readRaw(int id) {
        try (InputStream input = resources.openRawResource(id);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to read Prismal shader", error);
        }
    }

    private void uniform1f(String name, float value) {
        GLES20.glUniform1f(requireUniform(glassProgram, name), value);
    }
    private void uniform1i(String name, int value) {
        GLES20.glUniform1i(requireUniform(glassProgram, name), value);
    }
    private void uniform2f(String name, float x, float y) {
        GLES20.glUniform2f(requireUniform(glassProgram, name), x, y);
    }
    private void uniform4f(String name, float x, float y, float z, float w) {
        GLES20.glUniform4f(requireUniform(glassProgram, name), x, y, z, w);
    }

    private static int requireUniform(int program, String name) {
        int location = GLES20.glGetUniformLocation(program, name);
        if (location < 0) throw new IllegalStateException("missing Prismal uniform " + name);
        return location;
    }
    private static int requireAttrib(int program, String name) {
        int location = GLES20.glGetAttribLocation(program, name);
        if (location < 0) throw new IllegalStateException("missing Prismal attribute " + name);
        return location;
    }
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
    private static FloatBuffer floatBuffer(float[] values) {
        FloatBuffer buffer = ByteBuffer.allocateDirect(values.length * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        buffer.put(values).position(0);
        return buffer;
    }

    private void releaseTargets() {
        if (sourceFramebuffer != 0) GLES20.glDeleteFramebuffers(1, new int[]{sourceFramebuffer}, 0);
        if (blurFramebufferH != 0) GLES20.glDeleteFramebuffers(1, new int[]{blurFramebufferH}, 0);
        if (blurFramebufferV != 0) GLES20.glDeleteFramebuffers(1, new int[]{blurFramebufferV}, 0);
        if (outputFramebuffer != 0) GLES20.glDeleteFramebuffers(1, new int[]{outputFramebuffer}, 0);
        if (sourceTexture != 0) GLES20.glDeleteTextures(1, new int[]{sourceTexture}, 0);
        if (blurTextureH != 0) GLES20.glDeleteTextures(1, new int[]{blurTextureH}, 0);
        if (blurTextureV != 0) GLES20.glDeleteTextures(1, new int[]{blurTextureV}, 0);
        if (outputTexture != 0) GLES20.glDeleteTextures(1, new int[]{outputTexture}, 0);
        sourceFramebuffer = blurFramebufferH = blurFramebufferV = outputFramebuffer = 0;
        sourceTexture = blurTextureH = blurTextureV = outputTexture = 0;
        width = height = blurWidth = blurHeight = 0;
    }

    @Override
    public void close() {
        releaseTargets();
        if (sourceProgram != 0) GLES20.glDeleteProgram(sourceProgram);
        if (blurHProgram != 0) GLES20.glDeleteProgram(blurHProgram);
        if (blurVProgram != 0) GLES20.glDeleteProgram(blurVProgram);
        if (glassProgram != 0) GLES20.glDeleteProgram(glassProgram);
        sourceProgram = blurHProgram = blurVProgram = glassProgram = 0;
    }
}
