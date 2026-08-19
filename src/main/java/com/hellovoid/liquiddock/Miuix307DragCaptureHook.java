package com.hellovoid.liquiddock;

import android.view.View;

/**
 * Compatibility shell for the retired DockLiquidGlassView capture-freeze hook.
 * The PassBlur/OES renderer consumes the compositor stream directly and has no captured frame to
 * freeze or drag-layer ScreenCapture exclusion to maintain.
 */
final class Miuix307DragCaptureHook {
    private Miuix307DragCaptureHook() {}

    static void install(ClassLoader classLoader) {
        // Intentionally empty in the zero-copy release pipeline.
    }

    static void bind(View background) {
        // Intentionally empty in the zero-copy release pipeline.
    }
}
