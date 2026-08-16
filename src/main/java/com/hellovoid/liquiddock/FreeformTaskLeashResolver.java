package com.hellovoid.liquiddock;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;
import android.view.SurfaceControl;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Requests a display-scoped freeform leash snapshot from the existing SystemUI WMShell state. */
final class FreeformTaskLeashResolver {
    private static final String TAG = "LiquidDock";

    private final FreeformLeashBrokerClient brokerClient;
    private final FreeformBridgePolicy.CircuitBreaker breaker =
            new FreeformBridgePolicy.CircuitBreaker();
    private final AtomicLong requestIds = new AtomicLong();

    FreeformTaskLeashResolver(Context context) {
        Context app = context.getApplicationContext();
        Context safeContext = app != null ? app : context;
        this.brokerClient = FreeformLeashBrokerClient.shared(
                safeContext, FreeformLeashBrokerClient.Role.LAUNCHER);
    }

    void setProviderDemanded(boolean demanded) {
        brokerClient.setDemanded(demanded);
    }

    boolean isProviderReady() {
        return !breaker.isDisabled() && brokerClient.launcherProvider() != null;
    }

    Resolution resolveVisibleLeashes(int displayId) {
        brokerClient.setDemanded(true);
        if (displayId < 0 || breaker.isDisabled()) return Resolution.unavailable(true);

        IBinder provider = brokerClient.launcherProvider();
        if (provider == null) return Resolution.unavailable(true);

        long requestId = requestIds.incrementAndGet();
        RequestState state = new RequestState(requestId);
        Parcel request = Parcel.obtain();
        try {
            request.writeInterfaceToken(FreeformLeashProtocol.PROVIDER_DESCRIPTOR);
            request.writeLong(requestId);
            request.writeInt(displayId);
            request.writeStrongBinder(state.callback);
            boolean accepted = provider.transact(
                    FreeformLeashProtocol.TRANSACTION_REQUEST_VISIBLE_LEASH_SNAPSHOT,
                    request, null, IBinder.FLAG_ONEWAY);
            if (!accepted) {
                // Mixed module generations are a normal restart window. Stay fail-closed but
                // do not poison this Launcher process; a later SystemUI restart can recover.
                state.expire();
                return Resolution.unavailable(true);
            }
        } catch (RemoteException remoteGone) {
            // SystemUI/provider death is normal lifecycle. Broker death handling will
            // rediscover a replacement provider; do not poison the process-level breaker.
            state.expire();
            return Resolution.unavailable(true);
        } catch (Throwable error) {
            breaker.recordInfrastructureFailure();
            state.expire();
            Log.w(TAG, "freeform snapshot request failed", error);
            return Resolution.unavailable(true);
        } finally {
            request.recycle();
        }

        try {
            if (!state.latch.await(FreeformLeashProtocol.REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                state.expire();
                return Resolution.unavailable(true);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            state.expire();
            return Resolution.unavailable(true);
        }

        Resolution result = state.takeResolution();
        if (state.wasMalformed()) breaker.recordInfrastructureFailure();
        return result;
    }

    private static void release(SurfaceControl surface) {
        if (surface == null) return;
        try { surface.release(); } catch (Throwable ignored) {}
    }

    private static void releaseAll(SurfaceControl[] surfaces) {
        if (surfaces == null) return;
        for (SurfaceControl surface : surfaces) release(surface);
    }

    private static void releaseAll(ArrayList<SurfaceControl> surfaces) {
        if (surfaces == null) return;
        for (SurfaceControl surface : surfaces) release(surface);
        surfaces.clear();
    }

    static final class Resolution implements AutoCloseable {
        private final boolean visibleFreeform;
        private final boolean safe;
        private SurfaceControl[] ownedRemoteLeashes;
        private boolean closed;

        private Resolution(boolean visibleFreeform, boolean safe, SurfaceControl[] owned) {
            this.visibleFreeform = visibleFreeform;
            this.safe = safe;
            this.ownedRemoteLeashes = owned != null ? owned : new SurfaceControl[0];
        }

        static Resolution noFreeform() {
            return new Resolution(false, true, null);
        }

        static Resolution unavailable(boolean visibleFreeform) {
            return new Resolution(visibleFreeform, !visibleFreeform, null);
        }

        boolean hasVisibleFreeformTasks() { return visibleFreeform; }
        boolean isSafe() { return safe; }

        synchronized SurfaceControl[] borrowedRemoteLeashes() {
            return ownedRemoteLeashes.clone();
        }

        @Override public synchronized void close() {
            if (closed) return;
            closed = true;
            releaseAll(ownedRemoteLeashes);
            ownedRemoteLeashes = new SurfaceControl[0];
        }
    }

    private static final class RequestState {
        final long requestId;
        final CountDownLatch latch = new CountDownLatch(1);
        final ArrayList<SurfaceControl> received = new ArrayList<>();
        int responseStatus = FreeformLeashProtocol.STATUS_UNAVAILABLE;
        boolean expired;
        boolean malformed;

        final Binder callback = new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                if (code != FreeformLeashProtocol.TRANSACTION_VISIBLE_LEASH_SNAPSHOT_RESULT) {
                    return false;
                }
                ArrayList<SurfaceControl> parsed = new ArrayList<>();
                try {
                    data.enforceInterface(FreeformLeashProtocol.CALLBACK_DESCRIPTOR);
                    long responseId = data.readLong();
                    int status = data.readInt();
                    int count = data.readInt();
                    boolean bad = responseId != requestId
                            || count < 0 || count > FreeformLeashProtocol.MAX_TASKS;
                    int bounded = Math.max(0, Math.min(count, FreeformLeashProtocol.MAX_TASKS));

                    if (status != FreeformLeashProtocol.STATUS_OK
                            && status != FreeformLeashProtocol.STATUS_UNAVAILABLE
                            && status != FreeformLeashProtocol.STATUS_INFRASTRUCTURE_FAILURE) {
                        bad = true;
                    }
                    if (status != FreeformLeashProtocol.STATUS_OK && count != 0) {
                        bad = true;
                    }

                    for (int i = 0; i < bounded; i++) {
                        SurfaceControl surface = data.readTypedObject(SurfaceControl.CREATOR);
                        if (status == FreeformLeashProtocol.STATUS_OK
                                && surface != null && surface.isValid()) {
                            parsed.add(surface);
                        } else {
                            if (surface != null) release(surface);
                            bad = true;
                        }
                    }

                    synchronized (RequestState.this) {
                        if (expired || responseId != requestId) {
                            releaseAll(parsed);
                            return true;
                        }
                        malformed = bad;
                        responseStatus = status;
                        if (!bad && status == FreeformLeashProtocol.STATUS_OK) {
                            received.addAll(parsed);
                            parsed.clear();
                        }
                        latch.countDown();
                    }
                    releaseAll(parsed);
                    return true;
                } catch (Throwable error) {
                    releaseAll(parsed);
                    synchronized (RequestState.this) {
                        malformed = true;
                        if (!expired) latch.countDown();
                    }
                    return true;
                }
            }
        };

        RequestState(long requestId) {
            this.requestId = requestId;
        }

        synchronized boolean wasMalformed() {
            return malformed;
        }

        synchronized void expire() {
            if (expired) return;
            expired = true;
            releaseAll(received);
        }

        synchronized Resolution takeResolution() {
            if (expired || malformed) {
                expire();
                return Resolution.unavailable(true);
            }
            if (responseStatus == FreeformLeashProtocol.STATUS_UNAVAILABLE
                    || responseStatus == FreeformLeashProtocol.STATUS_INFRASTRUCTURE_FAILURE) {
                expire();
                return Resolution.unavailable(true);
            }
            if (responseStatus != FreeformLeashProtocol.STATUS_OK) {
                malformed = true;
                expire();
                return Resolution.unavailable(true);
            }
            if (received.isEmpty()) {
                expired = true;
                return Resolution.noFreeform();
            }

            SurfaceControl[] surfaces = received.toArray(new SurfaceControl[0]);
            received.clear();
            for (SurfaceControl surface : surfaces) {
                if (surface == null || !surface.isValid()) {
                    releaseAll(surfaces);
                    expired = true;
                    return Resolution.unavailable(true);
                }
            }
            expired = true;
            return new Resolution(true, true, surfaces);
        }
    }
}
