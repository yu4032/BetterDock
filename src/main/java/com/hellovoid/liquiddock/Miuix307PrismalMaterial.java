package com.hellovoid.liquiddock;

import android.opengl.GLES20;

/**
 * Current styropyr0/Prismal liquid-glass model adapted to the HyperOS PassBlur external-OES
 * backdrop. Prismal generates Dock-local optical UVs; every backdrop sample then passes through
 * the already device-validated Stage-B mapping (host rect -> config rotation -> SurfaceTexture
 * X-crop precompensation -> SurfaceTexture transform). No CPU capture/readback or texture upload.
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
            uniform float uRefractionInset;
            uniform float uSminSmoothing;
            uniform float uEdgeRefractionFalloff;
            uniform float uIor;
            uniform float uThicknessPx;
            uniform float uNormalStrength;
            uniform float uDisplacementScale;
            uniform float uHeightTransitionWidth;
            uniform float uLensRefractionPx;
            uniform float uLensDepthEffect;
            uniform float uChromaticAberration;
            uniform float uDispersionR;
            uniform float uDispersionB;
            uniform float uVibrancy;
            uniform float uPlainHighlight;
            uniform float uLiquidDome;
            uniform float uFresnelReflect;
            uniform float uBrightness;
            uniform vec4 uGlassColor;
            uniform float uHighlightWidth;
            uniform vec2 uLightDir;
            uniform float uSpecularStrength;
            uniform float uSpecularSharp;
            uniform float uRimLight;
            uniform vec4 uShadowColor;
            uniform float uShadowSoftness;
            uniform float uCausticIntensity;
            uniform float uTransmittance;
            uniform vec2 uBackdropScale;
            uniform float uParallaxScale;
            uniform float uPressProgress;
            uniform float uBackdropPinch;
            uniform vec2 uGlowCenter;
            uniform float uGlowStrength;
            uniform int uShowNormals;
            uniform float uBlurRadiusPx;
            uniform float uHighlightAlpha;
            uniform float uEdgeBand;

            varying vec2 vUv;

            float radiusAtCentered(vec2 c, vec4 radii) {
                if (c.x >= 0.0) return c.y <= 0.0 ? radii.y : radii.z;
                return c.y <= 0.0 ? radii.x : radii.w;
            }

            float sdRoundedRectRealistic(vec2 coord, vec2 halfSize, float radius) {
                vec2 cornerCoord = abs(coord) - (halfSize - vec2(radius));
                float outside = length(max(cornerCoord, 0.0)) - radius;
                float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
                return outside + inside;
            }

            vec2 gradSdRoundedRectRealistic(vec2 coord, vec2 halfSize, float radius) {
                vec2 cornerCoord = abs(coord) - (halfSize - vec2(radius));
                if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
                    vec2 m = max(cornerCoord, 0.0);
                    float len = length(m);
                    if (len < 0.00001) return vec2(0.0);
                    return sign(coord) * (m / len);
                }
                float gradX = step(cornerCoord.y, cornerCoord.x);
                return sign(coord) * vec2(gradX, 1.0 - gradX);
            }

            float circleMapRealistic(float x) {
                x = clamp(x, 0.0, 1.0);
                return 1.0 - sqrt(max(0.0, 1.0 - x * x));
            }

            float smin_poly(float a, float b, float k) {
                if (k <= 0.0) return min(a, b);
                float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
                return mix(b, a, h) - k * h * (1.0 - h);
            }

            float smax_poly(float a, float b, float k) {
                if (k <= 0.0) return max(a, b);
                float h = clamp(0.5 + 0.5 * (a - b) / k, 0.0, 1.0);
                return mix(b, a, h) + k * h * (1.0 - h);
            }

            float sdRoundBox(vec2 p, vec2 b, float r, float k) {
                if (k <= 0.0) {
                    vec2 q = abs(p) - b + r;
                    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
                }
                vec2 q = abs(p) - b + r;
                float a = smax_poly(q.x, q.y, k);
                float c = smin_poly(a, 0.0, k * 0.5);
                vec2 ql = vec2(smax_poly(q.x, 0.0, k), smax_poly(q.y, 0.0, k));
                return c + length(ql) - r;
            }

            float getHeightFromDist(float dist, float tw) {
                float t = clamp(-dist / tw, 0.0, 1.0);
                return sqrt(max(0.0, 2.0 * t - t * t));
            }

            vec2 computeGradientHeight(vec2 pPx, vec2 halfSz, float cr, float k, float tw) {
                float hpx = getHeightFromDist(sdRoundBox(pPx + vec2(1.0, 0.0), halfSz, cr, k), tw);
                float hnx = getHeightFromDist(sdRoundBox(pPx - vec2(1.0, 0.0), halfSz, cr, k), tw);
                float hpy = getHeightFromDist(sdRoundBox(pPx + vec2(0.0, 1.0), halfSz, cr, k), tw);
                float hny = getHeightFromDist(sdRoundBox(pPx - vec2(0.0, 1.0), halfSz, cr, k), tw);
                return vec2((hpx - hnx) * 0.5, (hpy - hny) * 0.5);
            }

            vec3 applyVibrancy(vec3 rgb, float sat) {
                if (sat <= 1.001) return rgb;
                float luma = dot(rgb, vec3(0.213, 0.715, 0.072));
                return clamp(mix(vec3(luma), rgb, sat), 0.0, 1.0);
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

            vec3 sampleBackdropRaw(vec2 dockUv) {
                return texture2D(uTexture, mapBackdropUv(dockUv)).rgb;
            }

            // Upstream Prismal uses a half-resolution separable FBO blur. The PassBlur/OES adapter
            // keeps the path zero-readback and approximates that filtering directly in Dock-local
            // space, so all taps still use the validated compositor mapping.
            vec3 sampleBackdrop(vec2 dockUv) {
                float r = max(uBlurRadiusPx, 0.0);
                if (r < 0.5) return sampleBackdropRaw(dockUv);
                vec2 d = vec2(r) / max(uViewSize, vec2(1.0));
                vec3 color = sampleBackdropRaw(dockUv) * 0.227027;
                color += sampleBackdropRaw(dockUv + vec2(d.x * 1.384615, 0.0)) * 0.158108;
                color += sampleBackdropRaw(dockUv - vec2(d.x * 1.384615, 0.0)) * 0.158108;
                color += sampleBackdropRaw(dockUv + vec2(0.0, d.y * 1.384615)) * 0.158108;
                color += sampleBackdropRaw(dockUv - vec2(0.0, d.y * 1.384615)) * 0.158108;
                color += sampleBackdropRaw(dockUv + vec2(d.x * 3.230769, d.y * 3.230769)) * 0.035635;
                color += sampleBackdropRaw(dockUv - vec2(d.x * 3.230769, d.y * 3.230769)) * 0.035635;
                color += sampleBackdropRaw(dockUv + vec2(d.x * 3.230769, -d.y * 3.230769)) * 0.033640;
                color += sampleBackdropRaw(dockUv + vec2(-d.x * 3.230769, d.y * 3.230769)) * 0.033640;
                return color;
            }

            vec2 backdropUv(vec2 screenUv, vec2 offset, float pinchMix) {
                float press = clamp(uPressProgress, 0.0, 1.0);
                float pinch = mix(1.0, max(uBackdropPinch, 0.01), press * pinchMix);
                vec2 s = max(uBackdropScale, vec2(0.01)) / vec2(pinch);
                vec2 scaled = (screenUv - 0.5) / s + 0.5;
                return clamp(scaled + offset, vec2(0.0), vec2(1.0));
            }

            void main() {
                vec2 halfSz = uViewSize * 0.5;
                float minDim = min(halfSz.x, halfSz.y);
                float pxNorm = clamp(minDim / 108.0, 0.36, 1.0)
                        + smoothstep(88.0, 220.0, minDim) * 0.45;
                float edgePunch = mix(1.0, 1.12, smoothstep(74.0, 200.0, minDim));
                float smallGlass = smoothstep(128.0, 46.0, minDim * 2.0);
                edgePunch = mix(edgePunch, 1.0, smallGlass * 0.85);
                float rimScale = 1.0;
                vec4 cornerRadii = vec4(uCornerRadiusPx);
                float crMask = min(uCornerRadiusPx, min(uViewSize.x, uViewSize.y) * 0.5);

                vec2 vShapeCoord = vUv - vec2(0.5);
                vec2 vScreenTexCoord = vUv;
                vec2 pPx = vShapeCoord * uViewSize;
                vec2 cKy = vec2(pPx.x, -pPx.y);

                float crMax = min(halfSz.x, halfSz.y);
                float radCorner = min(radiusAtCentered(cKy, cornerRadii), crMax);
                float sdKy = sdRoundedRectRealistic(cKy, halfSz, radCorner);
                float distMask = sdRoundBox(pPx, halfSz, crMask, uSminSmoothing);
                float edgeDist = -distMask;
                float reflShell = smoothstep(clamp(minDim * 0.09, 1.8, 18.0), 0.0, edgeDist)
                        * smoothstep(-3.0, 0.0, distMask);
                reflShell *= mix(0.78, 0.42, smallGlass);
                float inset = min(max(uRefractionInset, 0.8), max(minDim * 0.06, 1.6));
                inset = mix(inset, min(inset, minDim * 0.04), smallGlass);
                float opacity = 1.0 - smoothstep(-inset * 0.5, 0.0, distMask);
                opacity = mix(opacity, 1.0, smoothstep(0.0, 0.55, edgeDist));
                if (opacity < 0.001) discard;

                float dome = clamp(uLiquidDome, 0.0, 2.0);
                float refractionHeight = max(uHeightTransitionWidth * (1.0 + 0.55 * dome), 1.0);
                refractionHeight = min(refractionHeight, minDim * 0.98);
                float tw = max(uHeightTransitionWidth * (1.0 + 0.38 * dome), 1.0);
                tw = min(tw, minDim * 0.98);
                float hSig = getHeightFromDist(distMask, tw);
                vec2 gradHSig = computeGradientHeight(pPx, halfSz, crMask, uSminSmoothing, tw);
                float gradRadius = min(radCorner * 1.5, min(halfSz.x, halfSz.y));
                vec2 gradLens = gradSdRoundedRectRealistic(cKy, halfSz, gradRadius);

                float innerReach = max(min(halfSz.x, halfSz.y) - crMask * 0.42, minDim * 0.22);
                innerReach += refractionHeight * (1.0 + 0.25 * dome);
                innerReach = min(innerReach, max(halfSz.x, halfSz.y) * 0.95);
                float tDeep = clamp(edgeDist / max(innerReach, 2.0), 0.0, 1.0);
                float tShell = 1.0 - tDeep;
                float meniscusBand = smoothstep(0.0, 0.12, tShell);
                float hCap = pow(tShell, 0.38);
                float edgeBulge = 0.10 * pow(tShell, 2.8);
                float hDome = (hCap + edgeBulge) * meniscusBand;
                float coreBlend = smoothstep(0.0, 0.38, tDeep);
                float hSlab = mix(hSig * (0.58 + 0.42 * coreBlend), hSig,
                        0.4 + 0.6 * (1.0 - dome));
                float domeW = dome * (0.74 + 0.26 * smoothstep(0.12, 0.94, tShell));
                float height = mix(hSlab, hDome, domeW);
                float edgeRound = 1.0 - smoothstep(0.72, 1.0, tShell);
                height = clamp(height * (0.84 + 0.16 * meniscusBand + 0.08 * edgeRound), 0.0, 1.0);

                vec2 outward = length(gradLens) > 0.0001 ? normalize(gradLens) : vec2(0.0, 1.0);
                float shellCurv = smoothstep(0.0, 1.0, tShell);
                vec2 gCap = outward * (-shellCurv * (0.38 / max(minDim, 8.0)));
                gCap *= meniscusBand * edgeRound;
                vec2 gradH = mix(gradHSig, gCap, domeW);
                vec3 N = normalize(vec3(-gradH.x * uNormalStrength, -gradH.y * uNormalStrength, 1.0));

                float menW = clamp(edgeDist / tw, 0.0, 1.0);
                float menCirc = sqrt(max(0.0, 1.0 - menW * menW));
                vec3 N_meniscus = normalize(vec3(-outward * menCirc * 0.95, 0.26 + 0.74 * menW));
                float menBlend = smoothstep(tw * 0.42, 0.0, edgeDist)
                        * smoothstep(-4.0, 0.0, distMask) * 0.62;
                menBlend *= mix(1.0, 0.15, smallGlass);
                N = normalize(mix(N, N_meniscus, menBlend));

                float edgeFall = max(uEdgeRefractionFalloff, 0.05);
                float dropLens = pow(smoothstep(refractionHeight, 0.0, edgeDist),
                        clamp(1.64 / edgeFall, 0.28, 2.4));

                if (uShowNormals == 1) {
                    gl_FragColor = vec4(N * 0.5 + 0.5, opacity);
                    return;
                }

                vec3 V = vec3(0.0, 0.0, 1.0);
                float cosVN = clamp(dot(N, V), 0.0, 1.0);
                float r0 = pow((1.0 - uIor) / (1.0 + uIor), 2.0);
                float silW = clamp(minDim * 0.12, 2.5, 34.0);
                float edgeSil = smoothstep(silW, 0.0, edgeDist) * smoothstep(-4.5, 0.0, distMask);
                float tiltW = clamp(length(N.xy) * 2.4, 0.0, 1.0);
                float grazingW = clamp(edgeSil * 0.94 + tiltW * 0.55, 0.0, 1.0);
                float cosVNeff = mix(cosVN, max(0.04, cosVN * 0.22 + 0.07 * tiltW), grazingW);
                float F = r0 + (1.0 - r0) * pow(1.0 - cosVNeff, 5.0);
                float fresCtl = clamp(uFresnelReflect, 0.0, 5.0);
                float cosVNrim = cosVNeff;

                vec2 cenSafe = cKy + vec2(0.0001);
                vec2 lensDir = gradLens + uLensDepthEffect * normalize(cenSafe);
                float lensLen = length(lensDir);
                lensDir = lensLen > 0.00001 ? lensDir / lensLen : vec2(0.0);
                float sdIn = min(sdKy, 0.0);
                float dLens = 0.0;
                float lensPx = clamp(uLensRefractionPx, 0.0, minDim * 0.85);
                if ((-sdKy) < refractionHeight) {
                    dLens = circleMapRealistic(1.0 - (-sdIn / refractionHeight)) * (-lensPx);
                    dLens *= 1.0 + clamp(uPressProgress, 0.0, 1.0) * 0.45;
                }

                vec2 lensDeltaUv = (dLens * lensDir) / uViewSize;
                float parallaxK = 0.052 * uDisplacementScale;
                vec2 parallax = (gradLens * height * (7.0 + 22.0 * F)) / uViewSize
                        * parallaxK * uParallaxScale;
                lensDeltaUv += parallax;
                lensDeltaUv *= mix(0.78, 1.12, (1.0 - F) * (0.42 + 0.58 * height));

                float refrStr = height * (0.5 + F * 0.35);
                vec3 refIn = refract(-V, N, 1.0 / uIor);
                vec3 refOut = dot(refIn, refIn) < 0.001 ? vec3(0.0) : refract(refIn, -N, uIor);
                vec2 snellOff = (refOut.xy * uThicknessPx * refrStr / uViewSize) * uDisplacementScale;
                snellOff *= mix(0.72, 1.18, (1.0 - F) * (0.5 + 0.5 * height));

                vec2 bDir = length(pPx) > 0.001 ? -normalize(pPx) : vec2(0.0, -1.0);
                float bulge = smoothstep(0.05, 0.38, tDeep) * (1.0 - smoothstep(0.52, 0.94, tDeep));
                bulge = pow(max(bulge, 0.0), 0.62) * height * (0.014 + 0.01 * dome);
                bulge *= smoothstep(0.02, 0.36, tDeep) * dropLens;
                vec2 bulgeUv = bDir * bulge * uViewSize / uViewSize;
                snellOff *= pxNorm * dropLens;
                bulgeUv *= pxNorm;

                vec2 baseOffset = lensDeltaUv + snellOff + bulgeUv;
                float pinchMix = 1.0 - smoothstep(0.0, 0.72, tDeep);
                vec2 uvCenter = backdropUv(vScreenTexCoord, baseOffset, pinchMix);
                float avgDim = (uViewSize.x + uViewSize.y) * 0.5;
                float caAmt = max(uChromaticAberration, 0.0);
                vec3 color;
                if (caAmt < 0.02) {
                    color = sampleBackdrop(uvCenter);
                } else {
                    float chromaFar = avgDim * 0.5;
                    float edgeFac = pow(smoothstep(chromaFar, 0.0, edgeDist), 1.8);
                    float chromaBase = caAmt * 0.0018 * edgeFac;
                    vec2 dispDir = length(pPx) > 0.001 ? normalize(pPx) : vec2(0.0, 1.0);
                    vec2 chromaPush = dispDir * chromaBase * pxNorm;
                    vec2 uvR = backdropUv(vScreenTexCoord, baseOffset + chromaPush * uDispersionR, pinchMix);
                    vec2 uvB = backdropUv(vScreenTexCoord, baseOffset - chromaPush * uDispersionB, pinchMix);
                    color = vec3(sampleBackdrop(uvR).r, sampleBackdrop(uvCenter).g, sampleBackdrop(uvB).b);
                }
                color = applyVibrancy(color, uVibrancy);

                vec2 gDir = normalize(gradLens + vec2(0.0001));
                float edgeG = reflShell * pow(1.0 - cosVNrim, 1.35) * mix(0.07, 0.62, F);
                edgeG *= mix(0.72, 0.38, smallGlass);
                float reflW = min(0.9, edgeG * (0.1 + fresCtl * 0.46) * (0.28 + 0.72 * height));
                vec2 reflUv = clamp(vScreenTexCoord + baseOffset
                        + gDir * (4.0 + 38.0 * pow(1.0 - cosVNrim, 1.25) + length(N.xy) * 14.0)
                        / uViewSize * pxNorm, vec2(0.0), vec2(1.0));
                color = mix(color, sampleBackdrop(reflUv), reflW);

                vec3 skyHaze = vec3(0.88, 0.93, 1.02);
                float skyW = min(0.62, edgeG * pow(1.0 - cosVNrim, 1.2)
                        * (0.04 + fresCtl * 0.28) * (0.28 + 0.72 * height));
                skyW *= mix(0.68, 0.32, smallGlass);
                color = mix(color, mix(color, skyHaze, 0.55 + 0.1 * fresCtl), skyW);
                color *= uBrightness;
                color = mix(color, color * uGlassColor.rgb, uGlassColor.a);

                vec3 Lp = normalize(vec3(uLightDir, 1.45));
                vec3 Ls = normalize(vec3(-uLightDir.x * 0.62 + 0.41,
                        -uLightDir.y * 0.62 + 0.33, 0.74));
                vec3 Hp = normalize(Lp + V);
                vec3 Hs = normalize(Ls + V);
                float shadowExt = mix(0.15, 0.60, uShadowSoftness > 1.0
                        ? clamp(uShadowSoftness / 20.0, 0.0, 1.0)
                        : clamp(uShadowSoftness, 0.0, 1.0));
                float shadowFalloff = avgDim * shadowExt;
                float innerShadow = 1.0 - smoothstep(0.0, shadowFalloff, edgeDist);
                innerShadow = pow(innerShadow, 2.35) * 0.62 * (0.22 + height * 0.68);
                innerShadow *= 1.0 - smoothstep(0.0, clamp(minDim * 0.05, 0.6, 3.5), edgeDist) * 0.55;
                color = mix(color, uShadowColor.rgb * 0.25, innerShadow * uShadowColor.a);

                float sh = max(uSpecularSharp, 1.0);
                float sp = uSpecularStrength * 1.05;
                float specP = pow(max(dot(N, Hp), 0.0), sh) * sp;
                specP *= 0.32 + 0.68 * height;
                float specS = pow(max(dot(N, Hs), 0.0), sh * 0.68) * sp * 0.48;
                specS *= (0.24 + 0.76 * height) * (0.42 + 0.58 * F);
                color += (specP + specS) * vec3(0.99, 0.993, 1.0) * uHighlightAlpha;

                vec3 Vn = normalize(V);
                float dotNV = clamp(dot(N, Vn), 0.0, 1.0);
                float Fnv = pow(1.0 - dotNV, 2.9);
                float FedgeRim = pow(1.0 - cosVNrim, 3.25);
                float rimBandTight = mix(0.82, 0.52, smallGlass);
                float edgeBandCompat = clamp(uEdgeBand / 0.032, 0.2, 3.0);
                float bandFracR = mix(0.022, 0.042, smoothstep(62.0, 218.0, minDim))
                        * max(0.1, uHighlightWidth) * edgeBandCompat;
                float bandR = clamp(minDim * bandFracR * rimBandTight,
                        mix(0.28, 0.65, 1.0 - smallGlass), min(12.0, minDim * 0.1));
                float shellRim = smoothstep(bandR, bandR * 0.06, edgeDist)
                        * smoothstep(-2.2, 0.0, distMask);
                float centerQuiet = smoothstep(minDim * 0.18, minDim * 0.62, edgeDist);
                float depthFade = mix(1.0, 0.62, centerQuiet);

                vec2 cn = cKy / max(halfSz, vec2(1.0));
                vec2 Lxy = normalize(uLightDir + vec2(0.00001));
                vec2 gN = normalize(gradLens + vec2(0.0001));
                float edgeLight = dot(gN, Lxy);
                float tl = max(0.0, min(-cn.x, -cn.y));
                float trc = max(0.0, min(cn.x, -cn.y));
                float br = max(0.0, min(cn.x, cn.y));
                float bl = max(0.0, min(-cn.x, cn.y));
                float lightDiag = smoothstep(-0.3, 0.3, Lxy.x + Lxy.y * 0.46);
                float pairOpp = pow(clamp(mix(tl + br, trc + bl, lightDiag), 0.0, 1.0), 1.06);
                float runAlong = smoothstep(0.14, 0.98, max(abs(cn.x), abs(cn.y)));
                float sx = exp(-abs(cn.y) * (2.25 + 1.85 * pairOpp));
                float sy = exp(-abs(cn.x) * (2.25 + 1.85 * pairOpp));
                float streakOpp = pairOpp * runAlong * max(sx, sy);

                vec3 hiSoft = vec3(0.98, 0.992, 1.008);
                vec3 hiVeil = vec3(0.958, 0.978, 1.012);
                vec3 oppTint = vec3(0.952, 0.968, 1.018);
                float schlickW = F;
                float litHairline = pow(max(edgeLight, 0.0), 3.6) * shellRim;
                float oppGlow = pow(max(-edgeLight, 0.0), 1.05) * shellRim
                        * (0.28 + 0.72 * FedgeRim * schlickW);
                float rimLitSide = litHairline * uRimLight * mix(0.92, 1.18, smallGlass)
                        * (0.58 + 0.42 * height) * depthFade;
                float rimOpposite = oppGlow * uRimLight * mix(0.34, 0.48, smallGlass)
                        * (0.4 + 0.6 * height) * depthFade;
                color += hiSoft * rimLitSide * rimScale * uHighlightAlpha;
                color += mix(hiVeil, oppTint, 0.42) * rimOpposite * rimScale * uHighlightAlpha;
                float rimCorner = streakOpp * shellRim * uRimLight * 0.035 * FedgeRim
                        * (0.35 + 0.65 * height);
                color += hiSoft * rimCorner * rimScale * uHighlightAlpha;
                float faceSheenSoft = smoothstep(bandR * 1.8, bandR * 0.08, edgeDist)
                        * smoothstep(-2.0, 0.0, distMask) * smoothstep(0.08, 0.82, edgeLight)
                        * Fnv * schlickW * uRimLight * 0.022;
                color += hiSoft * faceSheenSoft * (0.48 + 0.52 * height) * rimScale * uHighlightAlpha;
                float plusHL = smoothstep(bandR * 0.95, bandR * 0.05, edgeDist)
                        * uPlainHighlight * uRimLight * pow(max(edgeLight, 0.0), 2.2)
                        * (1.0 - 0.55 * centerQuiet);
                plusHL *= mix(0.42, 0.06, smallGlass);
                color += plusHL * vec3(0.99, 0.995, 1.0) * uHighlightAlpha;

                if (uCausticIntensity > 0.001) {
                    float causticDot = dot(normalize(vec3(gradH * uNormalStrength, 0.45)), Lp);
                    float caust = pow(max(causticDot, 0.0), 7.0) * uCausticIntensity * height;
                    color += caust * vec3(1.0, 0.96, 0.90) * uHighlightAlpha;
                }

                float pressGlow = clamp(uPressProgress, 0.0, 1.0) * clamp(uGlowStrength, 0.0, 1.0);
                if (pressGlow > 0.001) {
                    vec2 glowPx = uGlowCenter * uViewSize - halfSz;
                    float glowR = minDim * 1.5;
                    float spot = smoothstep(glowR, glowR * 0.5, length(pPx - glowPx));
                    color += vec3(1.0) * pressGlow * (0.08 + spot * 0.15);
                }

                gl_FragColor = vec4(clamp(color, 0.0, 1.0), opacity * uTransmittance);
            }
            """;

    static final class Params {
        final float ior, thicknessPx, normalStrength, liquidDome, lensRefractionPx,
                lensDepthEffect, chromaticAberration, highlightWidth, brightness,
                specularSharp, specularStrength, rimLight, causticIntensity,
                edgeBand, highlightAlpha, blurRadiusPx;
        final float refractionInsetPx, displacementScale, heightTransitionWidthPx,
                sminSmoothingPx, edgeRefractionFalloff, fresnelReflect,
                dispersionR, dispersionB, vibrancy, plainHighlight,
                lightDirX, lightDirY, shadowSoftness, transmittance,
                backdropScaleX, backdropScaleY, parallaxScale;
        final float tintR, tintG, tintB, tintA, shadowR, shadowG, shadowB, shadowA;
        final boolean showNormals;

        Params(float ior, float thicknessPx, float normalStrength, float liquidDome,
               float lensRefractionPx, float lensDepthEffect, float chromaticAberration,
               float highlightWidth, float brightness, float specularSharp,
               float specularStrength, float rimLight, float causticIntensity,
               float edgeBand, float highlightAlpha, float blurRadiusPx,
               float refractionInsetPx, float displacementScale, float heightTransitionWidthPx,
               float sminSmoothingPx, float edgeRefractionFalloff, float fresnelReflect,
               float dispersionR, float dispersionB, float vibrancy, float plainHighlight,
               float lightDirX, float lightDirY, float shadowSoftness, float transmittance,
               float backdropScaleX, float backdropScaleY, float parallaxScale,
               float tintR, float tintG, float tintB, float tintA,
               float shadowR, float shadowG, float shadowB, float shadowA,
               boolean showNormals) {
            this.ior = ior; this.thicknessPx = thicknessPx; this.normalStrength = normalStrength;
            this.liquidDome = liquidDome; this.lensRefractionPx = lensRefractionPx;
            this.lensDepthEffect = lensDepthEffect; this.chromaticAberration = chromaticAberration;
            this.highlightWidth = highlightWidth; this.brightness = brightness;
            this.specularSharp = specularSharp; this.specularStrength = specularStrength;
            this.rimLight = rimLight; this.causticIntensity = causticIntensity;
            this.edgeBand = edgeBand; this.highlightAlpha = highlightAlpha; this.blurRadiusPx = blurRadiusPx;
            this.refractionInsetPx = refractionInsetPx; this.displacementScale = displacementScale;
            this.heightTransitionWidthPx = heightTransitionWidthPx; this.sminSmoothingPx = sminSmoothingPx;
            this.edgeRefractionFalloff = edgeRefractionFalloff; this.fresnelReflect = fresnelReflect;
            this.dispersionR = dispersionR; this.dispersionB = dispersionB; this.vibrancy = vibrancy;
            this.plainHighlight = plainHighlight; this.lightDirX = lightDirX; this.lightDirY = lightDirY;
            this.shadowSoftness = shadowSoftness; this.transmittance = transmittance;
            this.backdropScaleX = backdropScaleX; this.backdropScaleY = backdropScaleY;
            this.parallaxScale = parallaxScale; this.tintR = tintR; this.tintG = tintG;
            this.tintB = tintB; this.tintA = tintA; this.shadowR = shadowR; this.shadowG = shadowG;
            this.shadowB = shadowB; this.shadowA = shadowA; this.showNormals = showNormals;
        }
    }

    private Miuix307PrismalMaterial() {}

    static Params defaults(float density) {
        float d = Math.max(0.1f, density);
        float height = 15f * d;
        float dome = 1f;
        float displacement = 1f;
        float lensPx = height * (1f + 0.55f * dome) * 2f * displacement;
        return new Params(1.5f, 20f * d, 1f, dome, lensPx, 0.9f,
                0f, 1f, 1f, 48f, 0.8f, 0.6f, 0.15f, 0.032f, 1f, 2.5f * d,
                5f * d, displacement, height, 2f * d, 2f, 0.79f,
                1f, 1f, 1.28f, 0.08f, 1f, 0.62f, 1f, 1f,
                1f, 1f, 1f, 1f, 1f, 0f,
                0f, 0f, 0f, 0f, false);
    }

    static Params fromConfig(LiquidDockConfig.Glass glass, float density, float blurRadiusPx) {
        if (glass == null) return defaults(density);
        float d = Math.max(0.1f, density);
        float heightPx = Math.max(1f, glass.prismalHeightTransitionWidth * d);
        float displacement = Math.max(0f, glass.prismalDisplacementScale);
        float legacyLensScale = glass.lensRefraction <= 0f ? 0f : glass.lensRefraction / 12f;
        float refractionHeight = heightPx * (1f + 0.55f * glass.dome);
        float lensPx = refractionHeight * 2f * displacement * legacyLensScale;
        float upstreamDepth = clamp(glass.normalStrength * 0.9f, 0f, 1f);
        float depthCompatScale = glass.depthEffect <= 0f ? 0f : glass.depthEffect / 0.08f;
        float lensDepth = clamp(upstreamDepth * depthCompatScale, 0f, 2f);
        return new Params(
                glass.ior, glass.thickness * d, glass.normalStrength, glass.dome,
                lensPx, lensDepth, glass.chromatic, glass.highlightWidth,
                glass.brightness, glass.specularSharp, glass.specularStrength,
                glass.rimLight, glass.caustics, glass.edgeBand, glass.highlightAlpha,
                Math.max(0f, blurRadiusPx),
                glass.prismalRefractionInset * d, glass.prismalDisplacementScale,
                glass.prismalHeightTransitionWidth * d, glass.prismalSminSmoothing * d,
                glass.prismalEdgeRefractionFalloff, glass.prismalFresnelReflect,
                glass.prismalDispersionR, glass.prismalDispersionB, glass.prismalVibrancy,
                glass.prismalPlainHighlight, glass.prismalLightDirX, glass.prismalLightDirY,
                glass.prismalShadowSoftness, glass.prismalTransmittance,
                glass.prismalBackdropScaleX, glass.prismalBackdropScaleY, glass.prismalParallaxScale,
                glass.tintR / 255f, glass.tintG / 255f, glass.tintB / 255f, glass.tintAlpha / 255f,
                glass.prismalShadowR / 255f, glass.prismalShadowG / 255f,
                glass.prismalShadowB / 255f, glass.prismalShadowAlpha / 255f,
                glass.prismalShowNormals);
    }

    static void applyUniforms(int program, Params p0, float cornerRadiusPx) {
        Params p = p0 != null ? p0 : defaults(1f);
        uniform1f(program, "uCornerRadiusPx", Math.max(0f, cornerRadiusPx));
        uniform1f(program, "uRefractionInset", Math.max(0f, p.refractionInsetPx));
        uniform1f(program, "uSminSmoothing", Math.max(0f, p.sminSmoothingPx));
        uniform1f(program, "uEdgeRefractionFalloff", Math.max(0.05f, p.edgeRefractionFalloff));
        uniform1f(program, "uIor", clamp(p.ior, 1.001f, 2.2f));
        uniform1f(program, "uThicknessPx", Math.max(0f, p.thicknessPx));
        uniform1f(program, "uNormalStrength", clamp(p.normalStrength, 0f, 5f));
        uniform1f(program, "uDisplacementScale", clamp(p.displacementScale, 0f, 4f));
        uniform1f(program, "uHeightTransitionWidth", Math.max(1f, p.heightTransitionWidthPx));
        uniform1f(program, "uLensRefractionPx", Math.max(0f, p.lensRefractionPx));
        uniform1f(program, "uLensDepthEffect", clamp(p.lensDepthEffect, 0f, 2f));
        uniform1f(program, "uChromaticAberration", Math.max(0f, p.chromaticAberration));
        uniform1f(program, "uDispersionR", clamp(p.dispersionR, 0f, 4f));
        uniform1f(program, "uDispersionB", clamp(p.dispersionB, 0f, 4f));
        uniform1f(program, "uVibrancy", clamp(p.vibrancy, 0f, 3f));
        uniform1f(program, "uPlainHighlight", clamp(p.plainHighlight, 0f, 1f));
        uniform1f(program, "uLiquidDome", clamp(p.liquidDome, 0f, 2f));
        uniform1f(program, "uFresnelReflect", clamp(p.fresnelReflect, 0f, 5f));
        uniform1f(program, "uBrightness", clamp(p.brightness, 0.5f, 2.2f));
        uniform1f(program, "uHighlightWidth", clamp(p.highlightWidth, 0.2f, 5f));
        uniform2f(program, "uLightDir", p.lightDirX, p.lightDirY);
        uniform1f(program, "uSpecularStrength", clamp(p.specularStrength, 0f, 3f));
        uniform1f(program, "uSpecularSharp", clamp(p.specularSharp, 1f, 240f));
        uniform1f(program, "uRimLight", clamp(p.rimLight, 0f, 3f));
        uniform1f(program, "uShadowSoftness", clamp(p.shadowSoftness, 0f, 4f));
        uniform1f(program, "uCausticIntensity", clamp(p.causticIntensity, 0f, 2f));
        uniform1f(program, "uTransmittance", clamp(p.transmittance, 0f, 1f));
        uniform2f(program, "uBackdropScale", clamp(p.backdropScaleX, 0.25f, 4f), clamp(p.backdropScaleY, 0.25f, 4f));
        uniform1f(program, "uParallaxScale", clamp(p.parallaxScale, 0f, 4f));
        uniform1f(program, "uPressProgress", 0f);
        uniform1f(program, "uBackdropPinch", 1f);
        uniform2f(program, "uGlowCenter", 0.5f, 0.5f);
        uniform1f(program, "uGlowStrength", 1f);
        uniform1i(program, "uShowNormals", p.showNormals ? 1 : 0);
        uniform1f(program, "uBlurRadiusPx", Math.max(0f, p.blurRadiusPx));
        uniform1f(program, "uHighlightAlpha", clamp(p.highlightAlpha, 0f, 2f));
        uniform1f(program, "uEdgeBand", clamp(p.edgeBand, 0.005f, 0.1f));
        uniform4f(program, "uGlassColor", p.tintR, p.tintG, p.tintB, p.tintA);
        uniform4f(program, "uShadowColor", p.shadowR, p.shadowG, p.shadowB, p.shadowA);
    }

    private static void uniform1f(int program, String name, float value) {
        int location = GLES20.glGetUniformLocation(program, name);
        if (location < 0) throw new IllegalStateException("missing material uniform " + name);
        GLES20.glUniform1f(location, value);
    }
    private static void uniform1i(int program, String name, int value) {
        int location = GLES20.glGetUniformLocation(program, name);
        if (location < 0) throw new IllegalStateException("missing material uniform " + name);
        GLES20.glUniform1i(location, value);
    }
    private static void uniform2f(int program, String name, float x, float y) {
        int location = GLES20.glGetUniformLocation(program, name);
        if (location < 0) throw new IllegalStateException("missing material uniform " + name);
        GLES20.glUniform2f(location, x, y);
    }
    private static void uniform4f(int program, String name, float r, float g, float b, float a) {
        int location = GLES20.glGetUniformLocation(program, name);
        if (location < 0) throw new IllegalStateException("missing material uniform " + name);
        GLES20.glUniform4f(location, clamp(r, 0f, 1f), clamp(g, 0f, 1f),
                clamp(b, 0f, 1f), clamp(a, 0f, 1f));
    }
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
