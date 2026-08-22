package com.hellovoid.prismal;

/**
 * Process-local component switches shared by Prismal renderers.
 *
 * <p>Launcher and Dock intentionally keep separate profiles. Defaults reproduce the verified
 * production baselines: compact launcher suppresses the nine upstream white/highlight paths and
 * keeps its continuous safe edge highlight, while Dock retains all upstream highlight paths.</p>
 */
public final class PrismalComponentControls {
    static final class Profile {
        final boolean skyHaze;
        final boolean specular;
        final boolean litRim;
        final boolean oppositeRim;
        final boolean cornerRim;
        final boolean faceSheen;
        final boolean plainHighlight;
        final boolean caustics;
        final boolean pressGlow;
        final boolean compactSafeHighlight;

        Profile(
                boolean skyHaze,
                boolean specular,
                boolean litRim,
                boolean oppositeRim,
                boolean cornerRim,
                boolean faceSheen,
                boolean plainHighlight,
                boolean caustics,
                boolean pressGlow,
                boolean compactSafeHighlight) {
            this.skyHaze = skyHaze;
            this.specular = specular;
            this.litRim = litRim;
            this.oppositeRim = oppositeRim;
            this.cornerRim = cornerRim;
            this.faceSheen = faceSheen;
            this.plainHighlight = plainHighlight;
            this.caustics = caustics;
            this.pressGlow = pressGlow;
            this.compactSafeHighlight = compactSafeHighlight;
        }
    }

    private static volatile Profile launcher = new Profile(
            false, false, false, false, false, false, false, false, false, true);
    private static volatile Profile dock = new Profile(
            true, true, true, true, true, true, true, true, true, false);
    private static final Profile UPSTREAM = new Profile(
            true, true, true, true, true, true, true, true, true, false);

    private PrismalComponentControls() {}

    public static void configureLauncher(
            boolean skyHaze,
            boolean specular,
            boolean litRim,
            boolean oppositeRim,
            boolean cornerRim,
            boolean faceSheen,
            boolean plainHighlight,
            boolean caustics,
            boolean pressGlow,
            boolean compactSafeHighlight) {
        launcher = new Profile(
                skyHaze, specular, litRim, oppositeRim, cornerRim, faceSheen,
                plainHighlight, caustics, pressGlow, compactSafeHighlight);
    }

    public static void configureDock(
            boolean skyHaze,
            boolean specular,
            boolean litRim,
            boolean oppositeRim,
            boolean cornerRim,
            boolean faceSheen,
            boolean plainHighlight,
            boolean caustics,
            boolean pressGlow) {
        dock = new Profile(
                skyHaze, specular, litRim, oppositeRim, cornerRim, faceSheen,
                plainHighlight, caustics, pressGlow, false);
    }

    static Profile forMode(PrismalRenderer.Mode mode) {
        if (mode == PrismalRenderer.Mode.LAUNCHER_COMPACT) return launcher;
        if (mode == PrismalRenderer.Mode.DOCK_SINGLE_EDGE) return dock;
        return UPSTREAM;
    }
}
