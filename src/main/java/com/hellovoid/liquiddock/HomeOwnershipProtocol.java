package com.hellovoid.liquiddock;

import android.os.IBinder;

/** Versioned Binder protocol for the production SystemUI HOME/APP baseline capability. */
final class HomeOwnershipProtocol {
    static final int VERSION = 1;

    static final String CALLBACK_DESCRIPTOR =
            "com.hellovoid.liquiddock.IHomeOwnershipCallback";

    // FIRST_CALL_TRANSACTION + 2 was used by the diagnostic shadow and is intentionally
    // left reserved so mixed diagnostic/production process generations fail closed.
    static final int TRANSACTION_REQUEST_BASELINE = IBinder.FIRST_CALL_TRANSACTION + 3;
    static final int TRANSACTION_BASELINE_RESULT = IBinder.FIRST_CALL_TRANSACTION;

    static final int STATUS_OK = 0;
    static final int STATUS_UNAVAILABLE = 1;
    static final int STATUS_STRUCTURE_FAILURE = 2;

    static final int BASELINE_UNKNOWN = 0;
    static final int BASELINE_HOME = 1;
    static final int BASELINE_APP = 2;

    static final int MAX_PENDING = 16;
    static final long REQUEST_TIMEOUT_MS = 250L;
    static final long RECHECK_DELAY_MS = 160L;

    private HomeOwnershipProtocol() {}

    static int encodeBaseline(HomeOwnershipPolicy.Baseline baseline) {
        if (baseline == HomeOwnershipPolicy.Baseline.HOME) return BASELINE_HOME;
        if (baseline == HomeOwnershipPolicy.Baseline.APP) return BASELINE_APP;
        return BASELINE_UNKNOWN;
    }

    static HomeOwnershipPolicy.Baseline decodeBaseline(int wireValue) {
        if (wireValue == BASELINE_HOME) return HomeOwnershipPolicy.Baseline.HOME;
        if (wireValue == BASELINE_APP) return HomeOwnershipPolicy.Baseline.APP;
        return HomeOwnershipPolicy.Baseline.UNKNOWN;
    }

    static boolean isKnownBaselineWireValue(int wireValue) {
        return wireValue == BASELINE_UNKNOWN
                || wireValue == BASELINE_HOME
                || wireValue == BASELINE_APP;
    }
}
