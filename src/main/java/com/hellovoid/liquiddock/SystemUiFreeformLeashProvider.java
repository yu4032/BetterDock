package com.hellovoid.liquiddock;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.SparseArray;
import android.view.SurfaceControl;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Passive SystemUI-side view of HyperOS' existing FreeformTaskListener.
 * No task lifecycle or SurfaceControl ownership is taken over by LiquidDock.
 */
final class SystemUiFreeformLeashProvider {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final FreeformBridgePolicy.CircuitBreaker BREAKER =
            new FreeformBridgePolicy.CircuitBreaker();
    private static final SurfaceControl[] NO_SURFACES = new SurfaceControl[0];

    private static volatile ListenerState currentState;
    private static volatile FreeformLeashBrokerClient brokerClient;

    private static Field contextField;
    private static Field organizerField;
    private static Field tasksField;
    private static Field taskInfoField;
    private static Field leashField;

    private SystemUiFreeformLeashProvider() {}

    static void install(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> listenerClass = Class.forName(
                    "com.android.wm.shell.freeform.FreeformTaskListener", false, classLoader);
            Class<?> stateClass = Class.forName(
                    "com.android.wm.shell.freeform.FreeformTaskListener$State", false, classLoader);
            contextField = HookUtil.findField(listenerClass, "mContext");
            organizerField = HookUtil.findField(listenerClass, "mShellTaskOrganizer");
            tasksField = HookUtil.findField(listenerClass, "mTasks");
            taskInfoField = HookUtil.findField(stateClass, "mTaskInfo");
            leashField = HookUtil.findField(stateClass, "mLeash");
            if (!SparseArray.class.isAssignableFrom(tasksField.getType())) {
                throw new IllegalStateException("FreeformTaskListener#mTasks is not SparseArray");
            }
            if (!SurfaceControl.class.isAssignableFrom(leashField.getType())) {
                throw new IllegalStateException("FreeformTaskListener.State#mLeash is not SurfaceControl");
            }
            Constructor<?>[] constructors = listenerClass.getDeclaredConstructors();
            if (constructors.length == 0) throw new IllegalStateException("no FreeformTaskListener ctor");
            for (Constructor<?> ctor : constructors) {
                HookUtil.hook(ctor, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    try {
                        observeConstructedListener(chain.getThisObject());
                    } catch (Throwable error) {
                        recordInfrastructureFailure("observe FreeformTaskListener", error);
                    }
                    return result;
                });
            }
            Api101Bridge.log("[DC] SystemUI freeform leash provider hook installed");
        } catch (Throwable error) {
            BREAKER.disableForProcess();
            Api101Bridge.log("[DC] SystemUI freeform leash provider disabled", error);
        }
    }

    private static void observeConstructedListener(Object listener) throws Exception {
        if (listener == null || BREAKER.isDisabled()) return;
        Object contextValue = contextField.get(listener);
        if (!(contextValue instanceof Context)) {
            throw new IllegalStateException("FreeformTaskListener#mContext unavailable");
        }
        Context context = (Context) contextValue;
        Context application = context.getApplicationContext();
        if (application != null) context = application;

        Object organizer = organizerField.get(listener);
        if (organizer == null) {
            throw new IllegalStateException("FreeformTaskListener#mShellTaskOrganizer unavailable");
        }
        Method getExecutor = HookUtil.findMethodBestMatch(
                organizer.getClass(), "getExecutor", new Object[0], false);
        Object executorValue = getExecutor.invoke(organizer);
        if (!(executorValue instanceof Executor)) {
            throw new IllegalStateException("ShellTaskOrganizer executor unavailable");
        }

        currentState = new ListenerState(listener, (Executor) executorValue, context);

        FreeformLeashBrokerClient client = brokerClient;
        if (client == null) {
            synchronized (SystemUiFreeformLeashProvider.class) {
                client = brokerClient;
                if (client == null) {
                    client = new FreeformLeashBrokerClient(
                            context, FreeformLeashBrokerClient.Role.SYSTEM_UI);
                    brokerClient = client;
                }
            }
        }
        client.setSystemUiProvider(PROVIDER_BINDER);
    }

    /** Read-only diagnostic access to the executor that owns ShellTaskOrganizer callbacks. */
    static Executor taskStateExecutorForDiagnostics() {
        ListenerState state = currentState;
        return state != null ? state.executor : null;
    }

    private static final Binder PROVIDER_BINDER = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            ListenerState state = currentState;
            if (SystemUiHomeOwnershipShadow.handles(code)) {
                return SystemUiHomeOwnershipShadow.handleTransaction(code, data);
            }
            if (code != FreeformLeashProtocol.TRANSACTION_REQUEST_VISIBLE_LEASH_SNAPSHOT) {
                return super.onTransact(code, data, reply, flags);
            }
            if (state == null || !callerIsLauncher(state.context)) return true;
            try {
                data.enforceInterface(FreeformLeashProtocol.PROVIDER_DESCRIPTOR);
                long requestId = data.readLong();
                int displayId = data.readInt();
                IBinder callback = data.readStrongBinder();
                if (callback == null) return true;
                if (displayId < 0) {
                    sendSnapshotResult(callback, requestId,
                            FreeformLeashProtocol.STATUS_INFRASTRUCTURE_FAILURE, NO_SURFACES);
                    return true;
                }
                if (BREAKER.isDisabled()) {
                    sendSnapshotResult(callback, requestId,
                            FreeformLeashProtocol.STATUS_INFRASTRUCTURE_FAILURE, NO_SURFACES);
                    return true;
                }
                if (state.listener.get() == null) {
                    sendSnapshotResult(callback, requestId,
                            FreeformLeashProtocol.STATUS_UNAVAILABLE, NO_SURFACES);
                    return true;
                }
                try {
                    state.executor.execute(() -> {
                        try {
                            resolveSnapshotOnShellExecutor(state, callback, requestId, displayId);
                        } catch (Throwable error) {
                            recordInfrastructureFailure("resolve freeform snapshot", error);
                            sendSnapshotResult(callback, requestId,
                                    FreeformLeashProtocol.STATUS_INFRASTRUCTURE_FAILURE,
                                    NO_SURFACES);
                        }
                    });
                } catch (Throwable error) {
                    recordInfrastructureFailure("schedule freeform snapshot lookup", error);
                    sendSnapshotResult(callback, requestId,
                            FreeformLeashProtocol.STATUS_INFRASTRUCTURE_FAILURE, NO_SURFACES);
                }
                return true;
            } catch (SecurityException | IllegalArgumentException malformedRequest) {
                return true;
            } catch (Throwable error) {
                recordInfrastructureFailure("SystemUI provider transaction", error);
                return true;
            }
        }
    };

    private static void resolveSnapshotOnShellExecutor(ListenerState state, IBinder callback,
                                                       long requestId, int displayId)
            throws Exception {
        Object listener = state.listener.get();
        if (listener == null) {
            sendSnapshotResult(callback, requestId,
                    FreeformLeashProtocol.STATUS_UNAVAILABLE, NO_SURFACES);
            return;
        }
        Object tasksValue = tasksField.get(listener);
        if (!(tasksValue instanceof SparseArray)) {
            throw new IllegalStateException("mTasks changed type");
        }

        SparseArray<?> tasks = (SparseArray<?>) tasksValue;
        ArrayList<SurfaceControl> included = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            Object taskState = tasks.valueAt(i);
            if (taskState == null) continue;

            Object taskInfo = taskInfoField.get(taskState);
            Integer taskDisplayId = reflectedDisplayId(taskInfo);
            Boolean visible = reflectedVisibility(taskInfo);
            if (!FreeformBridgePolicy.shouldIncludeFreeformCandidate(
                    taskDisplayId, visible, displayId)) {
                continue;
            }

            Object leashValue = leashField.get(taskState);
            if (!(leashValue instanceof SurfaceControl)
                    || !((SurfaceControl) leashValue).isValid()) {
                throw new IllegalStateException("candidate freeform leash unavailable");
            }
            included.add((SurfaceControl) leashValue);
            if (included.size() > FreeformLeashProtocol.MAX_TASKS) {
                throw new IllegalStateException("too many visible freeform tasks");
            }
        }

        sendSnapshotResult(callback, requestId, FreeformLeashProtocol.STATUS_OK,
                included.toArray(new SurfaceControl[0]));
    }

    private static Integer reflectedDisplayId(Object taskInfo) {
        if (taskInfo == null) return null;
        try {
            Field field = HookUtil.findField(taskInfo.getClass(), "displayId");
            Object value = field.get(taskInfo);
            if (value instanceof Integer) return (Integer) value;
        } catch (Throwable ignored) {}
        try {
            Object value = HookUtil.invoke(taskInfo, "getDisplayId");
            if (value instanceof Integer) return (Integer) value;
        } catch (Throwable ignored) {}
        return null;
    }

    private static Boolean reflectedVisibility(Object taskInfo) {
        if (taskInfo == null) return null;
        try {
            Field field = HookUtil.findField(taskInfo.getClass(), "isVisible");
            Object value = field.get(taskInfo);
            if (value instanceof Boolean) return (Boolean) value;
        } catch (Throwable ignored) {}
        try {
            Object value = HookUtil.invoke(taskInfo, "isVisible");
            if (value instanceof Boolean) return (Boolean) value;
        } catch (Throwable ignored) {}
        return null;
    }

    private static void sendSnapshotResult(IBinder callback, long requestId, int overallStatus,
                                           SurfaceControl[] surfaces) {
        if (callback == null) return;
        if (surfaces == null) surfaces = NO_SURFACES;
        Parcel out = Parcel.obtain();
        try {
            out.writeInterfaceToken(FreeformLeashProtocol.CALLBACK_DESCRIPTOR);
            out.writeLong(requestId);
            out.writeInt(overallStatus);
            out.writeInt(surfaces.length);
            for (int i = 0; i < surfaces.length; i++) {
                out.writeTypedObject(surfaces[i], 0);
            }
            callback.transact(FreeformLeashProtocol.TRANSACTION_VISIBLE_LEASH_SNAPSHOT_RESULT,
                    out, null, IBinder.FLAG_ONEWAY);
        } catch (RemoteException remoteGone) {
            // Normal Launcher process death; never count against SystemUI bridge health.
        } catch (Throwable error) {
            recordInfrastructureFailure("send freeform snapshot callback", error);
        } finally {
            out.recycle();
        }
    }

    private static boolean callerIsLauncher(Context context) {
        try {
            String[] packages = context.getPackageManager().getPackagesForUid(Binder.getCallingUid());
            return FreeformBridgePolicy.packageListContains(
                    packages, FreeformLeashProtocol.LAUNCHER_PACKAGE);
        } catch (Throwable error) {
            return false;
        }
    }

    private static void recordInfrastructureFailure(String where, Throwable error) {
        boolean disabled = BREAKER.recordInfrastructureFailure();
        Api101Bridge.log("[DC] " + where + (disabled ? "; bridge disabled for process" : ""), error);
    }

    private static final class ListenerState {
        final WeakReference<Object> listener;
        final Executor executor;
        final Context context;

        ListenerState(Object listener, Executor executor, Context context) {
            this.listener = new WeakReference<>(listener);
            this.executor = executor;
            this.context = context;
        }
    }
}
