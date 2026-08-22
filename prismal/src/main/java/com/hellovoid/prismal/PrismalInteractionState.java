package com.hellovoid.prismal;

/** Per-draw touch interaction for one glass node; optical material parameters stay shared. */
public final class PrismalInteractionState {
    public static final PrismalInteractionState IDLE =
            new PrismalInteractionState(0f, 0.5f, 0.5f);

    public final float pressProgress;
    public final float glowCenterX;
    public final float glowCenterY;

    public PrismalInteractionState(float pressProgress, float glowCenterX, float glowCenterY) {
        this.pressProgress = clamp01(pressProgress);
        this.glowCenterX = clamp01(glowCenterX);
        this.glowCenterY = clamp01(glowCenterY);
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0.5f;
        return Math.max(0f, Math.min(1f, value));
    }
}
