package com.hellovoid.liquiddock;

/** Workstation-only capture burst: keep sampling until the backdrop converges. */
final class WorkstationCaptureBurst {
    private boolean active;
    private boolean signatureValid;
    private long lastSignature;
    private int stableComparisons;

    void start() {
        active = true;
        signatureValid = false;
        stableComparisons = 0;
    }

    void stop() {
        active = false;
        signatureValid = false;
        stableComparisons = 0;
    }

    boolean isActive() {
        return active;
    }

    /** Returns true while another sample is required. */
    boolean onFrame(long signature) {
        if (!active) return false;
        if (!signatureValid) {
            signatureValid = true;
            lastSignature = signature;
            stableComparisons = 0;
            return true;
        }
        if (lastSignature != signature) {
            lastSignature = signature;
            stableComparisons = 0;
            return true;
        }
        stableComparisons++;
        if (stableComparisons >= 2) {
            active = false;
            return false;
        }
        return true;
    }
}
