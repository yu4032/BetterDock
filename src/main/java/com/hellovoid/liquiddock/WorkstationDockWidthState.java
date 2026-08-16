package com.hellovoid.liquiddock;

/**
 * Keeps a stable native-width baseline while an additive workstation Dock width offset is active.
 * Layout passes may report the width we applied on the previous pass; that value must never be
 * treated as a new native width or the offset would accumulate indefinitely.
 */
final class WorkstationDockWidthState {
    private int nativeWidth = -1;
    private int lastAppliedWidth = -1;

    int targetWidth(int observedWidth, int offsetPx) {
        if (observedWidth <= 0) return observedWidth;
        if (nativeWidth <= 0 || observedWidth != lastAppliedWidth) {
            nativeWidth = observedWidth;
        }
        int target = Math.max(1, nativeWidth + offsetPx);
        lastAppliedWidth = target;
        return target;
    }

    int restoreWidth(int observedWidth) {
        if (observedWidth > 0 && (nativeWidth <= 0 || observedWidth != lastAppliedWidth)) {
            nativeWidth = observedWidth;
        }
        int target = nativeWidth > 0 ? nativeWidth : observedWidth;
        lastAppliedWidth = -1;
        return target;
    }
}
