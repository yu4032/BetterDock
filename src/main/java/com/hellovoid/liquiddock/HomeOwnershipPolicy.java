package com.hellovoid.liquiddock;

/** Pure HOME/APP classification for SystemUI's existing task repository evidence. */
final class HomeOwnershipPolicy {
    enum Baseline { HOME, APP, UNKNOWN }

    static final class Result {
        final Baseline baseline;
        final boolean retryRecommended;

        Result(Baseline baseline, boolean retryRecommended) {
            this.baseline = baseline;
            this.retryRecommended = retryRecommended;
        }
    }

    private static final Result HOME = new Result(Baseline.HOME, false);
    private static final Result APP = new Result(Baseline.APP, false);
    private static final Result UNKNOWN = new Result(Baseline.UNKNOWN, false);
    private static final Result TRANSITION_CONFLICT = new Result(Baseline.UNKNOWN, true);

    private HomeOwnershipPolicy() {}

    static Result classify(boolean homeVisible, int homeTaskId,
                           int topFullscreenTaskId, boolean confirmation) {
        if (homeTaskId < 0) return UNKNOWN;
        if (!homeVisible) return APP;
        if (topFullscreenTaskId >= 0 && topFullscreenTaskId != homeTaskId) {
            return confirmation ? APP : TRANSITION_CONFLICT;
        }
        return HOME;
    }
}
