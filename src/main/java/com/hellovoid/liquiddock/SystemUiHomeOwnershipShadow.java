package com.hellovoid.liquiddock;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/** Diagnostic-only, read-only view of WMShell's existing MultiTaskingTaskRepository. */
final class SystemUiHomeOwnershipShadow {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static volatile boolean disabledForProcess;
    private static volatile ShadowState currentState;

    private static Field contextField;
    private static Field executorField;
    private static Method isHomeVisibleMethod;
    private static Method getHomeTaskMethod;
    private static Method getTopFullscreenTaskInfoMethod;

    private SystemUiHomeOwnershipShadow() {}

    static void install(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> repositoryClass = Class.forName(
                    "com.android.wm.shell.multitasking.common.taskmanager.MultiTaskingTaskRepository",
                    false, classLoader);
            contextField = HookUtil.findField(repositoryClass, "mContext");
            executorField = HookUtil.findField(repositoryClass, "mBgExecutor");
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
                        disableShadow("observe MultiTaskingTaskRepository", error);
                    }
                    return result;
                });
            }
            Api101Bridge.log("[DC-SHADOW] HOME ownership repository hook installed");
        } catch (Throwable error) {
            disableShadow("install HOME ownership repository hook", error);
        }
    }

    static boolean handles(int code) {
        return code == HomeOwnershipShadowProtocol.TRANSACTION_REQUEST_HOME_OWNERSHIP_SHADOW;
    }

    static boolean handleTransaction(int code, Parcel data, Context authContext) {
        if (!handles(code)) return false;
        if (authContext == null || !callerIsLauncher(authContext)) return true;

        long requestId = -1L;
        IBinder callback = null;
        try {
            data.enforceInterface(FreeformLeashProtocol.PROVIDER_DESCRIPTOR);
            requestId = data.readLong();
            int displayId = data.readInt();
            callback = data.readStrongBinder();
            if (callback == null) return true;
            if (displayId < 0 || disabledForProcess) {
                sendResult(callback, requestId,
                        disabledForProcess
                                ? HomeOwnershipShadowProtocol.STATUS_STRUCTURE_FAILURE
                                : HomeOwnershipShadowProtocol.STATUS_UNAVAILABLE,
                        false, -1, -1, -1, System.nanoTime());
                return true;
            }

            ShadowState state = currentState;
            if (state == null || state.repository.get() == null) {
                sendResult(callback, requestId, HomeOwnershipShadowProtocol.STATUS_UNAVAILABLE,
                        false, -1, -1, -1, System.nanoTime());
                return true;
            }

            final long id = requestId;
            final int targetDisplayId = displayId;
            final IBinder resultCallback = callback;
            Runnable task = () -> sampleOnRepositoryExecutor(
                    state, resultCallback, id, targetDisplayId);
            state.executeMethod.invoke(state.executor, task);
            return true;
        } catch (SecurityException | IllegalArgumentException malformed) {
            return true;
        } catch (Throwable error) {
            logRuntimeFailure("HOME shadow request", error);
            if (callback != null && requestId >= 0L) {
                sendResult(callback, requestId, HomeOwnershipShadowProtocol.STATUS_UNAVAILABLE,
                        false, -1, -1, -1, System.nanoTime());
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
        Context application = context.getApplicationContext();
        if (application != null) context = application;

        Object executor = executorField.get(repository);
        if (executor == null) {
            throw new IllegalStateException("MultiTaskingTaskRepository#mBgExecutor unavailable");
        }
        Method execute = HookUtil.findMethodBestMatch(
                executor.getClass(), "execute", new Object[]{(Runnable) () -> {}}, false);

        currentState = new ShadowState(repository, context, executor, execute,
                isHomeVisibleMethod, getHomeTaskMethod, getTopFullscreenTaskInfoMethod);
        Api101Bridge.log("[DC-SHADOW] HOME ownership repository observed");
    }

    private static void sampleOnRepositoryExecutor(ShadowState state, IBinder callback,
                                                   long requestId, int displayId) {
        try {
            Object repository = state.repository.get();
            if (repository == null) {
                sendResult(callback, requestId, HomeOwnershipShadowProtocol.STATUS_UNAVAILABLE,
                        false, -1, -1, -1, System.nanoTime());
                return;
            }

            Object visibleValue = state.isHomeVisibleMethod.invoke(repository);
            if (!(visibleValue instanceof Boolean)) {
                throw new IllegalStateException("isHomeVisible did not return boolean");
            }
            boolean homeVisible = (Boolean) visibleValue;

            Object homeTask = null;
            Object topFullscreenTask = null;
            try { homeTask = state.getHomeTaskMethod.invoke(repository); }
            catch (Throwable ignored) {}
            try { topFullscreenTask = state.getTopFullscreenTaskInfoMethod.invoke(repository, displayId); }
            catch (Throwable ignored) {}

            int homeTaskId = reflectedTaskId(homeTask);
            int topFullscreenTaskId = reflectedTaskId(topFullscreenTask);
            int topFullscreenWindowingMode = reflectedWindowingMode(topFullscreenTask);
            sendResult(callback, requestId, HomeOwnershipShadowProtocol.STATUS_OK,
                    homeVisible, homeTaskId, topFullscreenTaskId,
                    topFullscreenWindowingMode, System.nanoTime());
        } catch (Throwable error) {
            logRuntimeFailure("sample HOME ownership", error);
            sendResult(callback, requestId, HomeOwnershipShadowProtocol.STATUS_UNAVAILABLE,
                    false, -1, -1, -1, System.nanoTime());
        }
    }

    private static int reflectedTaskId(Object taskInfo) {
        if (taskInfo == null) return -1;
        try {
            Field field = HookUtil.findField(taskInfo.getClass(), "taskId");
            Object value = field.get(taskInfo);
            if (value instanceof Integer) return (Integer) value;
        } catch (Throwable ignored) {}
        return -1;
    }

    private static int reflectedWindowingMode(Object taskInfo) {
        if (taskInfo == null) return -1;
        try {
            Object value = HookUtil.invoke(taskInfo, "getWindowingMode");
            if (value instanceof Integer) return (Integer) value;
        } catch (Throwable ignored) {}
        return -1;
    }

    private static void sendResult(IBinder callback, long requestId, int status,
                                   boolean homeVisible, int homeTaskId,
                                   int topFullscreenTaskId, int topFullscreenWindowingMode,
                                   long sampleElapsedNanos) {
        if (callback == null) return;
        Parcel out = Parcel.obtain();
        try {
            out.writeInterfaceToken(HomeOwnershipShadowProtocol.CALLBACK_DESCRIPTOR);
            out.writeLong(requestId);
            out.writeInt(status);
            out.writeInt(homeVisible ? 1 : 0);
            out.writeInt(homeTaskId);
            out.writeInt(topFullscreenTaskId);
            out.writeInt(topFullscreenWindowingMode);
            out.writeLong(sampleElapsedNanos);
            callback.transact(
                    HomeOwnershipShadowProtocol.TRANSACTION_HOME_OWNERSHIP_SHADOW_RESULT,
                    out, null, IBinder.FLAG_ONEWAY);
        } catch (RemoteException launcherGone) {
            // Normal Launcher process death; diagnostic health is unaffected.
        } catch (Throwable error) {
            logRuntimeFailure("send HOME shadow callback", error);
        } finally {
            out.recycle();
        }
    }

    private static boolean callerIsLauncher(Context context) {
        try {
            String[] packages = context.getPackageManager().getPackagesForUid(Binder.getCallingUid());
            return FreeformBridgePolicy.packageListContains(
                    packages, FreeformLeashProtocol.LAUNCHER_PACKAGE);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void disableShadow(String where, Throwable error) {
        disabledForProcess = true;
        currentState = null;
        Api101Bridge.log("[DC-SHADOW] " + where + "; shadow disabled for process", error);
    }

    private static void logRuntimeFailure(String where, Throwable error) {
        Api101Bridge.log("[DC-SHADOW] " + where + " unavailable", error);
    }

    private static final class ShadowState {
        final WeakReference<Object> repository;
        final Context context;
        final Object executor;
        final Method executeMethod;
        final Method isHomeVisibleMethod;
        final Method getHomeTaskMethod;
        final Method getTopFullscreenTaskInfoMethod;

        ShadowState(Object repository, Context context, Object executor, Method executeMethod,
                    Method isHomeVisibleMethod, Method getHomeTaskMethod,
                    Method getTopFullscreenTaskInfoMethod) {
            this.repository = new WeakReference<>(repository);
            this.context = context;
            this.executor = executor;
            this.executeMethod = executeMethod;
            this.isHomeVisibleMethod = isHomeVisibleMethod;
            this.getHomeTaskMethod = getHomeTaskMethod;
            this.getTopFullscreenTaskInfoMethod = getTopFullscreenTaskInfoMethod;
        }
    }
}
