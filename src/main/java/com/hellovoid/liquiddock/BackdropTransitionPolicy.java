package com.hellovoid.liquiddock;

/** Drops an installed frame only when the scene changes capture-source domain. */
final class BackdropTransitionPolicy {
    private BackdropTransitionPolicy() {}

    static boolean shouldDropInstalled(CaptureScene installed, CaptureScene target) {
        if (installed == null || target == null) return false;
        return CaptureSourcePolicy.sourceFor(installed) != CaptureSourcePolicy.sourceFor(target);
    }
}
