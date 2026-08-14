package com.hellovoid.liquiddock;

/** Pure runtime fallback policy. Persisted user intent is never mutated here. */
final class LiquidBlurBackendPolicy {
    private LiquidBlurBackendPolicy() {}

    static LiquidBlurMode activeBackend(LiquidBlurMode requested, boolean capabilityApplied) {
        return requested == LiquidBlurMode.ADVANCED_MATERIAL && capabilityApplied
                ? LiquidBlurMode.ADVANCED_MATERIAL
                : LiquidBlurMode.SHADER;
    }
}
