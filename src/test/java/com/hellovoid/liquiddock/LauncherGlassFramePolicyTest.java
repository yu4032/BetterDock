package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LauncherGlassFramePolicyTest {
    @Test
    public void redrawWithoutBackdropInvalidationKeepsPreparedBackdropClean() {
        LauncherGlassFramePolicy policy = new LauncherGlassFramePolicy();

        assertTrue(policy.request(false));
        LauncherGlassFramePolicy.Work work = policy.consume();

        assertTrue(work.render);
        assertFalse(work.refreshProducer);
        assertFalse(work.rebuildBackdrop);
    }

    @Test
    public void backdropInvalidationIsStickyAcrossCoalescedRequests() {
        LauncherGlassFramePolicy policy = new LauncherGlassFramePolicy();

        assertTrue(policy.request(false));
        assertFalse(policy.requestBackdropRefresh());
        LauncherGlassFramePolicy.Work work = policy.consume();

        assertTrue(work.render);
        assertTrue(work.refreshProducer);
        assertTrue(work.rebuildBackdrop);
    }

    @Test
    public void cachedBackdropCanBeRebuiltWithoutRefreshingProducer() {
        LauncherGlassFramePolicy policy = new LauncherGlassFramePolicy();

        assertTrue(policy.requestBackdropRebuild());
        LauncherGlassFramePolicy.Work work = policy.consume();

        assertTrue(work.render);
        assertFalse(work.refreshProducer);
        assertTrue(work.rebuildBackdrop);
    }
}
