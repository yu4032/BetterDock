package com.hellovoid.liquiddock;

/** Coalesces many node invalidations into one launcher-scene render work item. */
final class LauncherGlassFramePolicy {
    private boolean scheduled;
    private boolean refreshProducer;
    private boolean rebuildBackdrop;

    synchronized boolean request(boolean requireProducerRefresh) {
        refreshProducer |= requireProducerRefresh;
        rebuildBackdrop |= requireProducerRefresh;
        return schedule();
    }

    synchronized boolean requestBackdropRefresh() {
        refreshProducer = true;
        rebuildBackdrop = true;
        return schedule();
    }

    synchronized boolean requestBackdropRebuild() {
        rebuildBackdrop = true;
        return schedule();
    }

    private boolean schedule() {
        if (scheduled) return false;
        scheduled = true;
        return true;
    }

    synchronized Work consume() {
        if (!scheduled) return new Work(false, false, false);
        Work work = new Work(true, refreshProducer, rebuildBackdrop);
        scheduled = false;
        refreshProducer = false;
        rebuildBackdrop = false;
        return work;
    }

    static final class Work {
        final boolean render;
        final boolean refreshProducer;
        final boolean rebuildBackdrop;

        Work(boolean render, boolean refreshProducer, boolean rebuildBackdrop) {
            this.render = render;
            this.refreshProducer = refreshProducer;
            this.rebuildBackdrop = rebuildBackdrop;
        }
    }
}
