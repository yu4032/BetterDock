package com.hellovoid.liquiddock;

import android.os.IBinder;

/** Raw Binder protocol shared by broker, SystemUI provider, and Launcher client. */
final class FreeformLeashProtocol {
    static final String BROKER_DESCRIPTOR =
            "com.hellovoid.liquiddock.IFreeformLeashBroker";
    static final String BROKER_PROVIDER_CALLBACK_DESCRIPTOR =
            "com.hellovoid.liquiddock.IFreeformLeashBrokerProviderCallback";
    static final String PROVIDER_DESCRIPTOR =
            "com.hellovoid.liquiddock.IFreeformLeashProvider";
    static final String CALLBACK_DESCRIPTOR =
            "com.hellovoid.liquiddock.IFreeformLeashCallback";

    // Broker transaction codes live on the broker Binder only. The +2 watcher therefore does
    // not reuse the diagnostic HOME request tombstone at +2 on the separate provider Binder.
    static final int TRANSACTION_REGISTER_PROVIDER = IBinder.FIRST_CALL_TRANSACTION;
    static final int TRANSACTION_GET_PROVIDER = IBinder.FIRST_CALL_TRANSACTION + 1;
    static final int TRANSACTION_WATCH_PROVIDER = IBinder.FIRST_CALL_TRANSACTION + 2;
    static final int TRANSACTION_PROVIDER_CHANGED = IBinder.FIRST_CALL_TRANSACTION;

    // Legacy per-task wire codes are intentionally left reserved. Reusing them with a new
    // payload would let old/new Launcher and SystemUI processes misinterpret each other.
    static final int TRANSACTION_REQUEST_LEASHES = IBinder.FIRST_CALL_TRANSACTION;
    static final int TRANSACTION_LEASH_RESULT = IBinder.FIRST_CALL_TRANSACTION;
    static final int TRANSACTION_REQUEST_VISIBLE_LEASH_SNAPSHOT =
            IBinder.FIRST_CALL_TRANSACTION + 1;
    static final int TRANSACTION_VISIBLE_LEASH_SNAPSHOT_RESULT =
            IBinder.FIRST_CALL_TRANSACTION + 1;

    static final int STATUS_OK = 0;
    static final int STATUS_UNAVAILABLE = 1;
    static final int STATUS_INFRASTRUCTURE_FAILURE = 2;

    static final int MAX_TASKS = 32;
    static final long REQUEST_TIMEOUT_MS = 25L;

    static final String MODULE_PACKAGE = "com.hellovoid.liquiddock";
    static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    static final String LAUNCHER_PACKAGE = "com.miui.home";

    private FreeformLeashProtocol() {}
}
