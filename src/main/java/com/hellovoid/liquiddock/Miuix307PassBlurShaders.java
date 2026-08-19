package com.hellovoid.liquiddock;

/** Shader sources that adapt the HyperOS PassBlur external-OES producer into Prismal's 2D domain. */
final class Miuix307PassBlurShaders {
    static final String QUAD_VERTEX = """
            attribute vec2 aPosition;
            attribute vec2 aUv;
            varying vec2 vUv;
            void main() {
                vUv = aUv;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
            """;

    static final String OES_NORMALIZE_FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            precision highp float;

            uniform samplerExternalOES uTexture;
            uniform mat4 uTexMatrix;
            uniform vec4 uBackdropRect;
            uniform int uConfigRot;
            uniform vec4 uValidDockRect;
            varying vec2 vUv;

            vec2 orientRootUv(vec2 rootUv) {
                if (uConfigRot == 1) {
                    return vec2(rootUv.y, 1.0 - rootUv.x);
                } else if (uConfigRot == 2) {
                    return vec2(1.0 - rootUv.x, 1.0 - rootUv.y);
                } else if (uConfigRot == 3) {
                    return vec2(1.0 - rootUv.y, rootUv.x);
                }
                return rootUv;
            }

            float mirrorIntoValidRange(float value, float lo, float hi) {
                float span = hi - lo;
                if (span <= 0.000001) {
                    return clamp(value, 0.0, 1.0);
                }
                if (value >= lo && value <= hi) {
                    return value;
                }

                float phase = mod((value - lo) / span, 2.0);
                if (phase < 0.0) phase += 2.0;
                float mirrored = phase <= 1.0 ? phase : 2.0 - phase;
                return lo + mirrored * span;
            }

            vec2 mirrorDockUv(vec2 uv) {
                return vec2(
                        mirrorIntoValidRange(uv.x, uValidDockRect.x, uValidDockRect.z),
                        mirrorIntoValidRange(uv.y, uValidDockRect.y, uValidDockRect.w));
            }

            void main() {
                // The Floating Dock PassBlur producer can expose only part of the material while
                // the Dock is being pulled in from off-screen. Fill the off-domain sampling guard
                // with a mirrored continuation of real producer pixels instead of transparent
                // black. The final material pass still scissors PARTIAL coverage, so these guard
                // pixels are never directly presented; blur/refraction alone may sample them.
                vec2 sampleDockUv = mirrorDockUv(vUv);

                // Keep Stage-B producer mapping itself intentionally unclamped. Mirroring happens
                // in Dock-local space against the measured producer-valid interval, so unavailable
                // coordinates never collapse into one repeated SurfaceTexture edge texel.
                vec2 rootUv = uBackdropRect.xy + sampleDockUv * uBackdropRect.zw;
                vec2 orientedUv = orientRootUv(rootUv);

                // HyperOS' SurfaceTexture matrix contains an extra horizontal crop. Neutralize
                // that crop before applying the matrix so root-space calibration stays stable.
                vec2 textureInputUv = orientedUv;
                float textureScaleX = uTexMatrix[0][0];
                float textureOffsetX = uTexMatrix[3][0];
                if (abs(textureScaleX) > 0.000001) {
                    textureInputUv.x = (orientedUv.x - textureOffsetX) / textureScaleX;
                }

                vec4 transformed = uTexMatrix * vec4(textureInputUv, 0.0, 1.0);
                gl_FragColor = texture2D(uTexture, transformed.xy);
            }
            """;

    /**
     * Current upstream Prismal 31-tap Gaussian kernel, parameterized by direction so the same
     * program can execute the original horizontal and vertical passes.
     */
    static final String GAUSSIAN_BLUR_FRAGMENT = """
            precision highp float;
            uniform sampler2D uTexture;
            uniform vec2 uTexelSize;
            uniform vec2 uDirection;
            uniform float uSigma;
            varying vec2 vUv;

            void main() {
                float s = max(uSigma, 0.5);
                float s2 = s * s * 2.0;
                float norm = 0.0;
                vec3 col = vec3(0.0);

                for (float i = -15.0; i <= 15.0; i += 1.0) {
                    float w = exp(-i * i / s2);
                    vec2 delta = vec2(
                            i * uTexelSize.x * uDirection.x,
                            i * uTexelSize.y * uDirection.y);
                    vec2 uv = clamp(vUv + delta, 0.0, 1.0);
                    col += texture2D(uTexture, uv).rgb * w;
                    norm += w;
                }

                gl_FragColor = vec4(col / norm, 1.0);
            }
            """;

    private Miuix307PassBlurShaders() {}
}
