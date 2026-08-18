package com.hellovoid.liquiddock;

import android.os.IBinder;

/** One-way SystemUI -> Launcher transition visual-handoff protocol. */
final class SystemUiTransitionProtocol {
    static final int VERSION = 1;

    static final String CALLBACK_DESCRIPTOR =
            "com.hellovoid.liquiddock.ISystemUiTransitionCallback";

    static final int TRANSACTION_REGISTER_CALLBACK = IBinder.FIRST_CALL_TRANSACTION + 4;
    static final int TRANSACTION_EVENT = IBinder.FIRST_CALL_TRANSACTION + 2;

    static final int APP_TO_LAUNCHER_START = 1;
    static final int TRANSITION_MERGED = 2;
    static final int TRANSITION_FINISHED = 3;
    static final int TRANSITION_ABORTED = 4;
    static final int LAUNCHER_TO_APP = 5;

    private SystemUiTransitionProtocol() {}

    static boolean isKnownEvent(int type) {
        return type >= APP_TO_LAUNCHER_START && type <= LAUNCHER_TO_APP;
    }
}
