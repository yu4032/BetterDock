package com.miui.home.common.utils;

import android.view.View;

/**
 * Stub class injected into com.miui.home to act as HyperLight proxy entry point.
 * HyperLight hooks applyMaterialBlur(). When we call it on the dock background,
 * HyperLight's LSPosed hook intercepts and applies liquid glass.
 */
public class MiuixMaterialBlurUtilities {
    public static void applyMaterialBlur(View view, Runnable onStart, Runnable onEnd) {
        // Stub body — never actually executed, HyperLight hook intercepts
    }
}
