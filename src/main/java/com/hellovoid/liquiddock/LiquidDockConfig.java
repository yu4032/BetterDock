package com.hellovoid.liquiddock;

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

    private LiquidDockConfig(ConfigReader c) {
        enabled = c.b("liquiddock_enabled", true);
        debugLog = c.b("liquiddock_debug_log", false);
        grid = new Grid(c);
        dock = new Dock(c);
        divider = new Divider(c);
        glass = new Glass(c);
        workstation = new Workstation(c);
    }

    static final class Grid {
        final boolean enabled, dp, offsets;
        final float landscapeHorizontal, landscapeTop, landscapeBottom, landscapeRowGap;
        final float portraitHorizontal, portraitTop, portraitBottom, portraitRowGap;
        final float landscapeIndicatorY, portraitIndicatorY;

        Grid(ConfigReader c) {
            enabled = c.b("home_grid_8x4", false);
            dp = c.b("grid_margins_dp", false);
            offsets = c.b("grid_margins_offset", false);
            landscapeHorizontal = c.has("grid_landscape_horizontal_distance")
                    ? c.f("grid_landscape_horizontal_distance", 0)
                    : (c.f("grid_landscape_margin_left", 0)
                    + c.f("grid_landscape_margin_right", 0)) / 2f;
            landscapeTop = c.f("grid_landscape_top_distance",
                    c.f("grid_landscape_margin_top", 0));
            landscapeBottom = c.f("grid_landscape_bottom_distance",
                    c.f("grid_landscape_margin_bottom", 0));
            portraitHorizontal = c.has("grid_portrait_horizontal_distance")
                    ? c.f("grid_portrait_horizontal_distance", 0)
                    : (c.f("grid_portrait_margin_left", 0)
                    + c.f("grid_portrait_margin_right", 0)) / 2f;
            portraitTop = c.f("grid_portrait_top_distance",
                    c.f("grid_portrait_margin_top", 0));
            portraitBottom = c.f("grid_portrait_bottom_distance",
                    c.f("grid_portrait_margin_bottom", 0));
            landscapeRowGap = c.f("grid_landscape_row_gap", offsets ? 0 : (dp ? 1 : 3));
            portraitRowGap = c.f("grid_portrait_row_gap", offsets ? 0 : (dp ? 1 : 3));
            landscapeIndicatorY = c.f("indicator_landscape_y", 0);
            portraitIndicatorY = c.f("indicator_portrait_y", 0);
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
            enabled = c.b("dock_customization", true);
            resizeAnimation = c.b("dock_resize_animation", false);
            smoothResizeAnimation = c.b("dock_smooth_resize_animation", true);
            dimensionsDp = c.b("dock_dimensions_dp", false);
            widthOffset = c.f("width_offset", 0);
            heightOffset = c.f("height_offset", 0);
            spacing = c.f("dock_spacing", 0);
            bottomOffset = c.f("dock_bottom_offset", 0);
            blurRadius = c.i("blur_radius", 100);
            cornersDp = c.b("corners_dp", false);
            cornerOffset = c.f("corner_offset", -1);
            blurCornerOffset = c.f("blur_corner_offset", 0);
            squircle = c.b("squircle", false);
            fillDiff = c.b("fill_diff", false);
            strokeEnabled = c.b("dock_stroke", true);
            squircleCp = c.i("sq_outer_cp", 58) / 100f;
            squircleStrokeWidth = c.f("sq_stroke_w", 4);
            squircleStrokeOffset = c.f("sq_stroke_off", 8);
            strokeWidth = c.f("stroke_w", 2);
            standardStrokeWidth = c.f("std_stroke_w", 4);
            strokeR = channel(c.i("stroke_base_r", 255));
            strokeG = channel(c.i("stroke_base_g", 255));
            strokeB = channel(c.i("stroke_base_b", 255));
            strokeAlpha = channel(c.i("stroke_base_alpha", 255));
            strokeShadow = c.b("stroke_shadow", false);
            strokeShadowRadius = c.f("shadow_radius", 8);
            strokeShadowAlpha = channel(c.i("shadow_alpha", 70));
            shadowEnabled = c.b("dock_shadow", true);
            shadowRadius = c.f("dock_shadow_radius", 42);
            shadowSize = c.f("dock_shadow_size", 52);
            shadowAlpha = channel(c.i("dock_shadow_alpha", 140));
            shadowY = c.f("dock_shadow_y", 12);
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
        final boolean enabled, dimensionsDp, dynamicAppCapture, fullscreenCapture;
        final float blur, chromatic, captureScale, thickness, ior, normalStrength, dome,
                lensRefraction, highlightWidth, depthEffect, brightness, specularStrength,
                rimLight, caustics, edgeBand, highlightAlpha, bleedTop, bleedBottom,
                recentsPrearmDistance;
        final int tintAlpha, captureFps, stopDelayMs, probeFps, motionThreshold,
                motionBitThreshold, motionHoldMs, blackThreshold, homeSettleDelayMs,
                tintR, tintG, tintB, specularSharp;

        Glass(ConfigReader c) {
            enabled = c.b("liquid_glass", false);
            dimensionsDp = c.b("liquid_dimensions_dp", false);
            blur = c.f("liquid_blur", 6);
            chromatic = c.i("liquid_chromatic", 8) / 100f;
            tintAlpha = channel(c.i("liquid_tint_alpha", 38));
            captureFps = clamp(c.i("liquid_capture_power_limit_fps", 20), 1, 165);
            stopDelayMs = Math.max(0, c.i("liquid_capture_stop_delay", 150));
            bleedTop = c.f("liquid_capture_bleed_top", -1);
            bleedBottom = c.f("liquid_capture_bleed_bottom", -1);
            thickness = c.f("liquid_thickness", 18);
            ior = c.i("liquid_ior", 155) / 100f;
            normalStrength = c.i("liquid_normal_strength", 115) / 100f;
            dome = c.i("liquid_dome", 100) / 100f;
            lensRefraction = c.f("liquid_lens_refraction", 12);
            captureScale = c.i("liquid_capture_scale", 50) / 100f;
            dynamicAppCapture = c.b("liquid_dynamic_app_capture", true);
            fullscreenCapture = c.b("liquid_capture_fullscreen", true);
            probeFps = clamp(c.i("liquid_dynamic_app_probe_fps", 3), 1, 60);
            motionThreshold = Math.max(0, c.i("liquid_dynamic_motion_threshold", 12));
            motionBitThreshold = Math.max(0, c.i("liquid_dynamic_bit_threshold", 18));
            motionHoldMs = Math.max(0, c.i("liquid_dynamic_hold_ms", 900));
            blackThreshold = channel(c.i("liquid_black_threshold", 10));
            homeSettleDelayMs = clamp(c.i("liquid_home_settle_delay", 1200), 200, 3000);
            highlightWidth = c.i("liquid_highlight_width", 100) / 100f;
            tintR = channel(c.i("liquid_tint_r", 238));
            tintG = channel(c.i("liquid_tint_g", 244));
            tintB = channel(c.i("liquid_tint_b", 255));
            depthEffect = c.i("liquid_depth_effect", 8) / 100f;
            brightness = c.i("liquid_brightness", 108) / 100f;
            specularSharp = Math.max(1, c.i("liquid_specular_sharp", 88));
            specularStrength = c.i("liquid_specular_strength", 105) / 100f;
            rimLight = c.i("liquid_rim_light", 100) / 100f;
            caustics = c.i("liquid_caustics", 28) / 100f;
            edgeBand = c.i("liquid_edge_band", 32) / 1000f;
            highlightAlpha = c.i("liquid_highlight_alpha", 100) / 100f;
            recentsPrearmDistance = c.f("liquid_recents_prearm_distance", 8);
        }
    }

    static final class Workstation {
        final boolean dockEnabled, dimensionsDp;
        final float dockWidthOffset, gridHorizontalOffset;
        final float allAppsLandscapeHorizontalOffset, allAppsLandscapeVerticalOffset;
        final float allAppsPortraitHorizontalOffset, allAppsPortraitVerticalOffset;
        final float iconTopOffset, iconBottomOffset;

        Workstation(ConfigReader c) {
            dockEnabled = c.b("workstation_dock_customization", false);
            dimensionsDp = c.b("dock_dimensions_dp", true);
            dockWidthOffset = c.f("workstation_dock_width_offset", 0);
            gridHorizontalOffset = c.f("workstation_grid_horizontal_offset", 0);
            // Preserve existing workstation All Apps tuning as the fallback for both
            // orientations; new installs can tune landscape and portrait independently.
            float legacyAllAppsX = c.f("workstation_all_apps_horizontal_offset", 0);
            float legacyAllAppsY = c.f("workstation_all_apps_vertical_offset", 0);
            allAppsLandscapeHorizontalOffset = c.f(
                    "workstation_all_apps_landscape_horizontal_offset", legacyAllAppsX);
            allAppsLandscapeVerticalOffset = c.f(
                    "workstation_all_apps_landscape_vertical_offset", legacyAllAppsY);
            allAppsPortraitHorizontalOffset = c.f(
                    "workstation_all_apps_portrait_horizontal_offset", legacyAllAppsX);
            allAppsPortraitVerticalOffset = c.f(
                    "workstation_all_apps_portrait_vertical_offset", legacyAllAppsY);
            iconTopOffset = c.f("workstation_dock_icon_top_offset", 0);
            iconBottomOffset = c.f("workstation_dock_icon_bottom_offset", 0);
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
