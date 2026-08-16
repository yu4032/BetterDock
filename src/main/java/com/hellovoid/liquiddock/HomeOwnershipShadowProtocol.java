package com.hellovoid.liquiddock;

import android.os.IBinder;

final class HomeOwnershipShadowProtocol {
    static final String CALLBACK_DESCRIPTOR =
            "com.hellovoid.liquiddock.IHomeOwnershipShadowCallback";

    static final int TRANSACTION_REQUEST_HOME_OWNERSHIP_SHADOW =
            IBinder.FIRST_CALL_TRANSACTION + 2;
    static final int TRANSACTION_HOME_OWNERSHIP_SHADOW_RESULT =
            IBinder.FIRST_CALL_TRANSACTION + 2;

    static final int STATUS_OK = 0;
    static final int STATUS_UNAVAILABLE = 1;
    static final int STATUS_STRUCTURE_FAILURE = 2;

    static final long RECHECK_DELAY_MS = 160L;
    static final long PENDING_TTL_MS = 1500L;
    static final int MAX_PENDING = 16;

    private HomeOwnershipShadowProtocol() {}
}
