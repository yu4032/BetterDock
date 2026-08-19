package com.hellovoid.liquiddock;

/** Pure class-name policy for the HyperOS Dock material implementations LiquidDock supports. */
final class Miuix307MaterialHostPolicy {
    private static final String BLUR_BACKGROUND2 =
            "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2";
    private static final String MIUIX_BLUR_BACKGROUND =
            "com.miui.home.launcher.hotseats.HotSeatsListContentMiuiXBlurBackground";

    private Miuix307MaterialHostPolicy() {}

    static boolean supportsZeroCopyBackdrop(String className) {
        return BLUR_BACKGROUND2.equals(className) || MIUIX_BLUR_BACKGROUND.equals(className);
    }

    static boolean usesExactBackgroundBlur(String className) {
        return BLUR_BACKGROUND2.equals(className);
    }
}
