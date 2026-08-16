package com.hellovoid.liquiddock;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

/** Process-local broker connection. Never blocks the Dock capture worker. */
final class FreeformLeashBrokerClient {
    enum Role { SYSTEM_UI, LAUNCHER }

    interface ProviderListener {
        void onProviderChanged(IBinder provider);
    }

    private static final String TAG = "LiquidDock";
    private static final long[] RECONNECT_MS = {250L, 500L, 1000L, 2000L, 5000L};
    private static volatile FreeformLeashBrokerClient sharedSystemUi;
    private static volatile FreeformLeashBrokerClient sharedLauncher;

    private final Context context;
    private final Role role;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean ioInFlight = new AtomicBoolean();
    private final Object lock = new Object();

    private volatile IBinder broker;
    private volatile IBinder launcherProvider;
    private volatile IBinder systemUiProvider;
    private volatile ProviderListener providerListener;
    private volatile boolean demanded;
    private boolean binding;
    private boolean bound;
    private int reconnectAttempt;

    private final Binder providerWatcherCallback = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code != FreeformLeashProtocol.TRANSACTION_PROVIDER_CHANGED) {
                return false;
            }
            try {
                data.enforceInterface(FreeformLeashProtocol.BROKER_PROVIDER_CALLBACK_DESCRIPTOR);
                IBinder next = data.readStrongBinder();
                if (next != null) setLauncherProvider(next);
                else clearLauncherProviderFromWatcher();
                return true;
            } catch (Throwable error) {
                clearLauncherProviderFromWatcher();
                return true;
            }
        }
    };

    static FreeformLeashBrokerClient shared(Context context, Role role) {
        FreeformLeashBrokerClient current = role == Role.SYSTEM_UI
                ? sharedSystemUi : sharedLauncher;
        if (current != null) return current;
        synchronized (FreeformLeashBrokerClient.class) {
            current = role == Role.SYSTEM_UI ? sharedSystemUi : sharedLauncher;
            if (current == null) {
                current = new FreeformLeashBrokerClient(context, role);
                if (role == Role.SYSTEM_UI) sharedSystemUi = current;
                else sharedLauncher = current;
            }
        }
        return current;
    }

    FreeformLeashBrokerClient(Context context, Role role) {
        Context app = context.getApplicationContext();
        this.context = app != null ? app : context;
        this.role = role;
    }

    void setProviderListener(ProviderListener listener) {
        providerListener = listener;
        if (listener != null && role == Role.LAUNCHER) {
            IBinder current = launcherProvider;
            mainHandler.post(() -> {
                if (providerListener == listener) listener.onProviderChanged(current);
            });
        }
    }

    void setDemanded(boolean value) {
        demanded = value;
        if (value) demandConnection();
    }

    void setSystemUiProvider(IBinder provider) {
        systemUiProvider = provider;
        demanded = provider != null;
        if (provider != null) demandConnection();
    }

    IBinder launcherProvider() {
        IBinder current = launcherProvider;
        if (current == null && demanded) {
            demandConnection();
            refreshLauncherProviderAsync();
        }
        return current;
    }

    private void notifyProviderChanged(IBinder provider) {
        ProviderListener listener = providerListener;
        if (listener == null) return;
        mainHandler.post(() -> {
            if (providerListener == listener) listener.onProviderChanged(provider);
        });
    }

    private void demandConnection() {
        mainHandler.post(() -> {
            synchronized (lock) {
                if (!demanded || broker != null || binding || bound) return;
                binding = true;
                // Reserve the binding before calling into Context so duplicate delayed
                // reconnect callbacks cannot increment the bind count.
                bound = true;
            }
            try {
                Intent intent = new Intent();
                intent.setClassName(FreeformLeashProtocol.MODULE_PACKAGE,
                        FreeformLeashBrokerService.class.getName());
                boolean ok = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
                synchronized (lock) {
                    binding = false;
                    if (!ok) bound = false;
                }
                if (!ok) scheduleReconnect();
            } catch (Throwable error) {
                synchronized (lock) {
                    binding = false;
                    bound = false;
                }
                Log.w(TAG, "freeform broker bind unavailable", error);
                scheduleReconnect();
            }
        });
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (lock) {
                binding = false;
                bound = true;
                broker = service;
                reconnectAttempt = 0;
            }
            if (role == Role.SYSTEM_UI) registerSystemUiProviderAsync();
            else registerProviderWatcherAsync();
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            // BIND_AUTO_CREATE remains active. Android will reconnect this same binding;
            // issuing bindService() again here would leak an additional bind count.
            clearBrokerKeepBinding();
        }

        @Override public void onBindingDied(ComponentName name) {
            clearBrokerKeepBinding();
            unbindAndReconnect();
        }

        @Override public void onNullBinding(ComponentName name) {
            clearBrokerKeepBinding();
            unbindAndReconnect();
        }
    };

    private void clearBrokerKeepBinding() {
        broker = null;
        if (role == Role.LAUNCHER) clearLauncherProviderFromWatcher();
    }

    private void unbindAndReconnect() {
        mainHandler.post(() -> {
            boolean shouldUnbind;
            synchronized (lock) {
                shouldUnbind = bound;
                bound = false;
                binding = false;
                broker = null;
            }
            if (shouldUnbind) {
                try { context.unbindService(connection); } catch (Throwable ignored) {}
            }
            if (role == Role.LAUNCHER) clearLauncherProviderFromWatcher();
            scheduleReconnect();
        });
    }

    private void scheduleReconnect() {
        if (!demanded) return;
        int index;
        synchronized (lock) {
            index = Math.min(reconnectAttempt++, RECONNECT_MS.length - 1);
        }
        mainHandler.postDelayed(this::demandConnection, RECONNECT_MS[index]);
    }

    private void registerSystemUiProviderAsync() {
        if (role != Role.SYSTEM_UI || systemUiProvider == null) return;
        runIo(() -> {
            IBinder b = broker;
            IBinder provider = systemUiProvider;
            if (b == null || provider == null) return;
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(FreeformLeashProtocol.BROKER_DESCRIPTOR);
                data.writeStrongBinder(provider);
                b.transact(FreeformLeashProtocol.TRANSACTION_REGISTER_PROVIDER,
                        data, reply, 0);
                reply.readException();
            } catch (Throwable error) {
                Log.w(TAG, "freeform provider registration unavailable", error);
                unbindAndReconnect();
            } finally {
                reply.recycle();
                data.recycle();
            }
        });
    }

    private void registerProviderWatcherAsync() {
        if (role != Role.LAUNCHER) return;
        runIo(() -> {
            IBinder b = broker;
            if (b == null) return;
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            boolean accepted = false;
            try {
                data.writeInterfaceToken(FreeformLeashProtocol.BROKER_DESCRIPTOR);
                data.writeStrongBinder(providerWatcherCallback);
                accepted = b.transact(FreeformLeashProtocol.TRANSACTION_WATCH_PROVIDER,
                        data, reply, 0);
                if (accepted) reply.readException();
            } catch (Throwable error) {
                Log.w(TAG, "freeform provider watcher registration unavailable", error);
                unbindAndReconnect();
                return;
            } finally {
                reply.recycle();
                data.recycle();
            }
            // Mixed generations may not understand the watcher transaction. Keep the old
            // GET_PROVIDER path as a one-shot compatibility fallback; current generations
            // recover event-driven through providerWatcherCallback.
            if (!accepted) mainHandler.post(FreeformLeashBrokerClient.this::refreshLauncherProviderAsync);
        });
    }

    private void refreshLauncherProviderAsync() {
        if (role != Role.LAUNCHER || launcherProvider != null) return;
        runIo(() -> {
            IBinder b = broker;
            if (b == null) return;
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(FreeformLeashProtocol.BROKER_DESCRIPTOR);
                b.transact(FreeformLeashProtocol.TRANSACTION_GET_PROVIDER, data, reply, 0);
                reply.readException();
                IBinder next = reply.readStrongBinder();
                if (next != null) setLauncherProvider(next);
            } catch (Throwable error) {
                Log.w(TAG, "freeform provider discovery unavailable", error);
                unbindAndReconnect();
            } finally {
                reply.recycle();
                data.recycle();
            }
        });
    }

    private void setLauncherProvider(IBinder next) {
        if (next == null || launcherProvider == next) return;
        try {
            next.linkToDeath(() -> clearLauncherProvider(next), 0);
            launcherProvider = next;
            notifyProviderChanged(next);
        } catch (Throwable error) {
            clearLauncherProvider(next);
        }
    }

    private void clearLauncherProviderFromWatcher() {
        IBinder current = launcherProvider;
        if (current == null) return;
        launcherProvider = null;
        notifyProviderChanged(null);
    }

    private void clearLauncherProvider(IBinder expected) {
        IBinder current = launcherProvider;
        if (expected != null && current != expected) return;
        if (current != null) {
            launcherProvider = null;
            notifyProviderChanged(null);
        }
        if (demanded && broker != null) refreshLauncherProviderAsync();
    }

    private void runIo(Runnable action) {
        if (!ioInFlight.compareAndSet(false, true)) return;
        Thread thread = new Thread(() -> {
            try { action.run(); }
            catch (Throwable error) { Log.w(TAG, "freeform broker I/O failed", error); }
            finally { ioInFlight.set(false); }
        }, "LiquidDock-FreeformBroker");
        thread.setDaemon(true);
        thread.start();
    }
}
