package com.hellovoid.liquiddock;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

/** Binder rendezvous between injected SystemUI and Launcher processes. */
public final class FreeformLeashBrokerService extends Service {
    private static final String TAG = "LiquidDock";
    private final Object lock = new Object();
    private IBinder provider;
    private IBinder.DeathRecipient providerDeath;
    private IBinder providerWatcher;
    private IBinder.DeathRecipient providerWatcherDeath;

    private final Binder binder = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code != FreeformLeashProtocol.TRANSACTION_REGISTER_PROVIDER
                    && code != FreeformLeashProtocol.TRANSACTION_GET_PROVIDER
                    && code != FreeformLeashProtocol.TRANSACTION_WATCH_PROVIDER) {
                return super.onTransact(code, data, reply, flags);
            }
            try {
                data.enforceInterface(FreeformLeashProtocol.BROKER_DESCRIPTOR);
                if (code == FreeformLeashProtocol.TRANSACTION_REGISTER_PROVIDER) {
                    if (!callerHasPackage(FreeformLeashProtocol.SYSTEM_UI_PACKAGE)) {
                        if (reply != null) reply.writeException(new SecurityException("SystemUI only"));
                        return true;
                    }
                    IBinder next = data.readStrongBinder();
                    replaceProvider(next);
                    if (reply != null) {
                        reply.writeNoException();
                        reply.writeInt(next != null ? 1 : 0);
                    }
                    return true;
                }

                if (!callerHasPackage(FreeformLeashProtocol.LAUNCHER_PACKAGE)) {
                    if (reply != null) reply.writeException(new SecurityException("Launcher only"));
                    return true;
                }

                if (code == FreeformLeashProtocol.TRANSACTION_WATCH_PROVIDER) {
                    IBinder watcher = data.readStrongBinder();
                    replaceProviderWatcher(watcher);
                    IBinder current;
                    synchronized (lock) { current = provider; }
                    if (watcher != null) notifyProviderWatcher(watcher, current);
                    if (reply != null) {
                        reply.writeNoException();
                        reply.writeInt(watcher != null ? 1 : 0);
                    }
                    return true;
                }

                IBinder current;
                synchronized (lock) { current = provider; }
                if (reply != null) {
                    reply.writeNoException();
                    reply.writeStrongBinder(current);
                }
                return true;
            } catch (Throwable error) {
                Log.e(TAG, "freeform broker transaction failed", error);
                if (reply != null) {
                    try { reply.writeException(new RemoteException("broker unavailable")); }
                    catch (Throwable ignored) {}
                }
                return true;
            }
        }
    };

    @Override public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override public void onDestroy() {
        replaceProvider(null);
        replaceProviderWatcher(null);
        super.onDestroy();
    }

    private boolean callerHasPackage(String expected) {
        String[] packages = getPackageManager().getPackagesForUid(Binder.getCallingUid());
        return FreeformBridgePolicy.packageListContains(packages, expected);
    }

    private void replaceProvider(IBinder next) {
        IBinder watcherToNotify;
        IBinder published;
        synchronized (lock) {
            if (provider == next) return;
            if (provider != null && providerDeath != null) {
                try { provider.unlinkToDeath(providerDeath, 0); } catch (Throwable ignored) {}
            }
            provider = null;
            providerDeath = null;
            if (next != null) {
                final IBinder registered = next;
                IBinder.DeathRecipient death = () -> {
                    IBinder watcher;
                    boolean changed = false;
                    synchronized (lock) {
                        if (provider == registered) {
                            provider = null;
                            providerDeath = null;
                            watcher = providerWatcher;
                            changed = true;
                        } else {
                            watcher = null;
                        }
                    }
                    if (changed) notifyProviderWatcher(watcher, null);
                };
                try {
                    next.linkToDeath(death, 0);
                    provider = next;
                    providerDeath = death;
                } catch (Throwable error) {
                    Log.w(TAG, "freeform provider already dead", error);
                }
            }
            watcherToNotify = providerWatcher;
            published = provider;
        }
        notifyProviderWatcher(watcherToNotify, published);
    }

    private void replaceProviderWatcher(IBinder next) {
        synchronized (lock) {
            if (providerWatcher == next) return;
            if (providerWatcher != null && providerWatcherDeath != null) {
                try { providerWatcher.unlinkToDeath(providerWatcherDeath, 0); }
                catch (Throwable ignored) {}
            }
            providerWatcher = null;
            providerWatcherDeath = null;
            if (next == null) return;
            final IBinder registered = next;
            IBinder.DeathRecipient death = () -> clearProviderWatcher(registered);
            try {
                next.linkToDeath(death, 0);
                providerWatcher = next;
                providerWatcherDeath = death;
            } catch (Throwable error) {
                Log.w(TAG, "freeform provider watcher already dead", error);
            }
        }
    }

    private void clearProviderWatcher(IBinder expected) {
        synchronized (lock) {
            if (expected != null && providerWatcher != expected) return;
            if (providerWatcher != null && providerWatcherDeath != null) {
                try { providerWatcher.unlinkToDeath(providerWatcherDeath, 0); }
                catch (Throwable ignored) {}
            }
            providerWatcher = null;
            providerWatcherDeath = null;
        }
    }

    private void notifyProviderWatcher(IBinder watcher, IBinder next) {
        if (watcher == null) return;
        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken(FreeformLeashProtocol.BROKER_PROVIDER_CALLBACK_DESCRIPTOR);
            data.writeStrongBinder(next);
            boolean accepted = watcher.transact(
                    FreeformLeashProtocol.TRANSACTION_PROVIDER_CHANGED,
                    data, null, IBinder.FLAG_ONEWAY);
            if (!accepted) clearProviderWatcher(watcher);
        } catch (Throwable error) {
            clearProviderWatcher(watcher);
        } finally {
            data.recycle();
        }
    }
}
