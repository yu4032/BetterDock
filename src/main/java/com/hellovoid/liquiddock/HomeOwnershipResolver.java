package com.hellovoid.liquiddock;

import android.content.Context;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Launcher-only asynchronous client for the production SystemUI HOME/APP baseline. */
final class HomeOwnershipResolver {
    interface Listener {
        void onBaseline(HomeOwnershipPolicy.Baseline baseline, String reason);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicLong requestIds = new AtomicLong();
    private final Object lock = new Object();
    private final LinkedHashMap<Long, Pending> pending = new LinkedHashMap<>();
    private final FreeformLeashBrokerClient brokerClient;
    private final Listener listener;

    private long generation;
    private int lastDisplayId = -1;
    private Runnable confirmationRunnable;

    HomeOwnershipResolver(Context context, Listener listener) {
        Context app = context.getApplicationContext();
        Context safeContext = app != null ? app : context;
        this.listener = listener;
        this.brokerClient = FreeformLeashBrokerClient.shared(
                safeContext, FreeformLeashBrokerClient.Role.LAUNCHER);
        this.brokerClient.setProviderListener(this::onProviderChanged);
        this.brokerClient.setDemanded(true);
    }

    void request(int displayId, String reason) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> request(displayId, reason));
            return;
        }
        long nextGeneration = ++generation;
        lastDisplayId = displayId;
        cancelConfirmation();
        clearPending();
        // A request boundary invalidates the previous ordinary baseline immediately.
        // This is the approved fail-closed behavior, not a transition animation heuristic.
        deliver(HomeOwnershipPolicy.Baseline.UNKNOWN, safeReason(reason) + "-pending");
        if (displayId < 0) return;
        submit(nextGeneration, displayId, safeReason(reason), false);
    }

    private void onProviderChanged(IBinder provider) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> onProviderChanged(provider));
            return;
        }
        if (provider == null) {
            generation++;
            cancelConfirmation();
            clearPending();
            deliver(HomeOwnershipPolicy.Baseline.UNKNOWN, "provider-unavailable");
            return;
        }
        if (lastDisplayId >= 0) request(lastDisplayId, "provider-ready");
    }

    private void submit(long requestGeneration, int displayId, String reason,
                        boolean confirmation) {
        if (requestGeneration != generation) return;
        IBinder provider = brokerClient.launcherProvider();
        if (provider == null) {
            deliverIfCurrent(requestGeneration, HomeOwnershipPolicy.Baseline.UNKNOWN,
                    reason + "-provider-unavailable");
            return;
        }

        long requestId = requestIds.incrementAndGet();
        Pending request = new Pending(requestId, requestGeneration, displayId, reason, confirmation);
        registerPending(request);

        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken(FreeformLeashProtocol.PROVIDER_DESCRIPTOR);
            data.writeInt(HomeOwnershipProtocol.VERSION);
            data.writeLong(requestId);
            data.writeInt(displayId);
            data.writeInt(confirmation ? 1 : 0);
            data.writeStrongBinder(callback);
            boolean accepted = provider.transact(
                    HomeOwnershipProtocol.TRANSACTION_REQUEST_BASELINE,
                    data, null, IBinder.FLAG_ONEWAY);
            if (!accepted) {
                removePending(requestId);
                deliverIfCurrent(requestGeneration, HomeOwnershipPolicy.Baseline.UNKNOWN,
                        reason + "-transaction-rejected");
                return;
            }
        } catch (RemoteException providerGone) {
            removePending(requestId);
            deliverIfCurrent(requestGeneration, HomeOwnershipPolicy.Baseline.UNKNOWN,
                    reason + "-provider-dead");
            return;
        } catch (Throwable error) {
            removePending(requestId);
            Api101Bridge.log("[DC] HOME ownership request unavailable", error);
            deliverIfCurrent(requestGeneration, HomeOwnershipPolicy.Baseline.UNKNOWN,
                    reason + "-request-error");
            return;
        } finally {
            data.recycle();
        }

        mainHandler.postDelayed(() -> expire(requestId), HomeOwnershipProtocol.REQUEST_TIMEOUT_MS);
    }

    private final Binder callback = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code != HomeOwnershipProtocol.TRANSACTION_BASELINE_RESULT) return false;
            try {
                data.enforceInterface(HomeOwnershipProtocol.CALLBACK_DESCRIPTOR);
                int version = data.readInt();
                long requestId = data.readLong();
                int status = data.readInt();
                int baselineWire = data.readInt();
                boolean retryRecommended = data.readInt() != 0;

                Pending request = removePending(requestId);
                if (request == null) return true;
                boolean valid = version == HomeOwnershipProtocol.VERSION
                        && (status == HomeOwnershipProtocol.STATUS_OK
                            || status == HomeOwnershipProtocol.STATUS_UNAVAILABLE
                            || status == HomeOwnershipProtocol.STATUS_STRUCTURE_FAILURE)
                        && HomeOwnershipProtocol.isKnownBaselineWireValue(baselineWire);
                HomeOwnershipPolicy.Baseline baseline = valid
                        ? HomeOwnershipProtocol.decodeBaseline(baselineWire)
                        : HomeOwnershipPolicy.Baseline.UNKNOWN;
                if (status != HomeOwnershipProtocol.STATUS_OK) {
                    baseline = HomeOwnershipPolicy.Baseline.UNKNOWN;
                    retryRecommended = false;
                }
                final HomeOwnershipPolicy.Baseline result = baseline;
                final boolean retry = valid && retryRecommended;
                mainHandler.post(() -> handleResult(request, result, retry, valid));
                return true;
            } catch (Throwable error) {
                Api101Bridge.log("[DC] HOME ownership callback malformed", error);
                return true;
            }
        }
    };

    private void handleResult(Pending request, HomeOwnershipPolicy.Baseline baseline,
                              boolean retryRecommended, boolean valid) {
        if (request.generation != generation) return;
        if (!valid) {
            deliver(HomeOwnershipPolicy.Baseline.UNKNOWN, request.reason + "-malformed");
            return;
        }
        if (baseline == HomeOwnershipPolicy.Baseline.UNKNOWN
                && retryRecommended && !request.confirmation) {
            deliver(HomeOwnershipPolicy.Baseline.UNKNOWN, request.reason + "-conflict");
            scheduleConfirmation(request);
            return;
        }
        deliver(baseline, request.reason + (request.confirmation ? "-confirmed" : ""));
    }

    private void scheduleConfirmation(Pending request) {
        cancelConfirmation();
        Runnable action = new Runnable() {
            @Override public void run() {
                if (confirmationRunnable != this) return;
                confirmationRunnable = null;
                if (request.generation != generation) return;
                submit(request.generation, request.displayId, request.reason, true);
            }
        };
        confirmationRunnable = action;
        mainHandler.postDelayed(action, HomeOwnershipProtocol.RECHECK_DELAY_MS);
    }

    private void cancelConfirmation() {
        Runnable action = confirmationRunnable;
        confirmationRunnable = null;
        if (action != null) mainHandler.removeCallbacks(action);
    }

    private void registerPending(Pending request) {
        synchronized (lock) {
            while (pending.size() >= HomeOwnershipProtocol.MAX_PENDING) {
                Iterator<Map.Entry<Long, Pending>> iterator = pending.entrySet().iterator();
                if (!iterator.hasNext()) break;
                iterator.next();
                iterator.remove();
            }
            pending.put(request.requestId, request);
        }
    }

    private Pending removePending(long requestId) {
        synchronized (lock) {
            return pending.remove(requestId);
        }
    }

    private void clearPending() {
        synchronized (lock) {
            pending.clear();
        }
    }

    private void expire(long requestId) {
        Pending request = removePending(requestId);
        if (request == null || request.generation != generation) return;
        deliver(HomeOwnershipPolicy.Baseline.UNKNOWN, request.reason + "-timeout");
    }

    private void deliverIfCurrent(long requestGeneration,
                                  HomeOwnershipPolicy.Baseline baseline, String reason) {
        if (requestGeneration == generation) deliver(baseline, reason);
    }

    private void deliver(HomeOwnershipPolicy.Baseline baseline, String reason) {
        if (listener != null) listener.onBaseline(baseline, reason);
    }

    private static String safeReason(String reason) {
        return reason == null || reason.isEmpty() ? "refresh" : reason;
    }

    private static final class Pending {
        final long requestId;
        final long generation;
        final int displayId;
        final String reason;
        final boolean confirmation;

        Pending(long requestId, long generation, int displayId,
                String reason, boolean confirmation) {
            this.requestId = requestId;
            this.generation = generation;
            this.displayId = displayId;
            this.reason = reason;
            this.confirmation = confirmation;
        }
    }
}
