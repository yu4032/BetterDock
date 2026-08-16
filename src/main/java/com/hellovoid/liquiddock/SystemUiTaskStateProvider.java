package com.hellovoid.liquiddock;

import android.content.Context;
import android.os.Binder;
import android.os.Parcel;
import android.os.RemoteException;

/**
 * SystemUI-process Binder rendezvous for independent task-state capabilities.
 * Owns transport registration only; no task or HOME/APP state lives here.
 */
final class SystemUiTaskStateProvider {
    private static volatile FreeformLeashBrokerClient brokerClient;

    private SystemUiTaskStateProvider() {}

    static void attachContext(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        Context safeContext = app != null ? app : context;
        FreeformLeashBrokerClient client = brokerClient;
        if (client == null) {
            synchronized (SystemUiTaskStateProvider.class) {
                client = brokerClient;
                if (client == null) {
                    client = FreeformLeashBrokerClient.shared(
                            safeContext, FreeformLeashBrokerClient.Role.SYSTEM_UI);
                    brokerClient = client;
                }
            }
        }
        client.setSystemUiProvider(PROVIDER_BINDER);
    }

    static boolean callerIsLauncher(Context context) {
        if (context == null) return false;
        try {
            String[] packages = context.getPackageManager().getPackagesForUid(Binder.getCallingUid());
            return FreeformBridgePolicy.packageListContains(
                    packages, FreeformLeashProtocol.LAUNCHER_PACKAGE);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static final Binder PROVIDER_BINDER = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            try {
                if (SystemUiHomeOwnershipSource.handles(code)) {
                    return SystemUiHomeOwnershipSource.handleTransaction(code, data);
                }
                if (SystemUiFreeformLeashProvider.handles(code)) {
                    return SystemUiFreeformLeashProvider.handleTransaction(code, data);
                }
                return super.onTransact(code, data, reply, flags);
            } catch (Throwable error) {
                // Provider multiplexing must never destabilize SystemUI. Individual capability
                // handlers own their own health/fail-closed response semantics.
                Api101Bridge.log("[DC] SystemUI task-state provider transaction unavailable", error);
                return true;
            }
        }
    };
}
