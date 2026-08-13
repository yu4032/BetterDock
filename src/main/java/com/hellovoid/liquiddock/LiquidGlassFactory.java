package com.hellovoid.liquiddock;

import android.view.View;

/** Sole assembly point for a liquid-glass view. Keeps JSON/default knowledge out of hooks. */
final class LiquidGlassFactory {
    private LiquidGlassFactory() {}

    static DockLiquidGlassView create(View background, View workspace,
                                      LiquidDockConfig.Glass config,
                                      LiquidDockConfig.Dock dockConfig,
                                      boolean squircle, float squircleCp) {
        float scale = config.dimensionsDp
                ? background.getResources().getDisplayMetrics().density : 1f;
        DockLiquidGlassView view = new DockLiquidGlassView(background, workspace,
                Math.round(config.blur * scale), config.chromatic, config.tintAlpha,
                squircle, squircleCp, config.captureFps);
        view.setStopGraceMillis(config.stopDelayMs);
        view.setBleedVerticalPx(config.bleedTop < 0 ? -1 : Math.round(config.bleedTop * scale),
                config.bleedBottom < 0 ? -1 : Math.round(config.bleedBottom * scale));
        view.setPrismalParams(config.thickness * scale, config.ior, config.normalStrength,
                config.dome, config.lensRefraction * scale);
        view.setCaptureScale(config.captureScale);
        view.setDynamicAppCapture(config.dynamicAppCapture, config.captureFps, config.probeFps,
                config.motionThreshold, config.motionBitThreshold, config.motionHoldMs,
                config.blackThreshold);
        view.setCapturePowerLimitFps(config.captureFps);
        view.setHighlightWidth(config.highlightWidth);
        view.setTintColor(config.tintR, config.tintG, config.tintB);
        view.setAppearance(config.depthEffect, config.brightness, config.specularSharp,
                config.specularStrength, config.rimLight, config.caustics, config.edgeBand);
        view.setHighlightAlpha(config.highlightAlpha);
        view.setDockStrokeConfig(dockConfig);
        view.setRecentsPrearmDistanceDp(config.recentsPrearmDistance);
        view.setFullscreenCapture(config.fullscreenCapture);
        return view;
    }
}
