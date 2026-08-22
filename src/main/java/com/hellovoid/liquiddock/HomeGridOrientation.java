package com.hellovoid.liquiddock;

/** Stable semantic orientation used by custom-grid layout memory. */
enum HomeGridOrientation {
    LANDSCAPE,
    PORTRAIT;

    HomeGridOrientation other() {
        return this == LANDSCAPE ? PORTRAIT : LANDSCAPE;
    }
}
