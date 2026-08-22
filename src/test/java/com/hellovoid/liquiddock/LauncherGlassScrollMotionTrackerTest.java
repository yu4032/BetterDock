package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LauncherGlassScrollMotionTrackerTest {
    @Test
    public void onlyMovementOnSameWorkspaceIsRealtimeMotion() {
        LauncherGlassScrollMotionTracker tracker = new LauncherGlassScrollMotionTracker();
        Object workspaceA = new Object();
        Object workspaceB = new Object();

        assertFalse(tracker.update(workspaceA, 0, 0));
        assertFalse(tracker.update(workspaceA, 0, 0));
        assertTrue(tracker.update(workspaceA, 32, 0));
        assertTrue(tracker.update(workspaceA, 32, 14));
        assertFalse(tracker.update(workspaceA, 32, 14));

        assertFalse(tracker.update(workspaceB, 700, 0));
        assertTrue(tracker.update(workspaceB, 680, 0));
    }

    @Test
    public void nullWorkspaceResetsBaseline() {
        LauncherGlassScrollMotionTracker tracker = new LauncherGlassScrollMotionTracker();
        Object workspace = new Object();

        assertFalse(tracker.update(workspace, 100, 0));
        assertTrue(tracker.update(workspace, 120, 0));
        assertFalse(tracker.update(null, 0, 0));
        assertFalse(tracker.update(workspace, 140, 0));
    }
}
