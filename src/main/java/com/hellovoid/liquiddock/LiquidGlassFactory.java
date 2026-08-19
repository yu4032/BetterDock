package com.hellovoid.liquiddock;

import android.view.View;

/**
 * Compatibility assembly point retained for old MainHook branches.
 *
 * release/1.3.0 no longer owns a screen-capture renderer. The real glass path is installed by
 * Miuix307MaterialPipeline/Miuix307ZeroCopyRenderer; if an obsolete branch reaches this factory,
 * it receives a transparent no-capture shell rather than resurrecting Bitmap capture.
 */
final class LiquidGlassFactory {
    private LiquidGlassFactory() {}

    static DockLiquidGlassView create(View background, View workspace,
                                      LiquidDockConfig.Glass config,
                                      LiquidDockConfig.Dock dockConfig,
                                      boolean squircle, float squircleCp) {
        return new DockLiquidGlassView(background.getContext());
    }
}
