package com.hellovoid.prismal;

/** Immutable per-draw highlight selection. Optics remain shared by Dock and Launcher surfaces. */
public final class PrismalHighlightProfile {
    public static final PrismalHighlightProfile ALL_ENABLED =
            new PrismalHighlightProfile(true, true, true, true, true, true, true, true, true);

    public final boolean skyHaze;
    public final boolean specular;
    public final boolean litRim;
    public final boolean oppositeRim;
    public final boolean cornerRim;
    public final boolean faceSheen;
    public final boolean plainHighlight;
    public final boolean caustics;
    public final boolean pressGlow;

    public PrismalHighlightProfile(
            boolean skyHaze,
            boolean specular,
            boolean litRim,
            boolean oppositeRim,
            boolean cornerRim,
            boolean faceSheen,
            boolean plainHighlight,
            boolean caustics,
            boolean pressGlow) {
        this.skyHaze = skyHaze;
        this.specular = specular;
        this.litRim = litRim;
        this.oppositeRim = oppositeRim;
        this.cornerRim = cornerRim;
        this.faceSheen = faceSheen;
        this.plainHighlight = plainHighlight;
        this.caustics = caustics;
        this.pressGlow = pressGlow;
    }
}
