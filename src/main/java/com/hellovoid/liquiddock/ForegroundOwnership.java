package com.hellovoid.liquiddock;

/** Physical foreground-task ownership. UNKNOWN is never treated as evidence of HOME. */
enum ForegroundOwnership {
    HOME,
    EXTERNAL,
    UNKNOWN
}
