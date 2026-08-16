package com.hellovoid.liquiddock;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;
import android.view.SurfaceControl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Resolves all visible freeform task IDs to parcel-copied WMShell task leashes. */
final class FreeformTaskLeashResolver {
    private static final String TAG = "LiquidDock";
    private static final int MAX_RUNNING_TASKS = 32;

    private final Context context;
    private final FreeformLeashBrokerClient brokerClient;
    private final FreeformBridgePolicy.CircuitBreaker breaker =
            new FreeformBridgePolicy.CircuitBreaker();
    private final AtomicLong requestIds = new AtomicLong();

    FreeformTaskLeashResolver(Context context) {
        Context app = context.getApplicationContext();
        this.context = app != null ? app : context;
        this.brokerClient = new FreeformLeashBrokerClient(
                this.context, FreeformLeashBrokerClient.Role.LAUNCHER);
    }

    void setProviderDemanded(boolean demanded) {
        brokerClient.setDemanded(demanded);
    }

    boolean isProviderReady() {
        return !breaker.isDisabled() && brokerClient.launcherProvider() != null;
    }

    Resolution resolveVisibleLeashes(int displayId) {
        int[] taskIds = visibleFreeformTaskIds(displayId);
        if (taskIds == null) {
            brokerClient.setDemanded(true);
            return Resolution.unavailable(true);
        }
        if (taskIds.length == 0) {
            brokerClient.setDemanded(false);
            return Resolution.noFreeform();
        }
        brokerClient.setDemanded(true);
        if (breaker.isDisabled()) return Resolution.unavailable(true);

        IBinder provider = brokerClient.launcherProvider();
        if (provider == null) return Resolution.unavailable(true);

        long requestId = requestIds.incrementAndGet();
        RequestState state = new RequestState(requestId, taskIds);
        Parcel request = Parcel.obtain();
        try {
            request.writeInterfaceToken(FreeformLeashProtocol.PROVIDER_DESCRIPTOR);
            request.writeLong(requestId);
            request.writeInt(taskIds.length);
            for (int taskId : taskIds) request.writeInt(taskId);
            request.writeStrongBinder(state.callback);
            boolean accepted = provider.transact(
                    FreeformLeashProtocol.TRANSACTION_REQUEST_LEASHES,
                    request, null, IBinder.FLAG_ONEWAY);
            if (!accepted) {
                breaker.recordInfrastructureFailure();
                state.expire();
                return Resolution.unavailable(true);
            }
        } catch (RemoteException remoteGone) {
            // SystemUI/provider process death is normal lifecycle. Broker death handling will
            // rediscover a replacement provider; do not poison the process-level breaker.
            state.expire();
            return Resolution.unavailable(true);
        } catch (Throwable error) {
            breaker.recordInfrastructureFailure();
            state.expire();
            Log.w(TAG, "freeform leash request failed", error);
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

    /** Returns null when task enumeration itself failed; callers must fail closed. */
    private int[] visibleFreeformTaskIds(int displayId) {
        List<Integer> ids = new ArrayList<>();
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return null;
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(MAX_RUNNING_TASKS);
            if (tasks == null) return null;
            for (ActivityManager.RunningTaskInfo task : tasks) {
                if (task == null || task.displayId != displayId) continue;
                if (!FreeformCapturePolicy.shouldExclude(windowingMode(task), isVisible(task))) continue;
                ids.add(task.taskId);
            }
        } catch (Throwable error) {
            Log.w(TAG, "visible freeform task scan failed", error);
            return null;
        }
        int[] raw = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) raw[i] = ids.get(i);
        return FreeformBridgePolicy.deduplicateTaskIds(raw);
    }

    private static int windowingMode(ActivityManager.RunningTaskInfo task) {
        try {
            Object value = HookUtil.invoke(task, "getWindowingMode");
            return value instanceof Integer ? (Integer) value : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static boolean isVisible(ActivityManager.RunningTaskInfo task) {
        try {
            java.lang.reflect.Field field = task.getClass().getField("isVisible");
            Object value = field.get(task);
            if (value instanceof Boolean) return (Boolean) value;
        } catch (Throwable ignored) {}
        try {
            Object value = HookUtil.invoke(task, "isVisible");
            if (value instanceof Boolean) return (Boolean) value;
        } catch (Throwable ignored) {}
        return true;
    }

    private static void release(SurfaceControl surface) {
        if (surface == null) return;
        try { surface.release(); } catch (Throwable ignored) {}
    }

    private static void releaseAll(SurfaceControl[] surfaces) {
        if (surfaces == null) return;
        for (SurfaceControl surface : surfaces) release(surface);
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
        final int[] requestedTaskIds;
        final CountDownLatch latch = new CountDownLatch(1);
        final Map<Integer, SurfaceControl> received = new LinkedHashMap<>();
        boolean expired;
        boolean malformed;

        final Binder callback = new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                if (code != FreeformLeashProtocol.TRANSACTION_LEASH_RESULT) return false;
                ArrayList<SurfaceControl> parsed = new ArrayList<>();
                try {
                    data.enforceInterface(FreeformLeashProtocol.CALLBACK_DESCRIPTOR);
                    long responseId = data.readLong();
                    int count = data.readInt();
                    LinkedHashMap<Integer, SurfaceControl> successful = new LinkedHashMap<>();
                    boolean bad = responseId != requestId || count < 0
                            || count > FreeformLeashProtocol.MAX_TASKS;
                    int bounded = Math.max(0, Math.min(count, FreeformLeashProtocol.MAX_TASKS));
                    for (int i = 0; i < bounded; i++) {
                        int taskId = data.readInt();
                        int status = data.readInt();
                        SurfaceControl surface = data.readTypedObject(SurfaceControl.CREATOR);
                        if (surface != null) parsed.add(surface);
                        if (status == FreeformLeashProtocol.STATUS_OK
                                && surface != null && surface.isValid()) {
                            SurfaceControl old = successful.put(taskId, surface);
                            if (old != null && old != surface) {
                                parsed.remove(old);
                                release(old);
                                bad = true;
                            }
                        } else {
                            if (status != FreeformLeashProtocol.STATUS_UNAVAILABLE
                                    && status != FreeformLeashProtocol.STATUS_INFRASTRUCTURE_FAILURE) {
                                bad = true;
                            }
                            if (surface != null) bad = true;
                        }
                    }
                    synchronized (RequestState.this) {
                        if (expired || responseId != requestId) {
                            for (SurfaceControl surface : parsed) release(surface);
                            return true;
                        }
                        malformed = bad;
                        received.putAll(successful);
                        parsed.clear();
                        latch.countDown();
                    }
                    return true;
                } catch (Throwable error) {
                    for (SurfaceControl surface : parsed) release(surface);
                    synchronized (RequestState.this) {
                        malformed = true;
                        if (!expired) latch.countDown();
                    }
                    return true;
                }
            }
        };

        RequestState(long requestId, int[] requestedTaskIds) {
            this.requestId = requestId;
            this.requestedTaskIds = requestedTaskIds.clone();
        }

        synchronized boolean wasMalformed() {
            return malformed;
        }

        synchronized void expire() {
            if (expired) return;
            expired = true;
            for (SurfaceControl surface : received.values()) release(surface);
            received.clear();
        }

        synchronized Resolution takeResolution() {
            if (expired || malformed || received.size() != requestedTaskIds.length) {
                expire();
                return Resolution.unavailable(true);
            }
            SurfaceControl[] surfaces = new SurfaceControl[requestedTaskIds.length];
            for (int i = 0; i < requestedTaskIds.length; i++) {
                SurfaceControl surface = received.remove(requestedTaskIds[i]);
                if (surface == null || !surface.isValid()) {
                    if (surface != null) release(surface);
                    releaseAll(surfaces);
                    expire();
                    return Resolution.unavailable(true);
                }
                surfaces[i] = surface;
            }
            if (!received.isEmpty()) {
                for (SurfaceControl surface : received.values()) release(surface);
                received.clear();
                releaseAll(surfaces);
                expired = true;
                return Resolution.unavailable(true);
            }
            expired = true;
            return new Resolution(true, true, surfaces);
        }
    }
}
