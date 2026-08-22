package com.hellovoid.liquiddock;

/** Coalesces many node invalidations into one launcher-scene render work item. */
final class LauncherGlassFramePolicy {
    private boolean scheduled;
    private boolean refreshProducer;

    synchronized boolean request(boolean requireProducerRefresh) {
        refreshProducer |= requireProducerRefresh;
        if (scheduled) return false;
        scheduled = true;
        return true;
    }

    synchronized Work consume() {
        if (!scheduled) return new Work(false, false);
        Work work = new Work(true, refreshProducer);
        scheduled = false;
        refreshProducer = false;
        return work;
    }

    static final class Work {
        final boolean render;
        final boolean refreshProducer;

        Work(boolean render, boolean refreshProducer) {
            this.render = render;
            this.refreshProducer = refreshProducer;
        }
    }
}
