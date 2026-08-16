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

    private static volatile WeakReference<Object> listenerRef = new WeakReference<>(null);
    private static volatile Executor shellExecutor;
    private static volatile Context appContext;
    private static volatile FreeformLeashBrokerClient brokerClient;

    private static Field contextField;
    private static Field organizerField;
    private static Field tasksField;
    private static volatile Field leashField;

    private SystemUiFreeformLeashProvider() {}

    static void install(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> listenerClass = Class.forName(
                    "com.android.wm.shell.freeform.FreeformTaskListener", false, classLoader);
            contextField = HookUtil.findField(listenerClass, "mContext");
            organizerField = HookUtil.findField(listenerClass, "mShellTaskOrganizer");
            tasksField = HookUtil.findField(listenerClass, "mTasks");
            if (!SparseArray.class.isAssignableFrom(tasksField.getType())) {
                throw new IllegalStateException("FreeformTaskListener#mTasks is not SparseArray");
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
            BREAKER.recordInfrastructureFailure();
            INSTALLED.set(true);
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

        listenerRef = new WeakReference<>(listener);
        shellExecutor = (Executor) executorValue;
        appContext = context;

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

    private static final Binder PROVIDER_BINDER = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code != FreeformLeashProtocol.TRANSACTION_REQUEST_LEASHES) {
                return super.onTransact(code, data, reply, flags);
            }
            try {
                data.enforceInterface(FreeformLeashProtocol.PROVIDER_DESCRIPTOR);
                Context context = appContext;
                if (context == null || !callerIsLauncher(context)) return true;
                if (BREAKER.isDisabled()) {
                    long requestId = data.readLong();
                    int[] taskIds = boundedTaskIds(data.createIntArray());
                    IBinder callback = data.readStrongBinder();
                    sendUniform(callback, requestId, taskIds,
                            FreeformLeashProtocol.STATUS_INFRASTRUCTURE_FAILURE);
                    return true;
                }

                long requestId = data.readLong();
                int[] taskIds = boundedTaskIds(data.createIntArray());
                IBinder callback = data.readStrongBinder();
                if (callback == null) return true;
                Executor executor = shellExecutor;
                if (executor == null || listenerRef.get() == null) {
                    sendUniform(callback, requestId, taskIds,
                            FreeformLeashProtocol.STATUS_UNAVAILABLE);
                    return true;
                }
                try {
                    executor.execute(() -> {
                        try {
                            resolveOnShellExecutor(callback, requestId, taskIds);
                        } catch (Throwable error) {
                            recordInfrastructureFailure("resolve freeform leash", error);
                            sendUniform(callback, requestId, taskIds,
                                    FreeformLeashProtocol.STATUS_INFRASTRUCTURE_FAILURE);
                        }
                    });
                } catch (Throwable error) {
                    recordInfrastructureFailure("schedule freeform leash lookup", error);
                    sendUniform(callback, requestId, taskIds,
                            FreeformLeashProtocol.STATUS_INFRASTRUCTURE_FAILURE);
                }
                return true;
            } catch (Throwable error) {
                recordInfrastructureFailure("SystemUI provider transaction", error);
                return true;
            }
        }
    };

    private static void resolveOnShellExecutor(IBinder callback, long requestId, int[] taskIds)
            throws Exception {
        Object listener = listenerRef.get();
        if (listener == null) {
            sendUniform(callback, requestId, taskIds, FreeformLeashProtocol.STATUS_UNAVAILABLE);
            return;
        }
        Object tasksValue = tasksField.get(listener);
        if (!(tasksValue instanceof SparseArray)) {
            throw new IllegalStateException("mTasks changed type");
        }
        SparseArray<?> tasks = (SparseArray<?>) tasksValue;
        int[] statuses = new int[taskIds.length];
        SurfaceControl[] surfaces = new SurfaceControl[taskIds.length];
        for (int i = 0; i < taskIds.length; i++) {
            Object state = tasks.get(taskIds[i]);
            if (state == null) {
                statuses[i] = FreeformLeashProtocol.STATUS_UNAVAILABLE;
                continue;
            }
            Field field = leashField;
            if (field == null || !field.getDeclaringClass().isAssignableFrom(state.getClass())) {
                field = HookUtil.findField(state.getClass(), "mLeash");
                if (!SurfaceControl.class.isAssignableFrom(field.getType())) {
                    throw new IllegalStateException("freeform state mLeash is not SurfaceControl");
                }
                leashField = field;
            }
            Object value = field.get(state);
            if (value instanceof SurfaceControl && ((SurfaceControl) value).isValid()) {
                statuses[i] = FreeformLeashProtocol.STATUS_OK;
                surfaces[i] = (SurfaceControl) value;
            } else {
                statuses[i] = FreeformLeashProtocol.STATUS_UNAVAILABLE;
            }
        }
        sendResult(callback, requestId, taskIds, statuses, surfaces);
    }

    private static void sendUniform(IBinder callback, long requestId, int[] taskIds, int status) {
        if (callback == null) return;
        int[] statuses = new int[taskIds.length];
        java.util.Arrays.fill(statuses, status);
        sendResult(callback, requestId, taskIds, statuses, new SurfaceControl[taskIds.length]);
    }

    private static void sendResult(IBinder callback, long requestId, int[] taskIds,
                                   int[] statuses, SurfaceControl[] surfaces) {
        Parcel out = Parcel.obtain();
        try {
            out.writeInterfaceToken(FreeformLeashProtocol.CALLBACK_DESCRIPTOR);
            out.writeLong(requestId);
            out.writeInt(taskIds.length);
            for (int i = 0; i < taskIds.length; i++) {
                out.writeInt(taskIds[i]);
                out.writeInt(statuses[i]);
                out.writeTypedObject(surfaces[i], 0);
            }
            callback.transact(FreeformLeashProtocol.TRANSACTION_LEASH_RESULT,
                    out, null, IBinder.FLAG_ONEWAY);
        } catch (Throwable error) {
            recordInfrastructureFailure("send freeform leash callback", error);
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

    private static int[] boundedTaskIds(int[] input) {
        int[] ids = FreeformBridgePolicy.deduplicateTaskIds(input);
        if (ids.length > FreeformLeashProtocol.MAX_TASKS) {
            return java.util.Arrays.copyOf(ids, FreeformLeashProtocol.MAX_TASKS);
        }
        return ids;
    }

    private static void recordInfrastructureFailure(String where, Throwable error) {
        boolean disabled = BREAKER.recordInfrastructureFailure();
        Api101Bridge.log("[DC] " + where + (disabled ? "; bridge disabled for process" : ""), error);
    }
}
