package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertSame;

public class LauncherGlassGeometryStabilityTest {
    @Test
    public void ancestorTransformDoesNotCommitUntilGeometrySettles() {
        LauncherGlassGeometryStability stability = new LauncherGlassGeometryStability();
        LauncherGlassGeometry.Snapshot current = snapshot(100f, 100f);
        LauncherGlassGeometry.Snapshot moving1 = snapshot(92f, 96f);
        LauncherGlassGeometry.Snapshot moving2 = snapshot(84f, 91f);
        LauncherGlassGeometry.Snapshot settled = snapshot(80f, 88f);

        assertSame(current, stability.select(current, moving1, false));
        assertSame(current, stability.select(current, moving2, false));
        assertSame(current, stability.select(current, settled, false));
        assertSame(settled, stability.select(current, settled, false));
    }

    @Test
    public void localMaterialChangeCommitsImmediately() {
        LauncherGlassGeometryStability stability = new LauncherGlassGeometryStability();
        LauncherGlassGeometry.Snapshot current = snapshot(100f, 100f);
        LauncherGlassGeometry.Snapshot changed = snapshot(104f, 100f);

        assertSame(changed, stability.select(current, changed, true));
    }

    @Test
    public void workspaceScrollAncestorMotionCommitsImmediately() {
        LauncherGlassGeometryStability stability = new LauncherGlassGeometryStability();
        LauncherGlassGeometry.Snapshot current = snapshot(100f, 100f);
        LauncherGlassGeometry.Snapshot movedByWorkspace = snapshot(72f, 100f);

        assertSame(movedByWorkspace,
                stability.select(current, movedByWorkspace, false, true));
    }

    @Test
    public void disappearanceCommitsImmediately() {
        LauncherGlassGeometryStability stability = new LauncherGlassGeometryStability();
        LauncherGlassGeometry.Snapshot current = snapshot(100f, 100f);

        assertSame(null, stability.select(current, null, false));
    }

    private static LauncherGlassGeometry.Snapshot snapshot(float left, float top) {
        return LauncherGlassGeometry.resolve(
                3000, 1800, left, top, left + 220f, top + 220f, 48f);
    }
}
