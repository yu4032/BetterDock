package com.hellovoid.liquiddock;

import android.content.Context;

/**
 * Compatibility shell for callers that still reference the former capture ownership bridge.
 * PassBlur/OES is compositor-backed and does not need HOME/APP screen-capture source selection.
 */
final class HomeOwnershipRuntime {
    private HomeOwnershipRuntime() {}

    static void bind(DockLiquidGlassView glass, Context context) {
        // Intentionally empty: there is no capture source to reclassify in the zero-copy backend.
    }

    static void request(String reason) {
        // Intentionally empty.
    }

    static HomeOwnershipPolicy.Baseline appliedBaselineForTests() {
        return HomeOwnershipPolicy.Baseline.UNKNOWN;
    }
}
