package com.hellovoid.liquiddock;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

/** Process-local broker connection. Never blocks the Dock capture worker. */
final class FreeformLeashBrokerClient {
    enum Role { SYSTEM_UI, LAUNCHER }

    private static final String TAG = "LiquidDock";
    private static final long[] RECONNECT_MS = {250L, 500L, 1000L, 2000L, 5000L};

    private final Context context;
    private final Role role;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean ioInFlight = new AtomicBoolean();
    private final Object lock = new Object();

    private volatile IBinder broker;
    private volatile IBinder launcherProvider;
    private volatile IBinder systemUiProvider;
    private volatile boolean demanded;
    private boolean binding;
    private int reconnectAttempt;

    FreeformLeashBrokerClient(Context context, Role role) {
        Context app = context.getApplicationContext();
        this.context = app != null ? app : context;
        this.role = role;
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

    private void demandConnection() {
        mainHandler.post(() -> {
            synchronized (lock) {
                if (!demanded || broker != null || binding) return;
                binding = true;
            }
            try {
                Intent intent = new Intent();
                intent.setClassName(FreeformLeashProtocol.MODULE_PACKAGE,
                        FreeformLeashProtocol.BROKER_SERVICE);
                boolean ok = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
                if (!ok) {
                    synchronized (lock) { binding = false; }
                    scheduleReconnect();
                }
            } catch (Throwable error) {
                synchronized (lock) { binding = false; }
                Log.w(TAG, "freeform broker bind unavailable", error);
                scheduleReconnect();
            }
        });
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (lock) {
                binding = false;
                broker = service;
                reconnectAttempt = 0;
            }
            try {
                service.linkToDeath(() -> {
                    clearBroker(service);
                    scheduleReconnect();
                }, 0);
            } catch (Throwable error) {
                clearBroker(service);
                scheduleReconnect();
                return;
            }
            if (role == Role.SYSTEM_UI) registerSystemUiProviderAsync();
            else refreshLauncherProviderAsync();
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            clearBroker(null);
            scheduleReconnect();
        }

        @Override public void onBindingDied(ComponentName name) {
            clearBroker(null);
            scheduleReconnect();
        }

        @Override public void onNullBinding(ComponentName name) {
            clearBroker(null);
            scheduleReconnect();
        }
    };

    private void clearBroker(IBinder expected) {
        synchronized (lock) {
            if (expected == null || broker == expected) broker = null;
            binding = false;
        }
        if (role == Role.LAUNCHER) clearLauncherProvider(null);
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
                clearBroker(b);
                scheduleReconnect();
            } finally {
                reply.recycle();
                data.recycle();
            }
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
                clearBroker(b);
                scheduleReconnect();
            } finally {
                reply.recycle();
                data.recycle();
            }
        });
    }

    private void setLauncherProvider(IBinder next) {
        if (next == null) return;
        try {
            next.linkToDeath(() -> clearLauncherProvider(next), 0);
            launcherProvider = next;
        } catch (Throwable error) {
            clearLauncherProvider(next);
        }
    }

    private void clearLauncherProvider(IBinder expected) {
        IBinder current = launcherProvider;
        if (expected == null || current == expected) launcherProvider = null;
        if (demanded) refreshLauncherProviderAsync();
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
