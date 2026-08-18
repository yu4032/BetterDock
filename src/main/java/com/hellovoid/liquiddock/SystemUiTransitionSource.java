package com.hellovoid.liquiddock;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Passive WMShell transition observer installed only inside SystemUI.
 *
 * The implementation deliberately reflects hidden Shell/TransitionInfo types so the module keeps
 * compiling against the public Android SDK. It publishes only normalized visual-handoff events;
 * it never mutates WMShell state and never polls Launcher ownership.
 */
final class SystemUiTransitionSource {
    private static final String TRANSITIONS_CLASS =
            "com.android.wm.shell.transition.Transitions";
    private static final String OBSERVER_CLASS =
            "com.android.wm.shell.transition.Transitions$TransitionObserver";

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicLong TOKEN_IDS = new AtomicLong();
    private static final long PROCESS_GENERATION = SystemClock.elapsedRealtimeNanos();

    private static final Object LOCK = new Object();
    private static final IdentityHashMap<IBinder, Long> tokenIds = new IdentityHashMap<>();
    private static final IdentityHashMap<IBinder, Integer> appToLauncherDisplays =
            new IdentityHashMap<>();

    private static volatile Object observedTransitions;
    private static volatile Object observerProxy;
    private static volatile IBinder launcherCallback;

    private SystemUiTransitionSource() {}

    static void install(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> transitionsClass = Class.forName(TRANSITIONS_CLASS, false, classLoader);
            Class<?> observerClass = Class.forName(OBSERVER_CLASS, false, classLoader);
            Constructor<?>[] constructors = transitionsClass.getDeclaredConstructors();
            if (constructors.length == 0) {
                throw new IllegalStateException("Transitions has no constructor");
            }
            for (Constructor<?> constructor : constructors) {
                HookUtil.hook(constructor, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    try {
                        observeTransitions(chain.getThisObject(), observerClass);
                    } catch (Throwable error) {
                        Api101Bridge.log("[DC] SystemUI transition observer attach unavailable", error);
                    }
                    return result;
                });
            }
            Api101Bridge.log("[DC] SystemUI transition source hook installed");
        } catch (Throwable error) {
            Api101Bridge.log("[DC] SystemUI transition source unavailable", error);
        }
    }

    static boolean handles(int code) {
        return code == SystemUiTransitionProtocol.TRANSACTION_REGISTER_CALLBACK;
    }

    static boolean handleTransaction(int code, Parcel data) {
        if (!handles(code)) return false;
        try {
            if (!SystemUiTaskStateProvider.callerIsLauncher(SystemUiTaskStateProvider.context())) {
                return true;
            }
            data.enforceInterface(FreeformLeashProtocol.PROVIDER_DESCRIPTOR);
            int version = data.readInt();
            IBinder callback = data.readStrongBinder();
            if (version != SystemUiTransitionProtocol.VERSION || callback == null) return true;
            setLauncherCallback(callback);
            return true;
        } catch (SecurityException | IllegalArgumentException malformed) {
            return true;
        } catch (Throwable error) {
            Api101Bridge.log("[DC] SystemUI transition callback registration unavailable", error);
            return true;
        }
    }

    private static void observeTransitions(Object transitions, Class<?> observerClass)
            throws Exception {
        if (transitions == null || transitions == observedTransitions) return;
        Object proxy = Proxy.newProxyInstance(observerClass.getClassLoader(),
                new Class<?>[]{observerClass}, (instance, method, args) -> {
                    String name = method.getName();
                    try {
                        if ("onTransitionReady".equals(name)) {
                            onTransitionReady(args);
                        } else if ("onTransitionMerged".equals(name)) {
                            onTransitionMerged(args);
                        } else if ("onTransitionFinished".equals(name)) {
                            onTransitionFinished(args);
                        }
                    } catch (Throwable error) {
                        Api101Bridge.log("[DC] SystemUI transition event unavailable " + name, error);
                    }
                    return null;
                });
        Method register = HookUtil.findMethodBestMatch(
                transitions.getClass(), "registerObserver", new Object[]{proxy}, false);
        register.invoke(transitions, proxy);
        observedTransitions = transitions;
        observerProxy = proxy; // strong ref for the lifetime of the Shell instance
        Api101Bridge.log("[DC] SystemUI WMShell TransitionObserver registered");
    }

    private static void onTransitionReady(Object[] args) {
        if (args == null || args.length < 2 || !(args[0] instanceof IBinder) || args[1] == null) {
            return;
        }
        IBinder token = (IBinder) args[0];
        List<SystemUiTransitionPolicy.Change> changes = normalizeChanges(args[1]);
        SystemUiTransitionPolicy.Kind kind = SystemUiTransitionPolicy.classify(changes);
        int displayId = SystemUiTransitionPolicy.displayIdFor(changes, kind);
        if (displayId < 0) return;

        long tokenId = tokenId(token);
        if (kind == SystemUiTransitionPolicy.Kind.APP_TO_LAUNCHER) {
            synchronized (LOCK) {
                appToLauncherDisplays.put(token, displayId);
            }
            pushTransitionEvent(SystemUiTransitionProtocol.APP_TO_LAUNCHER_START,
                    tokenId, 0L, displayId);
        } else if (kind == SystemUiTransitionPolicy.Kind.LAUNCHER_TO_APP) {
            synchronized (LOCK) {
                appToLauncherDisplays.remove(token);
            }
            pushTransitionEvent(SystemUiTransitionProtocol.LAUNCHER_TO_APP,
                    tokenId, 0L, displayId);
        }
    }

    private static void onTransitionMerged(Object[] args) {
        if (args == null || args.length < 2
                || !(args[0] instanceof IBinder) || !(args[1] instanceof IBinder)) {
            return;
        }
        IBinder merged = (IBinder) args[0];
        IBinder playing = (IBinder) args[1];
        Integer displayId;
        synchronized (LOCK) {
            displayId = appToLauncherDisplays.remove(merged);
            if (displayId != null) appToLauncherDisplays.put(playing, displayId);
        }
        if (displayId == null) return;
        pushTransitionEvent(SystemUiTransitionProtocol.TRANSITION_MERGED,
                tokenId(merged), tokenId(playing), displayId);
    }

    private static void onTransitionFinished(Object[] args) {
        if (args == null || args.length < 1 || !(args[0] instanceof IBinder)) return;
        IBinder token = (IBinder) args[0];
        boolean aborted = args.length > 1 && Boolean.TRUE.equals(args[1]);
        Integer displayId;
        synchronized (LOCK) {
            displayId = appToLauncherDisplays.remove(token);
        }
        if (displayId == null) return;
        pushTransitionEvent(aborted
                        ? SystemUiTransitionProtocol.TRANSITION_ABORTED
                        : SystemUiTransitionProtocol.TRANSITION_FINISHED,
                tokenId(token), 0L, displayId);
    }

    private static List<SystemUiTransitionPolicy.Change> normalizeChanges(Object transitionInfo) {
        ArrayList<SystemUiTransitionPolicy.Change> out = new ArrayList<>();
        try {
            Object value = invokeNoArgs(transitionInfo, "getChanges");
            if (!(value instanceof List)) return out;
            for (Object change : (List<?>) value) {
                if (change == null) continue;
                try {
                    int mode = intValue(invokeNoArgs(change, "getMode"), -1);
                    int flags = intValue(invokeNoArgs(change, "getFlags"), 0);
                    Object taskInfo = invokeNoArgs(change, "getTaskInfo");
                    int displayId = reflectedDisplayId(change, taskInfo);
                    int activityType = reflectedActivityType(taskInfo);
                    boolean homeTask = activityType == 2; // ACTIVITY_TYPE_HOME
                    boolean appTask = activityType == 1;  // ACTIVITY_TYPE_STANDARD
                    boolean wallpaper = (flags & transitionFlag("FLAG_IS_WALLPAPER", 1 << 1)) != 0;
                    boolean showWallpaper =
                            (flags & transitionFlag("FLAG_SHOW_WALLPAPER", 1)) != 0;
                    boolean movingFront = mode == transitMode("TRANSIT_OPEN", 1)
                            || mode == transitMode("TRANSIT_TO_FRONT", 3);
                    boolean movingBack = mode == transitMode("TRANSIT_CLOSE", 2)
                            || mode == transitMode("TRANSIT_TO_BACK", 4);
                    out.add(new SystemUiTransitionPolicy.Change(displayId,
                            homeTask, appTask, wallpaper, movingFront, movingBack, showWallpaper));
                } catch (Throwable ignored) {
                    // One vendor-only Change must not invalidate the rest of the transition.
                }
            }
        } catch (Throwable error) {
            Api101Bridge.log("[DC] TransitionInfo normalization unavailable", error);
        }
        return out;
    }

    private static int reflectedDisplayId(Object change, Object taskInfo) {
        int displayId = intFromField(taskInfo, "displayId", -1);
        if (displayId >= 0) return displayId;
        Object end = invokeNoArgsQuiet(change, "getEndDisplayId");
        displayId = intValue(end, -1);
        if (displayId >= 0) return displayId;
        return intValue(invokeNoArgsQuiet(change, "getStartDisplayId"), -1);
    }

    private static int reflectedActivityType(Object taskInfo) {
        if (taskInfo == null) return 0;
        Object direct = invokeNoArgsQuiet(taskInfo, "getActivityType");
        int type = intValue(direct, 0);
        if (type != 0) return type;
        type = intFromField(taskInfo, "topActivityType", 0);
        if (type != 0) return type;
        try {
            Object configuration = fieldValue(taskInfo, "configuration");
            Object windowConfiguration = fieldValue(configuration, "windowConfiguration");
            return intValue(invokeNoArgs(windowConfiguration, "getActivityType"), 0);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int transitionFlag(String name, int fallback) {
        return staticInt("android.window.TransitionInfo", name, fallback);
    }

    private static int transitMode(String name, int fallback) {
        return staticInt("android.view.WindowManager", name, fallback);
    }

    private static int staticInt(String className, String name, int fallback) {
        try {
            Class<?> type = Class.forName(className);
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(null);
            return value instanceof Integer ? (Integer) value : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static Object invokeNoArgs(Object owner, String name) throws Exception {
        if (owner == null) return null;
        Method method = HookUtil.findMethodBestMatch(owner.getClass(), name, new Object[0], false);
        return method.invoke(owner);
    }

    private static Object invokeNoArgsQuiet(Object owner, String name) {
        try { return invokeNoArgs(owner, name); }
        catch (Throwable ignored) { return null; }
    }

    private static Object fieldValue(Object owner, String name) throws Exception {
        if (owner == null) return null;
        Field field = HookUtil.findField(owner.getClass(), name);
        return field.get(owner);
    }

    private static int intFromField(Object owner, String name, int fallback) {
        try { return intValue(fieldValue(owner, name), fallback); }
        catch (Throwable ignored) { return fallback; }
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static long tokenId(IBinder token) {
        if (token == null) return 0L;
        synchronized (LOCK) {
            Long existing = tokenIds.get(token);
            if (existing != null) return existing;
            long next = TOKEN_IDS.incrementAndGet();
            tokenIds.put(token, next);
            return next;
        }
    }

    private static void setLauncherCallback(IBinder callback) {
        IBinder previous = launcherCallback;
        if (previous == callback) return;
        launcherCallback = callback;
        try {
            callback.linkToDeath(() -> {
                if (launcherCallback == callback) launcherCallback = null;
            }, 0);
        } catch (Throwable error) {
            if (launcherCallback == callback) launcherCallback = null;
            return;
        }
        Api101Bridge.log("[DC] SystemUI transition callback registered generation="
                + PROCESS_GENERATION);
    }

    private static void pushTransitionEvent(int type, long tokenId, long otherTokenId,
                                            int displayId) {
        IBinder callback = launcherCallback;
        if (callback == null) return;
        Parcel out = Parcel.obtain();
        try {
            out.writeInterfaceToken(SystemUiTransitionProtocol.CALLBACK_DESCRIPTOR);
            out.writeInt(SystemUiTransitionProtocol.VERSION);
            out.writeInt(type);
            out.writeLong(PROCESS_GENERATION);
            out.writeLong(tokenId);
            out.writeLong(otherTokenId);
            out.writeInt(displayId);
            callback.transact(SystemUiTransitionProtocol.TRANSACTION_EVENT,
                    out, null, IBinder.FLAG_ONEWAY);
        } catch (RemoteException launcherGone) {
            if (launcherCallback == callback) launcherCallback = null;
        } catch (Throwable error) {
            Api101Bridge.log("[DC] pushTransitionEvent unavailable type=" + type, error);
        } finally {
            out.recycle();
        }
    }
}
