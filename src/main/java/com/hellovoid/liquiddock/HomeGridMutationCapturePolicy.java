package com.hellovoid.liquiddock;

/**
 * Suppresses mutation captures while Launcher is changing orientation and de-duplicates
 * settled layout callbacks independently for portrait and landscape.
 */
final class HomeGridMutationCapturePolicy {
    private boolean transitionInProgress;
    private boolean hasLandscapeFingerprint;
    private boolean hasPortraitFingerprint;
    private long landscapeFingerprint;
    private long portraitFingerprint;

    synchronized void beginTransition() {
        transitionInProgress = true;
    }

    synchronized void endTransition(HomeGridOrientation orientation, long fingerprint) {
        if (orientation != null) setBaseline(orientation, fingerprint);
        transitionInProgress = false;
    }

    synchronized void endTransition() {
        transitionInProgress = false;
    }

    synchronized boolean shouldCapture(HomeGridOrientation orientation, long fingerprint) {
        if (orientation == null || transitionInProgress) return false;
        if (orientation == HomeGridOrientation.LANDSCAPE) {
            if (hasLandscapeFingerprint && landscapeFingerprint == fingerprint) return false;
            hasLandscapeFingerprint = true;
            landscapeFingerprint = fingerprint;
            return true;
        }
        if (hasPortraitFingerprint && portraitFingerprint == fingerprint) return false;
        hasPortraitFingerprint = true;
        portraitFingerprint = fingerprint;
        return true;
    }

    private void setBaseline(HomeGridOrientation orientation, long fingerprint) {
        if (orientation == HomeGridOrientation.LANDSCAPE) {
            hasLandscapeFingerprint = true;
            landscapeFingerprint = fingerprint;
        } else {
            hasPortraitFingerprint = true;
            portraitFingerprint = fingerprint;
        }
    }
}
