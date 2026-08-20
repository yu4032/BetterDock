package com.hellovoid.liquiddock;

/** Current upstream styropyr0/Prismal glass shader with only the full-view vertex adapter changed. */
final class Miuix307PrismalShader {
    static final String VERTEX_SHADER = """
            attribute vec2 aPosition;
            attribute vec2 aUv;
            varying vec2 v_screenTexCoord;
            varying vec2 v_shapeCoord;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                v_screenTexCoord = aUv;
                v_shapeCoord = aUv - vec2(0.5);
            }
            """;

    static final String FRAGMENT_SHADER = """
            precision highp float;

            uniform sampler2D u_backgroundTexture;
            uniform sampler2D u_blurredTexture;
            uniform int       u_useBlurredTexture;

            uniform vec2  u_resolution;
            uniform vec2  u_glassSize;
            uniform vec4  u_cornerRadii;
            uniform float u_refractionInset;
            uniform float u_sminSmoothing;
            uniform float u_edgeRefractionFalloff;

            uniform float u_ior;
            uniform float u_glassThickness;
            uniform float u_normalStrength;
            uniform float u_displacementScale;
            uniform float u_heightTransitionWidth;

            uniform float u_lensRefractionPx;
    uniform float u_lensDepthEffect;
    uniform float u_legacySCurveStrength;
    uniform float u_legacyLensRefractionPx;
    uniform float u_legacyThicknessPx;

    uniform float u_chromaticAberration;
            uniform float u_dispersionR;
            uniform float u_dispersionB;

            uniform float u_vibrancy;
            uniform float u_plainHighlight;

            uniform float u_liquidDome;
            uniform float u_fresnelReflect;

            uniform float u_brightness;
            uniform vec4  u_glassColor;
            uniform float u_highlightWidth;

            uniform vec2  u_lightDir;
            uniform float u_specular;
            uniform float u_shininess;
            uniform float u_rimStrength;

            uniform vec4  u_shadowColor;
            uniform float u_shadowSoftness;

            uniform float u_causticIntensity;
            uniform float u_transmittance;

            uniform vec2  u_backdropSampleScale;
            // Visible Dock rectangle inside the larger zero-copy overscan texture.
            uniform vec4  u_dockUvRect;
            uniform float u_parallaxScale;

            uniform float u_pressProgress;
            uniform float u_backdropPinch;
            uniform vec2  u_glowCenter;
            uniform float u_glowStrength;

            uniform int   u_showNormals;

            varying vec2 v_screenTexCoord;
            varying vec2 v_shapeCoord;

            float radiusAtCentered(vec2 c, vec4 radii) {
                if (c.x >= 0.0) {
                    if (c.y <= 0.0) return radii.y;
                    else return radii.z;
                } else {
                    if (c.y <= 0.0) return radii.x;
                    else return radii.w;
                }
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
                    if (len < 1e-5) return vec2(0.0);
                    return sign(coord) * (m / len);
                } else {
                    float gradX = step(cornerCoord.y, cornerCoord.x);
                    return sign(coord) * vec2(gradX, 1.0 - gradX);
                }
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
                vec2 s = vec2(1.0, 1.0);
                float hpx = getHeightFromDist(sdRoundBox(pPx + vec2(s.x, 0.0), halfSz, cr, k), tw);
                float hnx = getHeightFromDist(sdRoundBox(pPx - vec2(s.x, 0.0), halfSz, cr, k), tw);
                float hpy = getHeightFromDist(sdRoundBox(pPx + vec2(0.0, s.y), halfSz, cr, k), tw);
                float hny = getHeightFromDist(sdRoundBox(pPx - vec2(0.0, s.y), halfSz, cr, k), tw);
                return vec2((hpx - hnx) * 0.5, (hpy - hny) * 0.5);
            }

            vec3 applyVibrancy(vec3 rgb, float sat) {
                if (sat <= 1.001) return rgb;
                float L = dot(rgb, vec3(0.213, 0.715, 0.072));
                return clamp(mix(vec3(L), rgb, sat), 0.0, 1.0);
            }

            vec2 mapDockUvToBackdrop(vec2 dockUv) {
                return clamp(
                    u_dockUvRect.xy + dockUv * u_dockUvRect.zw,
                    vec2(0.0),
                    vec2(1.0)
                );
            }

            // Upstream Prismal computes optical displacement in a texture basis whose Y
            // axis is flipped in the vertex shader. Our normalized zero-copy FBO keeps local
            // bottom-origin UV, so convert displacement vectors here without flipping the image.
            vec2 upstreamOffsetToLocalTextureUv(vec2 offset) {
                return vec2(offset.x, -offset.y);
            }

            vec2 backdropUv(vec2 screenUv, vec2 offset, float pinchMix) {
                float press = clamp(u_pressProgress, 0.0, 1.0);
                float pinch = mix(1.0, max(u_backdropPinch, 0.01), press * pinchMix);
                vec2 s = max(u_backdropSampleScale, vec2(0.01)) / vec2(pinch);
                vec2 scaled = (screenUv - 0.5) / s + 0.5;
                vec2 dockUv = scaled + upstreamOffsetToLocalTextureUv(offset);
                return mapDockUvToBackdrop(dockUv);
            }

            void main() {
                vec2 halfSz = u_glassSize * 0.5;
                float minDim = min(halfSz.x, halfSz.y);
                float pxNorm = clamp(minDim / 108.0, 0.36, 1.0) + smoothstep(88.0, 220.0, minDim) * 0.45;
                float edgePunch = mix(1.0, 1.12, smoothstep(74.0, 200.0, minDim));
                float smallGlass = smoothstep(128.0, 46.0, minDim * 2.0);
                edgePunch = mix(edgePunch, 1.0, smallGlass * 0.85);
                float rimScale = 1.0;
                float crMask = min(min(u_cornerRadii.x, u_cornerRadii.y), min(u_cornerRadii.z, u_cornerRadii.w));
                crMask = min(crMask, min(u_glassSize.x, u_glassSize.y) * 0.5);

                vec2 pPx = v_shapeCoord * u_glassSize;
                vec2 cKy = vec2(pPx.x, -pPx.y);

                float crMax = min(halfSz.x, halfSz.y);
                float radCorner = min(radiusAtCentered(cKy, u_cornerRadii), crMax);
                float sdKy = sdRoundedRectRealistic(cKy, halfSz, radCorner);

                float distMask = sdRoundBox(pPx, halfSz, crMask, u_sminSmoothing);
                float edgeDist = -distMask;
                float reflShell = smoothstep(clamp(minDim * 0.09, 1.8, 18.0), 0.0, edgeDist) * smoothstep(-3.0, 0.0, distMask);
                reflShell *= mix(0.78, 0.42, smallGlass);
                float inset = min(max(u_refractionInset, 0.8), max(minDim * 0.06, 1.6));
                inset = mix(inset, min(inset, minDim * 0.04), smallGlass);
                float opacity = 1.0 - smoothstep(-inset * 0.5, 0.0, distMask);
                opacity = mix(opacity, 1.0, smoothstep(0.0, 0.55, edgeDist));
                if (opacity < 0.001) discard;

                float dome = clamp(u_liquidDome, 0.0, 2.0);
                float refractionHeight = max(u_heightTransitionWidth * (1.0 + 0.55 * dome), 1.0);
                refractionHeight = min(refractionHeight, minDim * 0.98);

                float tw = max(u_heightTransitionWidth * (1.0 + 0.38 * dome), 1.0);
                tw = min(tw, minDim * 0.98);
                float hSig = getHeightFromDist(distMask, tw);
                vec2 gradHSig = computeGradientHeight(pPx, halfSz, crMask, u_sminSmoothing, tw);

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
                float hSlab = mix(hSig * (0.58 + 0.42 * coreBlend), hSig, 0.4 + 0.6 * (1.0 - dome));

                float domeW = dome * (0.74 + 0.26 * smoothstep(0.12, 0.94, tShell));
                float height = mix(hSlab, hDome, domeW);
                float edgeRound = 1.0 - smoothstep(0.72, 1.0, tShell);
                height = clamp(height * (0.84 + 0.16 * meniscusBand + 0.08 * edgeRound), 0.0, 1.0);

                vec2 outward = (length(gradLens) > 1e-4) ? normalize(gradLens) : vec2(0.0, 1.0);
                float shellCurv = smoothstep(0.0, 1.0, tShell);
                vec2 gCap = outward * (-shellCurv * (0.38 / max(minDim, 8.0)));
                gCap *= meniscusBand * edgeRound;
                vec2 gradH = mix(gradHSig, gCap, domeW);

                vec3 N = normalize(vec3(-gradH.x * u_normalStrength, -gradH.y * u_normalStrength, 1.0));

                float menW = clamp(edgeDist / tw, 0.0, 1.0);
                float menCirc = sqrt(max(0.0, 1.0 - menW * menW));
                vec3 N_meniscus = normalize(vec3(-outward * menCirc * 0.95, 0.26 + 0.74 * menW));
                float menBlend = smoothstep(tw * 0.42, 0.0, edgeDist) * smoothstep(-4.0, 0.0, distMask) * 0.62;
                menBlend *= mix(1.0, 0.15, smallGlass);
                N = normalize(mix(N, N_meniscus, menBlend));

                float dropLensBase = pow(smoothstep(refractionHeight, 0.0, edgeDist), 0.82);
        float falloffPower = clamp(max(u_edgeRefractionFalloff, 0.05) * 0.205, 0.1, 4.0);
        float dropLens = pow(max(dropLensBase, 0.0), falloffPower / 0.82);

                if (u_showNormals == 1) {
                    gl_FragColor = vec4(N * 0.5 + 0.5, opacity);
                    return;
                }

                vec3 V = vec3(0.0, 0.0, 1.0);
                float cosVN = clamp(dot(N, V), 0.0, 1.0);
                float r0 = pow((1.0 - u_ior) / (1.0 + u_ior), 2.0);
                float silW = clamp(minDim * 0.12 * clamp(u_highlightWidth, 0.2, 3.0), 2.5, 34.0);
                float edgeSil = smoothstep(silW, 0.0, edgeDist) * smoothstep(-4.5, 0.0, distMask);
                float tiltW = clamp(length(N.xy) * 2.4, 0.0, 1.0);
                float grazingW = clamp(edgeSil * 0.94 + tiltW * 0.55, 0.0, 1.0);
                float cosVNeff = mix(cosVN, max(0.04, cosVN * 0.22 + 0.07 * tiltW), grazingW);
                float F = r0 + (1.0 - r0) * pow(1.0 - cosVNeff, 5.0);
                float fresCtl = clamp(u_fresnelReflect, 0.0, 5.0);
                float Fr = fresCtl;
                float cosVNrim = cosVNeff;

                vec2 cenSafe = cKy + vec2(1e-4, 1e-4);
                vec2 lensDir = gradLens + u_lensDepthEffect * normalize(cenSafe);
                float ldLen = length(lensDir);
                lensDir = ldLen > 1e-5 ? lensDir / ldLen : vec2(0.0);

                float lensRh = refractionHeight;
                float sdIn = min(sdKy, 0.0);
                float dLens = 0.0;
                if ((-sdKy) < lensRh) {
                    dLens = circleMapRealistic(1.0 - (-sdIn / lensRh)) * (-u_lensRefractionPx);
                    dLens *= (1.0 + clamp(u_pressProgress, 0.0, 1.0) * 0.45);
                }

                vec2 lensDeltaUv = (dLens * lensDir) / u_resolution;
                float parallaxK = 0.052 * u_displacementScale;
                vec2 parallax = (gradLens * height * (7.0 + 22.0 * F)) / u_resolution * parallaxK * u_parallaxScale;
                lensDeltaUv += parallax;
                lensDeltaUv *= mix(0.78, 1.12, (1.0 - F) * (0.42 + 0.58 * height));

                float refrStr = height * (0.5 + F * 0.35);
                vec3 refIn = refract(-V, N, 1.0 / u_ior);
                vec3 refOut = (dot(refIn, refIn) < 0.001) ? vec3(0.0) : refract(refIn, -N, u_ior);
                vec2 snellOff = (refOut.xy * u_glassThickness * refrStr / u_resolution) * u_displacementScale;
                snellOff *= mix(0.72, 1.18, (1.0 - F) * (0.5 + 0.5 * height));

                vec2 bDir = length(pPx) > 1e-3 ? -normalize(pPx) : vec2(0.0, -1.0);
                float bulge = smoothstep(0.05, 0.38, tDeep) * (1.0 - smoothstep(0.52, 0.94, tDeep));
                bulge = pow(max(bulge, 0.0), 0.62) * height * (0.014 + 0.01 * dome);
                bulge *= smoothstep(0.02, 0.36, tDeep) * dropLens;
                vec2 bulgeUv = bDir * bulge * u_glassSize / u_resolution;
                snellOff *= pxNorm * dropLens;
                bulgeUv *= pxNorm;

                vec2 currentOffset = lensDeltaUv + snellOff + bulgeUv;
        float legacyStrength = clamp(u_legacySCurveStrength, 0.0, 2.0);
        vec2 baseOffset = currentOffset;
        if (legacyStrength > 0.001) {
            // LiquidDock v1.2.0 used a Dock-height-derived SDF lens instead of the
            // current Prismal Quick Start dimensions. Keep its optical constants local
            // so 100% reproduces that refraction geometry without rewriting current UI
            // parameters; lighting/color remain on the modern Prismal path.
            float legacyRefractionHeight = clamp(u_glassSize.y * 0.48, 1.0, 140.0);
            float legacyTw = max(legacyRefractionHeight * 1.38, 1.0);
            legacyTw = min(legacyTw, minDim * 0.98);
            float legacyDistMask = sdRoundBox(pPx, halfSz, crMask, 0.0);
            float legacyEdgeDist = -legacyDistMask;
            float legacyHSig = getHeightFromDist(legacyDistMask, legacyTw);
            vec2 legacyGradHSig = computeGradientHeight(pPx, halfSz, crMask, 0.0, legacyTw);

            float legacyInnerReach = max(min(halfSz.x, halfSz.y) - crMask * 0.42, minDim * 0.22);
            legacyInnerReach += legacyRefractionHeight * 1.25;
            legacyInnerReach = min(legacyInnerReach, max(halfSz.x, halfSz.y) * 0.95);
            float legacyTDeep = clamp(legacyEdgeDist / max(legacyInnerReach, 2.0), 0.0, 1.0);
            float legacyTShell = 1.0 - legacyTDeep;
            float legacyMeniscusBand = smoothstep(0.0, 0.12, legacyTShell);
            float legacyHCap = pow(legacyTShell, 0.38);
            float legacyEdgeBulge = 0.10 * pow(legacyTShell, 2.8);
            float legacyHDome = (legacyHCap + legacyEdgeBulge) * legacyMeniscusBand;
            float legacyCoreBlend = smoothstep(0.0, 0.38, legacyTDeep);
            float legacyHSlab = mix(legacyHSig * (0.58 + 0.42 * legacyCoreBlend), legacyHSig, 0.4);
            float legacyDomeW = 0.74 + 0.26 * smoothstep(0.12, 0.94, legacyTShell);
            float legacyHeight = mix(legacyHSlab, legacyHDome, legacyDomeW);
            float legacyEdgeRound = 1.0 - smoothstep(0.72, 1.0, legacyTShell);
            legacyHeight = clamp(legacyHeight * (0.84 + 0.16 * legacyMeniscusBand + 0.08 * legacyEdgeRound), 0.0, 1.0);

            float legacyShellCurv = smoothstep(0.0, 1.0, legacyTShell);
            vec2 legacyGCap = outward * (-legacyShellCurv * (0.38 / max(minDim, 8.0)));
            legacyGCap *= legacyMeniscusBand * legacyEdgeRound;
            vec2 legacyGradH = mix(legacyGradHSig, legacyGCap, legacyDomeW);
            vec3 legacyN = normalize(vec3(-legacyGradH.x * 1.15, -legacyGradH.y * 1.15, 1.0));

            float legacyCosVN = clamp(dot(legacyN, V), 0.0, 1.0);
            float legacyR0 = pow((1.0 - 1.55) / (1.0 + 1.55), 2.0);
            float legacySilW = clamp(minDim * 0.12, 1.0, 90.0);
            float legacyEdgeSil = smoothstep(legacySilW, 0.0, legacyEdgeDist) * smoothstep(-4.5, 0.0, legacyDistMask);
            float legacyTiltW = clamp(length(legacyN.xy) * 2.4, 0.0, 1.0);
            float legacyGrazingW = clamp(legacyEdgeSil * 0.94 + legacyTiltW * 0.55, 0.0, 1.0);
            float legacyCosVNeff = mix(legacyCosVN, max(0.04, legacyCosVN * 0.22 + 0.07 * legacyTiltW), legacyGrazingW);
            float legacyF = legacyR0 + (1.0 - legacyR0) * pow(1.0 - legacyCosVNeff, 5.0);

            vec2 legacyLensDir = gradLens + 0.08 * normalize(cenSafe);
            float legacyLensLen = length(legacyLensDir);
            legacyLensDir = legacyLensLen > 1e-5 ? legacyLensDir / legacyLensLen : vec2(0.0);
            float legacySdIn = min(sdKy, 0.0);
            float legacyDLens = 0.0;
            if ((-sdKy) < legacyRefractionHeight) {
                legacyDLens = circleMapRealistic(1.0 - (-legacySdIn / legacyRefractionHeight)) * (-u_legacyLensRefractionPx);
            }
            vec2 legacyLensDeltaUv = (legacyDLens * legacyLensDir) / u_resolution;
            float legacyParallaxK = 0.052 * 1.15;
            vec2 legacyParallax = (gradLens * legacyHeight * (7.0 + 22.0 * legacyF)) / u_resolution * legacyParallaxK;
            legacyLensDeltaUv += legacyParallax;
            legacyLensDeltaUv *= mix(0.78, 1.12, (1.0 - legacyF) * (0.42 + 0.58 * legacyHeight));

            float legacyRefrStr = legacyHeight * (0.5 + legacyF * 0.35);
            vec3 legacyRefIn = refract(-V, legacyN, 1.0 / 1.55);
            vec3 legacyRefOut = (dot(legacyRefIn, legacyRefIn) < 0.001) ? vec3(0.0) : refract(legacyRefIn, -legacyN, 1.55);
            vec2 legacySnellOff = (legacyRefOut.xy * u_legacyThicknessPx * legacyRefrStr / u_resolution) * 1.15;
            legacySnellOff *= mix(0.72, 1.18, (1.0 - legacyF) * (0.5 + 0.5 * legacyHeight));

            vec2 legacyBDir = length(pPx) > 1e-3 ? -normalize(pPx) : vec2(0.0, -1.0);
            float legacyBulge = smoothstep(0.05, 0.38, legacyTDeep) * (1.0 - smoothstep(0.52, 0.94, legacyTDeep));
            legacyBulge = pow(max(legacyBulge, 0.0), 0.62) * legacyHeight * 0.024;
            legacyBulge *= smoothstep(0.02, 0.36, legacyTDeep);
            vec2 legacyBulgeUv = legacyBDir * legacyBulge * halfSz / u_resolution;

            vec2 legacyOffset = legacyLensDeltaUv + legacySnellOff + legacyBulgeUv;
            baseOffset = legacyStrength <= 1.0
                    ? mix(currentOffset, legacyOffset, legacyStrength)
                    : legacyOffset * legacyStrength;
        }
        float pinchMix = 1.0 - smoothstep(0.0, 0.72, tDeep);
                vec2 uvCenter = backdropUv(v_screenTexCoord, baseOffset, pinchMix);
                float avgDim = (u_glassSize.x + u_glassSize.y) * 0.5;

                float caAmt = max(u_chromaticAberration, 0.0);
                vec3 color;

                if (caAmt < 0.02) {
                    if (u_useBlurredTexture == 1) {
                        color = texture2D(u_blurredTexture, uvCenter).rgb;
                    } else {
                        color = texture2D(u_backgroundTexture, uvCenter).rgb;
                    }
                } else {
                    float chromaFar = avgDim * 0.5;
                    float edgeFac = pow(smoothstep(chromaFar, 0.0, edgeDist), 1.8);
                    float chromaBase = caAmt * 0.0018 * edgeFac;

                    vec2 dispDir = length(pPx) > 1e-3 ? normalize(pPx) : vec2(0.0, 1.0);
                    vec2 chromaPush = dispDir * chromaBase * pxNorm;
                    vec2 uvR = backdropUv(v_screenTexCoord, baseOffset + chromaPush * u_dispersionR, pinchMix);
                    vec2 uvG = uvCenter;
                    vec2 uvB = backdropUv(v_screenTexCoord, baseOffset - chromaPush * u_dispersionB, pinchMix);

                    if (u_useBlurredTexture == 1) {
                        float r = texture2D(u_blurredTexture, uvR).r;
                        float g = texture2D(u_blurredTexture, uvG).g;
                        float b = texture2D(u_blurredTexture, uvB).b;
                        color = vec3(r, g, b);
                    } else {
                        float r = texture2D(u_backgroundTexture, uvR).r;
                        float g = texture2D(u_backgroundTexture, uvG).g;
                        float b = texture2D(u_backgroundTexture, uvB).b;
                        color = vec3(r, g, b);
                    }
                }

                color = applyVibrancy(color, u_vibrancy);

                vec2 gDir = normalize(gradLens + vec2(1e-4));
                float edgeG = reflShell * pow(1.0 - cosVNrim, 1.35) * mix(0.07, 0.62, F);
                edgeG *= mix(0.72, 0.38, smallGlass);
                float reflW = min(0.9, edgeG * (0.1 + fresCtl * 0.46) * (0.28 + 0.72 * height));
                vec2 reflOffset = baseOffset
                    + gDir * (4.0 + 38.0 * pow(1.0 - cosVNrim, 1.25) + length(N.xy) * 14.0) / u_resolution * pxNorm;
                vec2 reflDockUv = v_screenTexCoord
                    + upstreamOffsetToLocalTextureUv(reflOffset);
                vec2 reflUv = mapDockUvToBackdrop(reflDockUv);
                vec3 reflSample;
                if (u_useBlurredTexture == 1) {
                    reflSample = texture2D(u_blurredTexture, reflUv).rgb;
                } else {
                    reflSample = texture2D(u_backgroundTexture, reflUv).rgb;
                }
                color = mix(color, reflSample, reflW);

                vec3 skyHaze = vec3(0.88, 0.93, 1.02);
                float skyW = min(0.62, edgeG * pow(1.0 - cosVNrim, 1.2) * (0.04 + fresCtl * 0.28) * (0.28 + 0.72 * height));
                skyW *= mix(0.68, 0.32, smallGlass);
                color = mix(color, mix(color, skyHaze, 0.55 + 0.1 * fresCtl), skyW);

                color *= u_brightness;
                color = mix(color, color * u_glassColor.rgb, u_glassColor.a);

                vec3 Lp = normalize(vec3(u_lightDir, 1.45));
                vec3 Ls = normalize(vec3(-u_lightDir.x * 0.62 + 0.41, -u_lightDir.y * 0.62 + 0.33, 0.74));
                vec3 Hp = normalize(Lp + V);
                vec3 Hs = normalize(Ls + V);

                float shadowExt = mix(0.15, 0.60, u_shadowSoftness > 1.0
                    ? clamp(u_shadowSoftness / 20.0, 0.0, 1.0)
                    : clamp(u_shadowSoftness, 0.0, 1.0));
                float shadowFalloff = avgDim * shadowExt;
                float innerShadow = 1.0 - smoothstep(0.0, shadowFalloff, edgeDist);
                innerShadow = pow(innerShadow, 2.35) * 0.62 * (0.22 + height * 0.68);
                innerShadow *= 1.0 - smoothstep(0.0, clamp(minDim * 0.05, 0.6, 3.5), edgeDist) * 0.55;
                color = mix(color, u_shadowColor.rgb * 0.25, innerShadow * u_shadowColor.a);

                float sh = max(u_shininess, 1.0);
                float sp = u_specular * 1.05;
                float specP = pow(max(dot(N, Hp), 0.0), sh) * sp;
                specP *= (0.32 + 0.68 * height);
                float specS = pow(max(dot(N, Hs), 0.0), sh * 0.68) * sp * 0.48;
                specS *= (0.24 + 0.76 * height) * (0.42 + 0.58 * F);
                color += (specP + specS) * vec3(0.99, 0.993, 1.0);

                vec3 Vn = normalize(V);
                float dotNV = clamp(dot(N, Vn), 0.0, 1.0);
                float Fnv = pow(1.0 - dotNV, 2.9);
                float FedgeRim = pow(1.0 - cosVNrim, 3.25);

                float rimBandTight = mix(0.82, 0.52, smallGlass);
                float bandFracR = mix(0.022, 0.042, smoothstep(62.0, 218.0, minDim));
                float bandR = clamp(minDim * bandFracR * rimBandTight * clamp(u_highlightWidth, 0.2, 3.0), mix(0.28, 0.65, 1.0 - smallGlass), min(18.0, minDim * 0.16));
                float shellRim = smoothstep(bandR, bandR * 0.06, edgeDist) * smoothstep(-2.2, 0.0, distMask);
                float centerQuiet = smoothstep(minDim * 0.18, minDim * 0.62, edgeDist);
                float depthFade = mix(1.0, 0.62, centerQuiet);

                vec2 cn = cKy / max(halfSz, vec2(1.0));
                vec2 Lxy = normalize(u_lightDir + vec2(1e-5));
                vec2 gN = normalize(gradLens + vec2(1e-4));
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
                float oppGlow = pow(max(-edgeLight, 0.0), 1.05) * shellRim * (0.28 + 0.72 * FedgeRim * schlickW);
                float rimLitSide = litHairline * u_rimStrength * mix(0.92, 1.18, smallGlass) * (0.58 + 0.42 * height) * depthFade;
                float rimOpposite = oppGlow * u_rimStrength * mix(0.34, 0.48, smallGlass) * (0.4 + 0.6 * height) * depthFade;
                color += hiSoft * rimLitSide * rimScale;
                color += mix(hiVeil, oppTint, 0.42) * rimOpposite * rimScale;

                float rimCorner = streakOpp * shellRim * u_rimStrength * 0.035 * FedgeRim * (0.35 + 0.65 * height);
                color += hiSoft * rimCorner * rimScale;

                float faceSheenSoft = smoothstep(bandR * 1.8, bandR * 0.08, edgeDist) * smoothstep(-2.0, 0.0, distMask)
                    * smoothstep(0.08, 0.82, edgeLight) * Fnv * schlickW * u_rimStrength * 0.022;
                color += hiSoft * faceSheenSoft * (0.48 + 0.52 * height) * rimScale;

                float plusHL = smoothstep(bandR * 0.95, bandR * 0.05, edgeDist) * u_plainHighlight * u_rimStrength
                    * pow(max(edgeLight, 0.0), 2.2) * (1.0 - 0.55 * centerQuiet);
                plusHL *= mix(0.42, 0.06, smallGlass);
                color += plusHL * vec3(0.99, 0.995, 1.0);

                if (u_causticIntensity > 0.001) {
                    float causticDot = dot(normalize(vec3(gradH * u_normalStrength, 0.45)), Lp);
                    float caust = pow(max(causticDot, 0.0), 7.0) * u_causticIntensity * height;
                    color += caust * vec3(1.0, 0.96, 0.90);
                }

                float pressGlow = clamp(u_pressProgress, 0.0, 1.0) * clamp(u_glowStrength, 0.0, 1.0);
                if (pressGlow > 0.001) {
                    vec2 glowPx = u_glowCenter * u_glassSize - halfSz;
                    float glowR = minDim * 1.5;
                    float spot = smoothstep(glowR, glowR * 0.5, length(pPx - glowPx));
                    color += vec3(1.0) * pressGlow * (0.08 + spot * 0.15);
                }

                gl_FragColor = vec4(color, opacity * u_transmittance);
            }
            """;

    private Miuix307PrismalShader() {}
}