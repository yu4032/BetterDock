package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.ConfigSchema;

/** Immutable, typed runtime configuration. All defaults and unit semantics live here so
 * hooks and renderers do not need to know JSON keys. */
final class LiquidDockConfig {
    final boolean enabled;
    final boolean debugLog;
    final Grid grid;
    final Dock dock;
    final Divider divider;
    final Glass glass;
    final Workstation workstation;

    static LiquidDockConfig load() { return new LiquidDockConfig(ConfigReader.load()); }

    static LiquidDockConfig from(ConfigReader reader) { return new LiquidDockConfig(reader); }

    private LiquidDockConfig(ConfigReader c) {
        enabled = c.b(ConfigSchema.Core.ENABLED.name(),
                ConfigSchema.Core.ENABLED.runtimeFallback());
        debugLog = c.b(ConfigSchema.Debug.LOGGING.name(),
                ConfigSchema.Debug.LOGGING.runtimeFallback());
        grid = new Grid(c);
        dock = new Dock(c);
        divider = new Divider(c);
        glass = new Glass(c);
        workstation = new Workstation(c);
    }

    static final class Grid {
        final boolean enabled, widgetAdaptation, dp, offsets;
        final HomeGridProfile profile;
        final float landscapeHorizontal, landscapeTop, landscapeBottom, landscapeRowGap;
        final float portraitHorizontal, portraitTop, portraitBottom, portraitRowGap;
        final float landscapeIndicatorY, portraitIndicatorY;

        Grid(ConfigReader c) {
            enabled = c.has(ConfigSchema.Grid.ENABLED.name())
                    ? c.b(ConfigSchema.Grid.ENABLED.name(), ConfigSchema.Grid.ENABLED.runtimeFallback())
                    : c.b(ConfigSchema.Grid.LEGACY_8X4.name(),
                            ConfigSchema.Grid.LEGACY_8X4.runtimeFallback());
            profile = HomeGridProfile.fromPersisted(c.s(ConfigSchema.Grid.PROFILE.name(),
                    ConfigSchema.Grid.PROFILE.runtimeFallback()));
            widgetAdaptation = c.b(ConfigSchema.Grid.WIDGET_ADAPTATION.name(),
                    ConfigSchema.Grid.WIDGET_ADAPTATION.runtimeFallback());
            dp = c.b(ConfigSchema.Grid.MARGINS_DP.name(),
                    ConfigSchema.Grid.MARGINS_DP.runtimeFallback());
            offsets = c.b(ConfigSchema.Grid.MARGINS_OFFSET.name(),
                    ConfigSchema.Grid.MARGINS_OFFSET.runtimeFallback());
            landscapeHorizontal = c.has(ConfigSchema.Grid.LANDSCAPE_HORIZONTAL_DISTANCE.name())
                    ? c.f(ConfigSchema.Grid.LANDSCAPE_HORIZONTAL_DISTANCE.name(),
                            ConfigSchema.Grid.LANDSCAPE_HORIZONTAL_DISTANCE.runtimeFallback())
                    : (c.f(ConfigSchema.Grid.LANDSCAPE_MARGIN_LEFT.name(),
                            ConfigSchema.Grid.LANDSCAPE_MARGIN_LEFT.runtimeFallback())
                    + c.f(ConfigSchema.Grid.LANDSCAPE_MARGIN_RIGHT.name(),
                            ConfigSchema.Grid.LANDSCAPE_MARGIN_RIGHT.runtimeFallback())) / 2f;
            landscapeTop = c.f(ConfigSchema.Grid.LANDSCAPE_TOP_DISTANCE.name(),
                    c.f(ConfigSchema.Grid.LANDSCAPE_MARGIN_TOP.name(),
                            ConfigSchema.Grid.LANDSCAPE_MARGIN_TOP.runtimeFallback()));
            landscapeBottom = c.f(ConfigSchema.Grid.LANDSCAPE_BOTTOM_DISTANCE.name(),
                    c.f(ConfigSchema.Grid.LANDSCAPE_MARGIN_BOTTOM.name(),
                            ConfigSchema.Grid.LANDSCAPE_MARGIN_BOTTOM.runtimeFallback()));
            portraitHorizontal = c.has(ConfigSchema.Grid.PORTRAIT_HORIZONTAL_DISTANCE.name())
                    ? c.f(ConfigSchema.Grid.PORTRAIT_HORIZONTAL_DISTANCE.name(),
                            ConfigSchema.Grid.PORTRAIT_HORIZONTAL_DISTANCE.runtimeFallback())
                    : (c.f(ConfigSchema.Grid.PORTRAIT_MARGIN_LEFT.name(),
                            ConfigSchema.Grid.PORTRAIT_MARGIN_LEFT.runtimeFallback())
                    + c.f(ConfigSchema.Grid.PORTRAIT_MARGIN_RIGHT.name(),
                            ConfigSchema.Grid.PORTRAIT_MARGIN_RIGHT.runtimeFallback())) / 2f;
            portraitTop = c.f(ConfigSchema.Grid.PORTRAIT_TOP_DISTANCE.name(),
                    c.f(ConfigSchema.Grid.PORTRAIT_MARGIN_TOP.name(),
                            ConfigSchema.Grid.PORTRAIT_MARGIN_TOP.runtimeFallback()));
            portraitBottom = c.f(ConfigSchema.Grid.PORTRAIT_BOTTOM_DISTANCE.name(),
                    c.f(ConfigSchema.Grid.PORTRAIT_MARGIN_BOTTOM.name(),
                            ConfigSchema.Grid.PORTRAIT_MARGIN_BOTTOM.runtimeFallback()));
            landscapeRowGap = c.f(ConfigSchema.Grid.LANDSCAPE_ROW_GAP.name(),
                    ConfigSchema.Grid.LANDSCAPE_ROW_GAP.runtimeFallback());
            portraitRowGap = c.f(ConfigSchema.Grid.PORTRAIT_ROW_GAP.name(),
                    ConfigSchema.Grid.PORTRAIT_ROW_GAP.runtimeFallback());
            landscapeIndicatorY = c.f(ConfigSchema.Grid.LANDSCAPE_INDICATOR_Y.name(),
                    ConfigSchema.Grid.LANDSCAPE_INDICATOR_Y.runtimeFallback());
            portraitIndicatorY = c.f(ConfigSchema.Grid.PORTRAIT_INDICATOR_Y.name(),
                    ConfigSchema.Grid.PORTRAIT_INDICATOR_Y.runtimeFallback());
        }
    }

    static final class Dock {
        final boolean enabled, resizeAnimation, smoothResizeAnimation, dimensionsDp;
        final float widthOffset, heightOffset, spacing, bottomOffset;
        final int blurRadius;
        final boolean cornersDp, squircle, fillDiff, strokeEnabled, strokeShadow, shadowEnabled;
        final float cornerOffset, blurCornerOffset, squircleCp, squircleStrokeWidth,
                squircleStrokeOffset, strokeWidth, standardStrokeWidth;
        final int strokeR, strokeG, strokeB, strokeAlpha;
        final float strokeShadowRadius, shadowRadius, shadowSize, shadowY;
        final int strokeShadowAlpha, shadowAlpha;

        Dock(ConfigReader c) {
            enabled = c.b(ConfigSchema.Dock.ENABLED.name(),
                    ConfigSchema.Dock.ENABLED.runtimeFallback());
            resizeAnimation = c.b(ConfigSchema.Dock.RESIZE_ANIMATION.name(),
                    ConfigSchema.Dock.RESIZE_ANIMATION.runtimeFallback());
            smoothResizeAnimation = c.b(ConfigSchema.Dock.SMOOTH_RESIZE_ANIMATION.name(),
                    ConfigSchema.Dock.SMOOTH_RESIZE_ANIMATION.runtimeFallback());
            dimensionsDp = c.b(ConfigSchema.Dock.DIMENSIONS_DP.name(),
                    ConfigSchema.Dock.DIMENSIONS_DP.runtimeFallback());
            widthOffset = c.f(ConfigSchema.Dock.WIDTH_OFFSET.name(),
                    ConfigSchema.Dock.WIDTH_OFFSET.runtimeFallback());
            heightOffset = c.f(ConfigSchema.Dock.HEIGHT_OFFSET.name(),
                    ConfigSchema.Dock.HEIGHT_OFFSET.runtimeFallback());
            spacing = c.f(ConfigSchema.Dock.SPACING.name(),
                    ConfigSchema.Dock.SPACING.runtimeFallback());
            bottomOffset = c.f(ConfigSchema.Dock.BOTTOM_OFFSET.name(),
                    ConfigSchema.Dock.BOTTOM_OFFSET.runtimeFallback());
            blurRadius = c.i(ConfigSchema.Dock.BLUR_RADIUS.name(),
                    ConfigSchema.Dock.BLUR_RADIUS.runtimeFallback());
            cornersDp = c.b(ConfigSchema.Dock.CORNERS_DP.name(),
                    ConfigSchema.Dock.CORNERS_DP.runtimeFallback());
            cornerOffset = c.f(ConfigSchema.Dock.CORNER_OFFSET.name(),
                    ConfigSchema.Dock.CORNER_OFFSET.runtimeFallback());
            blurCornerOffset = c.f(ConfigSchema.Dock.BLUR_CORNER_OFFSET.name(),
                    ConfigSchema.Dock.BLUR_CORNER_OFFSET.runtimeFallback());
            squircle = c.b(ConfigSchema.Dock.SQUIRCLE.name(),
                    ConfigSchema.Dock.SQUIRCLE.runtimeFallback());
            fillDiff = c.b(ConfigSchema.Dock.FILL_DIFF.name(),
                    ConfigSchema.Dock.FILL_DIFF.runtimeFallback());
            strokeEnabled = c.b(ConfigSchema.Dock.STROKE_ENABLED.name(),
                    ConfigSchema.Dock.STROKE_ENABLED.runtimeFallback());
            squircleCp = c.i(ConfigSchema.Dock.SQUIRCLE_CONTROL_POINT.name(),
                    ConfigSchema.Dock.SQUIRCLE_CONTROL_POINT.runtimeFallback()) / 100f;
            squircleStrokeWidth = c.f(ConfigSchema.Dock.SQUIRCLE_STROKE_WIDTH.name(),
                    ConfigSchema.Dock.SQUIRCLE_STROKE_WIDTH.runtimeFallback());
            squircleStrokeOffset = c.f(ConfigSchema.Dock.SQUIRCLE_STROKE_OFFSET.name(),
                    ConfigSchema.Dock.SQUIRCLE_STROKE_OFFSET.runtimeFallback());
            strokeWidth = c.f(ConfigSchema.Dock.FILL_DIFF_STROKE_WIDTH.name(),
                    ConfigSchema.Dock.FILL_DIFF_STROKE_WIDTH.runtimeFallback());
            standardStrokeWidth = c.f(ConfigSchema.Dock.STANDARD_STROKE_WIDTH.name(),
                    ConfigSchema.Dock.STANDARD_STROKE_WIDTH.runtimeFallback());
            strokeR = channel(c.i(ConfigSchema.Dock.STROKE_RED.name(),
                    ConfigSchema.Dock.STROKE_RED.runtimeFallback()));
            strokeG = channel(c.i(ConfigSchema.Dock.STROKE_GREEN.name(),
                    ConfigSchema.Dock.STROKE_GREEN.runtimeFallback()));
            strokeB = channel(c.i(ConfigSchema.Dock.STROKE_BLUE.name(),
                    ConfigSchema.Dock.STROKE_BLUE.runtimeFallback()));
            strokeAlpha = channel(c.i(ConfigSchema.Dock.STROKE_ALPHA.name(),
                    ConfigSchema.Dock.STROKE_ALPHA.runtimeFallback()));
            strokeShadow = c.b(ConfigSchema.Dock.STROKE_SHADOW.name(),
                    ConfigSchema.Dock.STROKE_SHADOW.runtimeFallback());
            strokeShadowRadius = c.f(ConfigSchema.Dock.STROKE_SHADOW_RADIUS.name(),
                    ConfigSchema.Dock.STROKE_SHADOW_RADIUS.runtimeFallback());
            strokeShadowAlpha = channel(c.i(ConfigSchema.Dock.STROKE_SHADOW_ALPHA.name(),
                    ConfigSchema.Dock.STROKE_SHADOW_ALPHA.runtimeFallback()));
            shadowEnabled = c.b(ConfigSchema.Dock.SHADOW_ENABLED.name(),
                    ConfigSchema.Dock.SHADOW_ENABLED.runtimeFallback());
            shadowRadius = c.f(ConfigSchema.Dock.SHADOW_RADIUS.name(),
                    ConfigSchema.Dock.SHADOW_RADIUS.runtimeFallback());
            shadowSize = c.f(ConfigSchema.Dock.SHADOW_SIZE.name(),
                    ConfigSchema.Dock.SHADOW_SIZE.runtimeFallback());
            shadowAlpha = channel(c.i(ConfigSchema.Dock.SHADOW_ALPHA.name(),
                    ConfigSchema.Dock.SHADOW_ALPHA.runtimeFallback()));
            shadowY = c.f(ConfigSchema.Dock.SHADOW_Y.name(),
                    ConfigSchema.Dock.SHADOW_Y.runtimeFallback());
        }
    }

    /** Divider customization is independent from Dock geometry and unit switches. */
    static final class Divider {
        final boolean enabled, explicitMode;
        final float widthDp, heightPercent, yOffsetDp;
        final int colorR, colorG, colorB, alpha;

        Divider(ConfigReader c) {
            boolean hasLegacyConfig = c.has("dock_divider_width_dp")
                    || c.has("dock_divider_height_scale")
                    || c.has("dock_divider_y_offset")
                    || c.has("dock_divider_color_r")
                    || c.has("dock_divider_color_g")
                    || c.has("dock_divider_color_b")
                    || c.has("dock_divider_alpha");
            explicitMode = c.has("dock_divider_enabled");
            enabled = c.b("dock_divider_enabled", hasLegacyConfig);

            // Historical storage is tenths of dp. Normalize it here so the Hook only
            // sees real dp and never knows about dock_dimensions_dp. Missing fields must
            // stay zero in legacy mode because zero meant "do not override system".
            float widthDefault = explicitMode ? 10f : 0f;
            float heightDefault = explicitMode ? 60f : 0f;
            int colorDefault = explicitMode ? 255 : 0;
            int alphaDefault = explicitMode ? 128 : 0;
            widthDp = Math.max(0f, c.f("dock_divider_width_dp", widthDefault) / 10f);
            heightPercent = clamp(c.f("dock_divider_height_scale", heightDefault), 0f, 100f);
            yOffsetDp = c.f("dock_divider_y_offset", 0) / 10f;
            colorR = channel(c.i("dock_divider_color_r", colorDefault));
            colorG = channel(c.i("dock_divider_color_g", colorDefault));
            colorB = channel(c.i("dock_divider_color_b", colorDefault));
            alpha = channel(c.i("dock_divider_alpha", alphaDefault));
        }
    }

    static final class Glass {
        final boolean enabled;
        final boolean prismalShowNormals;
        final float blur, chromatic, thickness, ior, normalStrength, dome,
                lensRefraction, highlightWidth, brightness, specularStrength, rimLight, caustics;
        final float prismalRefractionInset, prismalDisplacementScale, prismalHeightTransitionWidth,
                prismalSminSmoothing, prismalEdgeRefractionFalloff, prismalFresnelReflect,
                prismalDispersionR, prismalDispersionB, prismalVibrancy, prismalPlainHighlight,
                prismalLightDirX, prismalLightDirY, prismalShadowSoftness, prismalTransmittance,
                prismalBackdropScaleX, prismalBackdropScaleY, prismalParallaxScale;
        final int captureBleedTopPx, captureBleedBottomPx,
                captureBleedLeftPx, captureBleedRightPx;
        final int tintAlpha, tintR, tintG, tintB, specularSharp,
                prismalShadowR, prismalShadowG, prismalShadowB, prismalShadowAlpha;

        Glass(ConfigReader c) {
            enabled = c.b(ConfigSchema.Glass.ENABLED.name(),
                    ConfigSchema.Glass.ENABLED.runtimeFallback());
            blur = c.f(ConfigSchema.Glass.BLUR.name(), ConfigSchema.Glass.BLUR.runtimeFallback());
            // Upstream Prismal uses the human-facing chromatic magnitude directly (for example 8).
            chromatic = c.i(ConfigSchema.Glass.CHROMATIC.name(),
                    ConfigSchema.Glass.CHROMATIC.runtimeFallback());
            captureBleedTopPx = clamp(c.i(ConfigSchema.Glass.CAPTURE_BLEED_TOP.name(),
                    ConfigSchema.Glass.CAPTURE_BLEED_TOP.runtimeFallback()), 0, 256);
            captureBleedBottomPx = clamp(c.i(ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM.name(),
                    ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM.runtimeFallback()), 0, 256);
            captureBleedLeftPx = clamp(c.i(ConfigSchema.Glass.CAPTURE_BLEED_LEFT.name(),
                    ConfigSchema.Glass.CAPTURE_BLEED_LEFT.runtimeFallback()), 0, 256);
            captureBleedRightPx = clamp(c.i(ConfigSchema.Glass.CAPTURE_BLEED_RIGHT.name(),
                    ConfigSchema.Glass.CAPTURE_BLEED_RIGHT.runtimeFallback()), 0, 256);
            tintAlpha = channel(c.i(ConfigSchema.Glass.TINT_ALPHA.name(),
                    ConfigSchema.Glass.TINT_ALPHA.runtimeFallback()));
            thickness = c.f(ConfigSchema.Glass.THICKNESS.name(),
                    ConfigSchema.Glass.THICKNESS.runtimeFallback());
            ior = c.i(ConfigSchema.Glass.IOR.name(), ConfigSchema.Glass.IOR.runtimeFallback()) / 100f;
            normalStrength = c.i(ConfigSchema.Glass.NORMAL_STRENGTH.name(),
                    ConfigSchema.Glass.NORMAL_STRENGTH.runtimeFallback()) / 100f;
            dome = c.i(ConfigSchema.Glass.DOME.name(), ConfigSchema.Glass.DOME.runtimeFallback()) / 100f;
            lensRefraction = c.f(ConfigSchema.Glass.LENS_REFRACTION.name(),
                    ConfigSchema.Glass.LENS_REFRACTION.runtimeFallback());
            highlightWidth = c.i(ConfigSchema.Glass.HIGHLIGHT_WIDTH.name(),
                    ConfigSchema.Glass.HIGHLIGHT_WIDTH.runtimeFallback()) / 100f;
            tintR = channel(c.i(ConfigSchema.Glass.TINT_RED.name(),
                    ConfigSchema.Glass.TINT_RED.runtimeFallback()));
            tintG = channel(c.i(ConfigSchema.Glass.TINT_GREEN.name(),
                    ConfigSchema.Glass.TINT_GREEN.runtimeFallback()));
            tintB = channel(c.i(ConfigSchema.Glass.TINT_BLUE.name(),
                    ConfigSchema.Glass.TINT_BLUE.runtimeFallback()));
            brightness = c.i(ConfigSchema.Glass.BRIGHTNESS.name(),
                    ConfigSchema.Glass.BRIGHTNESS.runtimeFallback()) / 100f;
            specularSharp = Math.max(1, c.i(ConfigSchema.Glass.SPECULAR_SHARPNESS.name(),
                    ConfigSchema.Glass.SPECULAR_SHARPNESS.runtimeFallback()));
            specularStrength = c.i(ConfigSchema.Glass.SPECULAR_STRENGTH.name(),
                    ConfigSchema.Glass.SPECULAR_STRENGTH.runtimeFallback()) / 100f;
            rimLight = c.i(ConfigSchema.Glass.RIM_LIGHT.name(),
                    ConfigSchema.Glass.RIM_LIGHT.runtimeFallback()) / 100f;
            caustics = c.i(ConfigSchema.Glass.CAUSTICS.name(),
                    ConfigSchema.Glass.CAUSTICS.runtimeFallback()) / 100f;

            prismalRefractionInset = c.f(ConfigSchema.Glass.PRISMAL_REFRACTION_INSET.name(),
                    ConfigSchema.Glass.PRISMAL_REFRACTION_INSET.runtimeFallback());
            prismalDisplacementScale = c.i(ConfigSchema.Glass.PRISMAL_DISPLACEMENT_SCALE.name(),
                    ConfigSchema.Glass.PRISMAL_DISPLACEMENT_SCALE.runtimeFallback()) / 100f;
            prismalHeightTransitionWidth = c.f(ConfigSchema.Glass.PRISMAL_HEIGHT_TRANSITION_WIDTH.name(),
                    ConfigSchema.Glass.PRISMAL_HEIGHT_TRANSITION_WIDTH.runtimeFallback());
            prismalSminSmoothing = c.f(ConfigSchema.Glass.PRISMAL_SMIN_SMOOTHING.name(),
                    ConfigSchema.Glass.PRISMAL_SMIN_SMOOTHING.runtimeFallback());
            prismalEdgeRefractionFalloff = c.i(ConfigSchema.Glass.PRISMAL_EDGE_REFRACTION_FALLOFF.name(),
                    ConfigSchema.Glass.PRISMAL_EDGE_REFRACTION_FALLOFF.runtimeFallback()) / 100f;
            prismalFresnelReflect = c.i(ConfigSchema.Glass.PRISMAL_FRESNEL_REFLECT.name(),
                    ConfigSchema.Glass.PRISMAL_FRESNEL_REFLECT.runtimeFallback()) / 100f;
            prismalDispersionR = c.i(ConfigSchema.Glass.PRISMAL_DISPERSION_R.name(),
                    ConfigSchema.Glass.PRISMAL_DISPERSION_R.runtimeFallback()) / 100f;
            prismalDispersionB = c.i(ConfigSchema.Glass.PRISMAL_DISPERSION_B.name(),
                    ConfigSchema.Glass.PRISMAL_DISPERSION_B.runtimeFallback()) / 100f;
            prismalVibrancy = c.i(ConfigSchema.Glass.PRISMAL_VIBRANCY.name(),
                    ConfigSchema.Glass.PRISMAL_VIBRANCY.runtimeFallback()) / 100f;
            prismalPlainHighlight = c.i(ConfigSchema.Glass.PRISMAL_PLAIN_HIGHLIGHT.name(),
                    ConfigSchema.Glass.PRISMAL_PLAIN_HIGHLIGHT.runtimeFallback()) / 100f;
            prismalLightDirX = c.i(ConfigSchema.Glass.PRISMAL_LIGHT_DIR_X.name(),
                    ConfigSchema.Glass.PRISMAL_LIGHT_DIR_X.runtimeFallback()) / 100f;
            prismalLightDirY = c.i(ConfigSchema.Glass.PRISMAL_LIGHT_DIR_Y.name(),
                    ConfigSchema.Glass.PRISMAL_LIGHT_DIR_Y.runtimeFallback()) / 100f;
            prismalShadowR = channel(c.i(ConfigSchema.Glass.PRISMAL_SHADOW_RED.name(),
                    ConfigSchema.Glass.PRISMAL_SHADOW_RED.runtimeFallback()));
            prismalShadowG = channel(c.i(ConfigSchema.Glass.PRISMAL_SHADOW_GREEN.name(),
                    ConfigSchema.Glass.PRISMAL_SHADOW_GREEN.runtimeFallback()));
            prismalShadowB = channel(c.i(ConfigSchema.Glass.PRISMAL_SHADOW_BLUE.name(),
                    ConfigSchema.Glass.PRISMAL_SHADOW_BLUE.runtimeFallback()));
            prismalShadowAlpha = channel(c.i(ConfigSchema.Glass.PRISMAL_SHADOW_ALPHA.name(),
                    ConfigSchema.Glass.PRISMAL_SHADOW_ALPHA.runtimeFallback()));
            prismalShadowSoftness = c.i(ConfigSchema.Glass.PRISMAL_SHADOW_SOFTNESS.name(),
                    ConfigSchema.Glass.PRISMAL_SHADOW_SOFTNESS.runtimeFallback()) / 100f;
            prismalTransmittance = c.i(ConfigSchema.Glass.PRISMAL_TRANSMITTANCE.name(),
                    ConfigSchema.Glass.PRISMAL_TRANSMITTANCE.runtimeFallback()) / 100f;
            prismalBackdropScaleX = c.i(ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_X.name(),
                    ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_X.runtimeFallback()) / 100f;
            prismalBackdropScaleY = c.i(ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_Y.name(),
                    ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_Y.runtimeFallback()) / 100f;
            prismalParallaxScale = c.i(ConfigSchema.Glass.PRISMAL_PARALLAX_SCALE.name(),
                    ConfigSchema.Glass.PRISMAL_PARALLAX_SCALE.runtimeFallback()) / 100f;
            prismalShowNormals = c.b(ConfigSchema.Glass.PRISMAL_SHOW_NORMALS.name(),
                    ConfigSchema.Glass.PRISMAL_SHOW_NORMALS.runtimeFallback());
        }
    }

    static final class Workstation {
        final boolean dockEnabled, dimensionsDp;
        final float dockWidthOffset, gridHorizontalOffset;
        final float allAppsLandscapeHorizontalOffset;
        final float allAppsLandscapeTopSpacing, allAppsLandscapeBottomSpacing;
        final float allAppsPortraitHorizontalOffset;
        final float allAppsPortraitTopSpacing, allAppsPortraitBottomSpacing;
        final float iconTopOffset, iconBottomOffset;

        Workstation(ConfigReader c) {
            dockEnabled = c.b(ConfigSchema.Workstation.DOCK_CUSTOMIZATION.name(),
                    ConfigSchema.Workstation.DOCK_CUSTOMIZATION.runtimeFallback());
            dimensionsDp = c.b("dock_dimensions_dp", true);
            dockWidthOffset = c.f(ConfigSchema.Workstation.DOCK_WIDTH_OFFSET.name(),
                    ConfigSchema.Workstation.DOCK_WIDTH_OFFSET.runtimeFallback());
            gridHorizontalOffset = c.f(ConfigSchema.Workstation.GRID_HORIZONTAL_OFFSET.name(),
                    ConfigSchema.Workstation.GRID_HORIZONTAL_OFFSET.runtimeFallback());
            // Compatibility chain: oldest global vertical -> old per-orientation merged
            // vertical -> new independent top/bottom. Existing users keep their layout until
            // they move either new edge control.
            float legacyAllAppsX = c.f(ConfigSchema.Workstation.LEGACY_ALL_APPS_HORIZONTAL_OFFSET.name(), 0);
            float legacyAllAppsY = c.f(ConfigSchema.Workstation.LEGACY_ALL_APPS_VERTICAL_OFFSET.name(), 0);
            float mergedLandscapeY = c.f(
                    ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_VERTICAL_OFFSET.name(), legacyAllAppsY);
            float mergedPortraitY = c.f(
                    ConfigSchema.Workstation.ALL_APPS_PORTRAIT_VERTICAL_OFFSET.name(), legacyAllAppsY);
            allAppsLandscapeHorizontalOffset = c.f(
                    ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_HORIZONTAL_OFFSET.name(), legacyAllAppsX);
            allAppsLandscapeTopSpacing = c.f(
                    ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_TOP_SPACING.name(), mergedLandscapeY);
            allAppsLandscapeBottomSpacing = c.f(
                    ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_BOTTOM_SPACING.name(), mergedLandscapeY);
            allAppsPortraitHorizontalOffset = c.f(
                    ConfigSchema.Workstation.ALL_APPS_PORTRAIT_HORIZONTAL_OFFSET.name(), legacyAllAppsX);
            allAppsPortraitTopSpacing = c.f(
                    ConfigSchema.Workstation.ALL_APPS_PORTRAIT_TOP_SPACING.name(), mergedPortraitY);
            allAppsPortraitBottomSpacing = c.f(
                    ConfigSchema.Workstation.ALL_APPS_PORTRAIT_BOTTOM_SPACING.name(), mergedPortraitY);
            iconTopOffset = c.f(ConfigSchema.Workstation.DOCK_ICON_TOP_OFFSET.name(),
                    ConfigSchema.Workstation.DOCK_ICON_TOP_OFFSET.runtimeFallback());
            iconBottomOffset = c.f(ConfigSchema.Workstation.DOCK_ICON_BOTTOM_OFFSET.name(),
                    ConfigSchema.Workstation.DOCK_ICON_BOTTOM_OFFSET.runtimeFallback());
        }
    }

    private static int channel(int value) { return clamp(value, 0, 255); }
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
