package com.hellovoid.liquiddock;

import android.content.Context;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Read-only production HOME/APP source backed by Xiaomi's existing WMShell task repository. */
final class SystemUiHomeOwnershipSource {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private static volatile boolean disabledForProcess;
    private static volatile SourceState currentState;

    private static Field contextField;
    private static Method isHomeVisibleMethod;
    private static Method getHomeTaskMethod;
    private static Method getTopFullscreenTaskInfoMethod;

    private SystemUiHomeOwnershipSource() {}

    static void install(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> repositoryClass = Class.forName(
                    "com.android.wm.shell.multitasking.common.taskmanager.MultiTaskingTaskRepository",
                    false, classLoader);
            contextField = HookUtil.findField(repositoryClass, "mContext");
            isHomeVisibleMethod = HookUtil.findMethodBestMatch(
                    repositoryClass, "isHomeVisible", new Object[0], false);
            getHomeTaskMethod = HookUtil.findMethodBestMatch(
                    repositoryClass, "getHomeTask", new Object[0], false);
            getTopFullscreenTaskInfoMethod = HookUtil.findMethodBestMatch(
                    repositoryClass, "getTopFullscreenTaskInfo", new Object[]{0}, false);

            Constructor<?>[] constructors = repositoryClass.getDeclaredConstructors();
            if (constructors.length == 0) {
                throw new IllegalStateException("MultiTaskingTaskRepository has no constructor");
            }
            for (Constructor<?> constructor : constructors) {
                HookUtil.hook(constructor, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    try {
                        observeRepository(chain.getThisObject());
                    } catch (Throwable error) {
                        disableForProcess("observe MultiTaskingTaskRepository", error);
                    }
                    return result;
                });
            }
            Api101Bridge.log("[DC] SystemUI HOME ownership source hook installed");
        } catch (Throwable error) {
            disableForProcess("install HOME ownership source", error);
        }
    }

    static boolean handles(int code) {
        return code == HomeOwnershipProtocol.TRANSACTION_REQUEST_BASELINE;
    }

    static boolean handleTransaction(int code, Parcel data) {
        if (!handles(code)) return false;

        SourceState state = currentState;
        if (state == null || !SystemUiTaskStateProvider.callerIsLauncher(state.context)) return true;

        long requestId = -1L;
        IBinder callback = null;
        try {
            data.enforceInterface(FreeformLeashProtocol.PROVIDER_DESCRIPTOR);
            int version = data.readInt();
            if (version != HomeOwnershipProtocol.VERSION) return true;
            requestId = data.readLong();
            int displayId = data.readInt();
            boolean confirmation = data.readInt() != 0;
            callback = data.readStrongBinder();
            if (callback == null) return true;

            if (displayId < 0) {
                sendResult(callback, requestId, HomeOwnershipProtocol.STATUS_UNAVAILABLE,
                        HomeOwnershipPolicy.Baseline.UNKNOWN, false);
                return true;
            }
            if (disabledForProcess || state.repository.get() == null) {
                sendResult(callback, requestId,
                        disabledForProcess
                                ? HomeOwnershipProtocol.STATUS_STRUCTURE_FAILURE
                                : HomeOwnershipProtocol.STATUS_UNAVAILABLE,
                        HomeOwnershipPolicy.Baseline.UNKNOWN, false);
                return true;
            }

            Executor executor = SystemUiTaskExecutorSource.executor();
            if (executor == null) {
                sendResult(callback, requestId, HomeOwnershipProtocol.STATUS_UNAVAILABLE,
                        HomeOwnershipPolicy.Baseline.UNKNOWN, false);
                return true;
            }

            final long id = requestId;
            final int targetDisplayId = displayId;
            final boolean confirm = confirmation;
            final IBinder resultCallback = callback;
            try {
                executor.execute(() -> sampleOnTaskExecutor(
                        state, resultCallback, id, targetDisplayId, confirm));
            } catch (Throwable error) {
                logRuntimeFailure("schedule HOME ownership sample", error);
                sendResult(callback, requestId, HomeOwnershipProtocol.STATUS_UNAVAILABLE,
                        HomeOwnershipPolicy.Baseline.UNKNOWN, false);
            }
            return true;
        } catch (SecurityException | IllegalArgumentException malformed) {
            return true;
        } catch (Throwable error) {
            logRuntimeFailure("HOME ownership request", error);
            if (callback != null && requestId >= 0L) {
                sendResult(callback, requestId, HomeOwnershipProtocol.STATUS_UNAVAILABLE,
                        HomeOwnershipPolicy.Baseline.UNKNOWN, false);
            }
            return true;
        }
    }

    private static void observeRepository(Object repository) throws Exception {
        if (repository == null || disabledForProcess) return;
        Object contextValue = contextField.get(repository);
        if (!(contextValue instanceof Context)) {
            throw new IllegalStateException("MultiTaskingTaskRepository#mContext unavailable");
        }
        Context context = (Context) contextValue;
        Context app = context.getApplicationContext();
        if (app != null) context = app;

        currentState = new SourceState(repository, context,
                isHomeVisibleMethod, getHomeTaskMethod, getTopFullscreenTaskInfoMethod);
        SystemUiTaskStateProvider.attachContext(context);
        Api101Bridge.log("[DC] SystemUI HOME ownership repository observed");
    }

    private static void sampleOnTaskExecutor(SourceState state, IBinder callback,
                                             long requestId, int displayId,
                                             boolean confirmation) {
        try {
            Object repository = state.repository.get();
            if (repository == null || disabledForProcess) {
                sendResult(callback, requestId, HomeOwnershipProtocol.STATUS_UNAVAILABLE,
                        HomeOwnershipPolicy.Baseline.UNKNOWN, false);
                return;
            }

            Object homeVisibleValue = state.isHomeVisible.invoke(repository);
            if (!(homeVisibleValue instanceof Boolean)) {
                throw new IllegalStateException("isHomeVisible did not return boolean");
            }
            boolean homeVisible = (Boolean) homeVisibleValue;

            Object homeTask;
            Object topFullscreenTask;
            try {
                homeTask = state.getHomeTask.invoke(repository);
            } catch (Throwable unavailable) {
                homeTask = null;
            }
            try {
                topFullscreenTask = state.getTopFullscreenTaskInfo.invoke(repository, displayId);
            } catch (Throwable unavailable) {
                topFullscreenTask = null;
            }

            int homeTaskId = reflectedTaskId(homeTask);
            int topFullscreenTaskId = reflectedTaskId(topFullscreenTask);
            HomeOwnershipPolicy.Result result = HomeOwnershipPolicy.classify(
                    homeVisible, homeTaskId, topFullscreenTaskId, confirmation);
            sendResult(callback, requestId, HomeOwnershipProtocol.STATUS_OK,
                    result.baseline, result.retryRecommended);
        } catch (Throwable error) {
            logRuntimeFailure("sample HOME ownership", error);
            sendResult(callback, requestId, HomeOwnershipProtocol.STATUS_UNAVAILABLE,
                    HomeOwnershipPolicy.Baseline.UNKNOWN, false);
        }
    }

    private static int reflectedTaskId(Object taskInfo) {
        if (taskInfo == null) return -1;
        try {
            Field field = HookUtil.findField(taskInfo.getClass(), "taskId");
            Object value = field.get(taskInfo);
            return value instanceof Integer ? (Integer) value : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static void sendResult(IBinder callback, long requestId, int status,
                                   HomeOwnershipPolicy.Baseline baseline,
                                   boolean retryRecommended) {
        if (callback == null) return;
        Parcel out = Parcel.obtain();
        try {
            out.writeInterfaceToken(HomeOwnershipProtocol.CALLBACK_DESCRIPTOR);
            out.writeInt(HomeOwnershipProtocol.VERSION);
            out.writeLong(requestId);
            out.writeInt(status);
            out.writeInt(HomeOwnershipProtocol.encodeBaseline(baseline));
            out.writeInt(retryRecommended ? 1 : 0);
            callback.transact(HomeOwnershipProtocol.TRANSACTION_BASELINE_RESULT,
                    out, null, IBinder.FLAG_ONEWAY);
        } catch (RemoteException launcherGone) {
            // Normal Launcher process death. HOME-source health is unchanged.
        } catch (Throwable error) {
            logRuntimeFailure("send HOME ownership callback", error);
        } finally {
            out.recycle();
        }
    }

    private static void disableForProcess(String where, Throwable error) {
        disabledForProcess = true;
        currentState = null;
        Api101Bridge.log("[DC] " + where + "; HOME ownership disabled for process", error);
    }

    private static void logRuntimeFailure(String where, Throwable error) {
        Api101Bridge.log("[DC] " + where + " unavailable", error);
    }

    private static final class SourceState {
        final WeakReference<Object> repository;
        final Context context;
        final Method isHomeVisible;
        final Method getHomeTask;
        final Method getTopFullscreenTaskInfo;

        SourceState(Object repository, Context context, Method isHomeVisible,
                    Method getHomeTask, Method getTopFullscreenTaskInfo) {
            this.repository = new WeakReference<>(repository);
            this.context = context;
            this.isHomeVisible = isHomeVisible;
            this.getHomeTask = getHomeTask;
            this.getTopFullscreenTaskInfo = getTopFullscreenTaskInfo;
        }
    }
}
