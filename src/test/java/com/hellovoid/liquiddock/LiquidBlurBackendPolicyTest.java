package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LiquidBlurBackendPolicyTest {
    @Test
    public void shaderRequestAlwaysUsesShader() {
        assertEquals(LiquidBlurMode.SHADER,
                LiquidBlurBackendPolicy.activeBackend(LiquidBlurMode.SHADER, true));
        assertEquals(LiquidBlurMode.SHADER,
                LiquidBlurBackendPolicy.activeBackend(LiquidBlurMode.SHADER, false));
    }

    @Test
    public void advancedRequestUsesAdvancedOnlyWhenCapabilityApplied() {
        assertEquals(LiquidBlurMode.ADVANCED_MATERIAL,
                LiquidBlurBackendPolicy.activeBackend(
                        LiquidBlurMode.ADVANCED_MATERIAL, true));
        assertEquals(LiquidBlurMode.SHADER,
                LiquidBlurBackendPolicy.activeBackend(
                        LiquidBlurMode.ADVANCED_MATERIAL, false));
    }

    @Test
    public void fallbackDoesNotRewriteRequestedMode() {
        LiquidBlurMode requested = LiquidBlurMode.ADVANCED_MATERIAL;

        LiquidBlurMode active = LiquidBlurBackendPolicy.activeBackend(requested, false);

        assertEquals(LiquidBlurMode.ADVANCED_MATERIAL, requested);
        assertEquals(LiquidBlurMode.SHADER, active);
    }
}
