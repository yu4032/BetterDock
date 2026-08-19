package com.hellovoid.liquiddock;

import android.opengl.GLES20;

/**
 * Prismal-derived optical material adapted to the HyperOS PassBlur external-OES backdrop.
 *
 * The optical model operates exclusively in Dock-local UV space. Every final backdrop sample then
 * passes through the already device-validated Stage-B mapping: material-host rect -> config rotation
 * -> SurfaceTexture X-crop precompensation -> SurfaceTexture transform matrix -> external OES.
 * No capture, readback, CPU texture upload, or software blur exists in this material unit.
 */
final class Miuix307PrismalMaterial {
    static final String FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision highp float;

            uniform samplerExternalOES uTexture;
            uniform mat4 uTexMatrix;
            uniform vec4 uBackdropRect;
            uniform int uConfigRot;
            uniform vec2 uViewSize;

            uniform float uCornerRadiusPx;
            uniform float uIor;
            uniform float uThicknessPx;
            uniform float uNormalStrength;
            uniform float uLiquidDome;
            uniform float uLensRefractionPx;
            uniform float uChromaticAberration;
            uniform float uHighlightWidth;
            uniform float uDepthEffect;
            uniform float uBrightness;
            uniform float uSpecularSharp;
            uniform float uSpecularStrength;
            uniform float uRimLight;
            uniform float uCausticStrength;
            uniform float uEdgeBand;
            uniform float uHighlightAlpha;
            uniform vec4 uTint;

            varying vec2 vUv;

            float sdRoundRect(vec2 p, vec2 h, float r) {
                vec2 q = abs(p) - (h - vec2(r));
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
            }

            vec2 gradRoundRect(vec2 p, vec2 h, float r) {
                vec2 q = abs(p) - (h - vec2(r));
                vec2 s = sign(p);
                s.x = s.x == 0.0 ? 1.0 : s.x;
                s.y = s.y == 0.0 ? 1.0 : s.y;
                if (q.x >= 0.0 || q.y >= 0.0) {
                    vec2 m = max(q, vec2(0.0001));
                    return s * normalize(m);
                }
                float gx = step(q.y, q.x);
                return s * vec2(gx, 1.0 - gx);
            }

            float circleMap(float x) {
                x = clamp(x, 0.0, 1.0);
                return 1.0 - sqrt(max(0.0, 1.0 - x * x));
            }

            float getHeightFromDist(float dist, float tw) {
                float t = clamp(-dist / tw, 0.0, 1.0);
                return sqrt(max(0.0, 2.0 * t - t * t));
            }

            vec2 computeGradientHeight(vec2 p, vec2 halfSize, float radius, float tw) {
                float s = 1.0;
                float hpx = getHeightFromDist(sdRoundRect(p + vec2(s, 0.0), halfSize, radius), tw);
                float hnx = getHeightFromDist(sdRoundRect(p - vec2(s, 0.0), halfSize, radius), tw);
                float hpy = getHeightFromDist(sdRoundRect(p + vec2(0.0, s), halfSize, radius), tw);
                float hny = getHeightFromDist(sdRoundRect(p - vec2(0.0, s), halfSize, radius), tw);
                return vec2((hpx - hnx) * 0.5, (hpy - hny) * 0.5);
            }

            vec2 mapBackdropUv(vec2 dockUv) {
                vec2 safeDockUv = clamp(dockUv, vec2(0.0), vec2(1.0));
                vec2 rootUv = uBackdropRect.xy + safeDockUv * uBackdropRect.zw;
                vec2 orientedUv = rootUv;
                if (uConfigRot == 1) {
                    orientedUv = vec2(rootUv.y, 1.0 - rootUv.x);
                } else if (uConfigRot == 2) {
                    orientedUv = vec2(1.0 - rootUv.x, 1.0 - rootUv.y);
                } else if (uConfigRot == 3) {
                    orientedUv = vec2(1.0 - rootUv.y, rootUv.x);
                }

                vec2 textureInputUv = orientedUv;
                float textureScaleX = uTexMatrix[0][0];
                float textureOffsetX = uTexMatrix[3][0];
                if (abs(textureScaleX) > 0.000001) {
                    textureInputUv.x = (orientedUv.x - textureOffsetX) / textureScaleX;
                }
                vec4 transformed = uTexMatrix * vec4(textureInputUv, 0.0, 1.0);
                return clamp(transformed.xy, vec2(0.0), vec2(1.0));
            }

            vec3 sampleBackdrop(vec2 dockUv) {
                return texture2D(uTexture, mapBackdropUv(dockUv)).rgb;
            }

            void main() {
                vec2 halfSize = max(uViewSize * 0.5 - vec2(0.5), vec2(1.0));
                float minDim = min(halfSize.x, halfSize.y);
                float radius = clamp(uCornerRadiusPx, 0.0, minDim);
                vec2 pPx = vUv * uViewSize - uViewSize * 0.5;
                vec2 cKy = vec2(pPx.x, -pPx.y);

                float sd = sdRoundRect(pPx, halfSize, radius);
                float edgeDist = -sd;
                float opacity = 1.0 - smoothstep(-0.75, 0.75, sd);
                if (opacity < 0.001) discard;

                float dome = clamp(uLiquidDome, 0.0, 2.0);
                float refractionHeight = max(min(uViewSize.y * 0.48, 140.0), 1.0);
                float tw = max(refractionHeight * (1.0 + 0.38 * dome), 1.0);
                tw = min(tw, minDim * 0.98);

                float hSig = getHeightFromDist(sd, tw);
                vec2 gradHSig = computeGradientHeight(pPx, halfSize, radius, tw);
                float gradRadius = min(radius * 1.5, minDim);
                vec2 gradLens = gradRoundRect(cKy, halfSize, gradRadius);

                float innerReach = max(minDim - radius * 0.42, minDim * 0.22);
                innerReach += refractionHeight * (1.0 + 0.25 * dome);
                innerReach = min(innerReach, max(halfSize.x, halfSize.y) * 0.95);
                float tDeep = clamp(edgeDist / max(innerReach, 2.0), 0.0, 1.0);
                float tShell = 1.0 - tDeep;

                float meniscusBand = smoothstep(0.0, 0.12, tShell);
                float hCap = pow(max(tShell, 0.0), 0.38);
                float edgeBulge = 0.10 * pow(max(tShell, 0.0), 2.8);
                float hDome = (hCap + edgeBulge) * meniscusBand;
                float coreBlend = smoothstep(0.0, 0.38, tDeep);
                float hSlab = mix(hSig * (0.58 + 0.42 * coreBlend), hSig,
                        0.4 + 0.6 * (1.0 - dome));
                float domeW = dome * (0.74 + 0.26 * smoothstep(0.12, 0.94, tShell));
                float height = mix(hSlab, hDome, domeW);
                float edgeRound = 1.0 - smoothstep(0.72, 1.0, tShell);
                height = clamp(height * (0.84 + 0.16 * meniscusBand + 0.08 * edgeRound),
                        0.0, 1.0);

                vec2 outward = length(gradLens) > 0.0001
                        ? normalize(gradLens) : vec2(0.0, 1.0);
                float shellCurv = smoothstep(0.0, 1.0, tShell);
                vec2 gCap = outward * (-shellCurv * (0.38 / max(minDim, 8.0)));
                gCap *= meniscusBand * edgeRound;
                vec2 gradH = mix(gradHSig, gCap, domeW);

                vec3 N = normalize(vec3(-gradH.x * uNormalStrength,
                        -gradH.y * uNormalStrength, 1.0));
                float menW = clamp(edgeDist / max(tw, 1.0), 0.0, 1.0);
                float menCirc = sqrt(max(0.0, 1.0 - menW * menW));
                vec3 meniscusN = normalize(vec3(-outward * menCirc * 0.95,
                        0.26 + 0.74 * menW));
                float menBlend = (1.0 - smoothstep(0.0, tw * 0.42, edgeDist))
                        * (1.0 - smoothstep(-4.0, 0.0, sd)) * 0.62;
                N = normalize(mix(N, meniscusN, clamp(menBlend, 0.0, 0.62)));

                vec3 V = vec3(0.0, 0.0, 1.0);
                float cosVN = clamp(dot(N, V), 0.0, 1.0);
                float r0 = pow((1.0 - uIor) / (1.0 + uIor), 2.0);
                float silW = clamp(minDim * 0.12 * max(0.1, uHighlightWidth), 1.0, 90.0);
                float edgeSil = (1.0 - smoothstep(0.0, silW, max(edgeDist, 0.0)))
                        * (1.0 - smoothstep(-4.5, 0.0, sd));
                float tiltW = clamp(length(N.xy) * 2.4, 0.0, 1.0);
                float grazingW = clamp(edgeSil * 0.94 + tiltW * 0.55, 0.0, 1.0);
                float cosVNeff = mix(cosVN,
                        max(0.04, cosVN * 0.22 + 0.07 * tiltW), grazingW);
                float F = r0 + (1.0 - r0) * pow(1.0 - cosVNeff, 5.0);

                vec2 cenSafe = cKy + vec2(0.0001);
                vec2 lensDir = gradLens + uDepthEffect * normalize(cenSafe);
                float lensLen = length(lensDir);
                lensDir = lensLen > 0.00001 ? lensDir / lensLen : vec2(0.0);

                float sdIn = min(sd, 0.0);
                float dLens = 0.0;
                if ((-sd) < refractionHeight) {
                    dLens = circleMap(1.0 - (-sdIn / refractionHeight))
                            * (-uLensRefractionPx);
                }
                vec2 lensDeltaUv = (dLens * lensDir) / uViewSize;
                vec2 parallax = (gradLens * height * (7.0 + 22.0 * F)) / uViewSize
                        * (0.052 * 1.15);
                lensDeltaUv += parallax;
                lensDeltaUv *= mix(0.78, 1.12,
                        (1.0 - F) * (0.42 + 0.58 * height));

                float refrStr = height * (0.5 + F * 0.35);
                vec3 refIn = refract(-V, N, 1.0 / uIor);
                vec3 refOut = dot(refIn, refIn) < 0.001
                        ? vec3(0.0) : refract(refIn, -N, uIor);
                vec2 snellOff = (refOut.xy * uThicknessPx * refrStr / uViewSize) * 1.15;
                snellOff *= mix(0.72, 1.18,
                        (1.0 - F) * (0.5 + 0.5 * height));

                vec2 bDir = length(pPx) > 0.001 ? -normalize(pPx) : vec2(0.0, -1.0);
                float bulge = smoothstep(0.05, 0.38, tDeep)
                        * (1.0 - smoothstep(0.52, 0.94, tDeep));
                bulge = pow(max(bulge, 0.0), 0.62) * height * (0.014 + 0.01 * dome);
                bulge *= smoothstep(0.02, 0.36, tDeep);
                vec2 bulgeUv = bDir * bulge * halfSize / uViewSize;

                vec2 baseOffset = lensDeltaUv + snellOff + bulgeUv;
                vec2 uvG = vUv + baseOffset;
                float caAmt = max(uChromaticAberration, 0.0);
                float avgDim = (uViewSize.x + uViewSize.y) * 0.5;
                float chromaFar = avgDim * 0.5;
                float edgeFac = pow(1.0 - smoothstep(0.0, chromaFar, max(edgeDist, 0.0)), 1.8);
                float chromaBase = caAmt * 0.0018 * edgeFac;
                vec2 dispDir = length(pPx) > 0.001 ? normalize(pPx) : vec2(0.0, 1.0);
                vec2 chromaPush = dispDir * chromaBase;
                vec2 uvR = uvG + chromaPush;
                vec2 uvB = uvG - chromaPush;

                vec3 color;
                if (caAmt < 0.02) {
                    color = sampleBackdrop(uvG);
                } else {
                    color = vec3(sampleBackdrop(uvR).r,
                            sampleBackdrop(uvG).g,
                            sampleBackdrop(uvB).b);
                }

                vec2 gDir = normalize(gradLens + vec2(0.0001));
                float reflShell = (1.0 - smoothstep(0.0,
                        clamp(minDim * 0.09, 1.8, 18.0), max(edgeDist, 0.0)))
                        * (1.0 - smoothstep(-3.0, 0.0, sd));
                float reflW = min(0.34, reflShell * F * (0.18 + 0.42 * height));
                vec2 reflUv = vUv + baseOffset + gDir
                        * (4.0 + 28.0 * pow(1.0 - cosVNeff, 1.25)
                        + length(N.xy) * 12.0) / uViewSize;
                color = mix(color, sampleBackdrop(reflUv), reflW);

                color *= uBrightness;
                color = mix(color, uTint.rgb, clamp(uTint.a, 0.0, 1.0));

                vec3 light = normalize(vec3(-0.5, -0.8, 1.45));
                vec3 halfVector = normalize(light + V);
                float sh = max(uSpecularSharp, 1.0);
                float spec = pow(max(dot(N, halfVector), 0.0), sh)
                        * 1.52 * max(uSpecularStrength, 0.0)
                        * (0.32 + 0.68 * height);

                float bandFrac = max(uEdgeBand, 0.005) * max(0.1, uHighlightWidth);
                float bandR = clamp(minDim * bandFrac, 0.5, min(12.0, minDim * 0.1));
                float shellRim = (1.0 - smoothstep(bandR * 0.06, bandR,
                        max(edgeDist, 0.0))) * (1.0 - smoothstep(-2.2, 0.0, sd));
                vec2 lightXY = normalize(vec2(-0.5, -0.8) + vec2(0.00001));
                float edgeLight = dot(normalize(gradLens + vec2(0.0001)), lightXY);
                float rimLit = pow(max(edgeLight, 0.0), 3.6) * shellRim
                        * 1.22 * 0.95 * uRimLight * (0.58 + 0.42 * height);
                float rimOpposite = pow(max(-edgeLight, 0.0), 1.05) * shellRim
                        * 1.22 * 0.4 * uRimLight * (0.4 + 0.6 * height);

                float causticDot = dot(normalize(vec3(gradH * uNormalStrength, 0.45)), light);
                float caustic = pow(max(causticDot, 0.0), 7.0)
                        * max(uCausticStrength, 0.0) * height;
                vec3 highlight = spec * vec3(0.99, 0.993, 1.0)
                        + rimLit * vec3(0.98, 0.992, 1.008)
                        + rimOpposite * vec3(0.952, 0.968, 1.018)
                        + caustic * vec3(1.0, 0.96, 0.90);
                color += highlight * max(uHighlightAlpha, 0.0);

                gl_FragColor = vec4(clamp(color, 0.0, 1.0), opacity);
            }
            """;

    static final class Params {
        final float ior;
        final float thicknessPx;
        final float normalStrength;
        final float liquidDome;
        final float lensRefractionPx;
        final float chromaticAberration;
        final float highlightWidth;
        final float depthEffect;
        final float brightness;
        final float specularSharp;
        final float specularStrength;
        final float rimLight;
        final float causticStrength;
        final float edgeBand;
        final float highlightAlpha;
        final float tintR;
        final float tintG;
        final float tintB;
        final float tintA;

        Params(float ior, float thicknessPx, float normalStrength, float liquidDome,
               float lensRefractionPx, float chromaticAberration, float highlightWidth,
               float depthEffect, float brightness, float specularSharp,
               float specularStrength, float rimLight, float causticStrength,
               float edgeBand, float highlightAlpha,
               float tintR, float tintG, float tintB, float tintA) {
            this.ior = ior;
            this.thicknessPx = thicknessPx;
            this.normalStrength = normalStrength;
            this.liquidDome = liquidDome;
            this.lensRefractionPx = lensRefractionPx;
            this.chromaticAberration = chromaticAberration;
            this.highlightWidth = highlightWidth;
            this.depthEffect = depthEffect;
            this.brightness = brightness;
            this.specularSharp = specularSharp;
            this.specularStrength = specularStrength;
            this.rimLight = rimLight;
            this.causticStrength = causticStrength;
            this.edgeBand = edgeBand;
            this.highlightAlpha = highlightAlpha;
            this.tintR = tintR;
            this.tintG = tintG;
            this.tintB = tintB;
            this.tintA = tintA;
        }
    }

    private Miuix307PrismalMaterial() {}

    static Params defaults(float density) {
        float d = Math.max(0.1f, density);
        return new Params(
                1.55f, 18f * d, 1.15f, 1.0f,
                12f * d, 0f, 1f,
                0.08f, 1.08f, 88f,
                1.05f, 1f, 0.28f,
                0.032f, 1f,
                238f / 255f, 244f / 255f, 1f, 0f);
    }

    static Params fromConfig(LiquidDockConfig.Glass glass, float density) {
        if (glass == null) return defaults(density);
        float d = Math.max(0.1f, density);
        return new Params(
                glass.ior,
                glass.thickness * d,
                glass.normalStrength,
                glass.dome,
                glass.lensRefraction * d,
                glass.chromatic,
                glass.highlightWidth,
                glass.depthEffect,
                glass.brightness,
                glass.specularSharp,
                glass.specularStrength,
                glass.rimLight,
                glass.caustics,
                glass.edgeBand,
                glass.highlightAlpha,
                glass.tintR / 255f,
                glass.tintG / 255f,
                glass.tintB / 255f,
                glass.tintAlpha / 255f);
    }

    static void applyUniforms(int program, Params params, float cornerRadiusPx) {
        Params p = params != null ? params : defaults(1f);
        uniform1f(program, "uCornerRadiusPx", Math.max(0f, cornerRadiusPx));
        uniform1f(program, "uIor", clamp(p.ior, 1.001f, 2f));
        uniform1f(program, "uThicknessPx", Math.max(1f, p.thicknessPx));
        uniform1f(program, "uNormalStrength", clamp(p.normalStrength, 0f, 5f));
        uniform1f(program, "uLiquidDome", clamp(p.liquidDome, 0f, 2f));
        uniform1f(program, "uLensRefractionPx", Math.max(0f, p.lensRefractionPx));
        uniform1f(program, "uChromaticAberration", Math.max(0f, p.chromaticAberration));
        uniform1f(program, "uHighlightWidth", clamp(p.highlightWidth, 0.2f, 3f));
        uniform1f(program, "uDepthEffect", clamp(p.depthEffect, 0f, 1f));
        uniform1f(program, "uBrightness", clamp(p.brightness, 0.5f, 2f));
        uniform1f(program, "uSpecularSharp", clamp(p.specularSharp, 1f, 400f));
        uniform1f(program, "uSpecularStrength", clamp(p.specularStrength, 0f, 5f));
        uniform1f(program, "uRimLight", clamp(p.rimLight, 0f, 3f));
        uniform1f(program, "uCausticStrength", clamp(p.causticStrength, 0f, 1f));
        uniform1f(program, "uEdgeBand", clamp(p.edgeBand, 0.005f, 0.1f));
        uniform1f(program, "uHighlightAlpha", clamp(p.highlightAlpha, 0f, 2f));
        int tint = GLES20.glGetUniformLocation(program, "uTint");
        if (tint < 0) throw new IllegalStateException("missing material uniform uTint");
        GLES20.glUniform4f(tint,
                clamp(p.tintR, 0f, 1f), clamp(p.tintG, 0f, 1f),
                clamp(p.tintB, 0f, 1f), clamp(p.tintA, 0f, 1f));
    }

    private static void uniform1f(int program, String name, float value) {
        int location = GLES20.glGetUniformLocation(program, name);
        if (location < 0) throw new IllegalStateException("missing material uniform " + name);
        GLES20.glUniform1f(location, value);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
