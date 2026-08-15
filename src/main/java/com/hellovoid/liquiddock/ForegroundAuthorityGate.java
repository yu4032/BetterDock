package com.hellovoid.liquiddock;

/**
 * Filters noisy foreground-task snapshots before they are allowed to change persistent
 * HOME/EXTERNAL authority.  A task-list sample is evidence, not an ownership transition.
 *
 * HOME -> EXTERNAL requires an external boundary supplied by the caller (Launcher focus loss,
 * fallback pause, or a Dock interaction that physically occurs over an external task).
 * EXTERNAL -> HOME requires a Launcher-home boundary and two consistent HOME samples separated
 * by a short stability interval.  UNKNOWN or contradictory samples reset the HOME candidate.
 */
final class ForegroundAuthorityGate {
    static final long HOME_CONFIRM_STABLE_NANOS = 120_000_000L;

    private long homeCandidateSinceNanos = Long.MIN_VALUE;

    ForegroundOwnership filter(ForegroundOwnership current,
                               ForegroundOwnership observed,
                               boolean allowHomeCommit,
                               boolean allowExternalCommit,
                               boolean returningFromExternal,
                               long nowNanos) {
        if (current == null) current = ForegroundOwnership.UNKNOWN;
        if (observed == null) observed = ForegroundOwnership.UNKNOWN;

        if (observed == ForegroundOwnership.UNKNOWN) {
            resetHomeCandidate();
            return current;
        }

        if (observed == ForegroundOwnership.EXTERNAL) {
            resetHomeCandidate();
            if (current == ForegroundOwnership.EXTERNAL || allowExternalCommit) {
                return ForegroundOwnership.EXTERNAL;
            }
            return current;
        }

        // observed == HOME
        if (current == ForegroundOwnership.HOME) {
            resetHomeCandidate();
            return ForegroundOwnership.HOME;
        }
        if (!allowHomeCommit) {
            resetHomeCandidate();
            return current;
        }

        // Initial HOME bootstrap is safe when there was no preceding external boundary.
        if (!returningFromExternal && current != ForegroundOwnership.EXTERNAL) {
            resetHomeCandidate();
            return ForegroundOwnership.HOME;
        }

        if (homeCandidateSinceNanos == Long.MIN_VALUE) {
            homeCandidateSinceNanos = nowNanos;
            return current;
        }
        if (nowNanos - homeCandidateSinceNanos < HOME_CONFIRM_STABLE_NANOS) {
            return current;
        }

        resetHomeCandidate();
        return ForegroundOwnership.HOME;
    }

    void resetHomeCandidate() {
        homeCandidateSinceNanos = Long.MIN_VALUE;
    }
}
