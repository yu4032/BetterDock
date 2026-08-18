package com.hellovoid.liquiddock;

import android.content.Context;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.view.Display;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Launcher-side consumer of SystemUI transition events.
 *
 * The runtime owns a visual hold only. It never requests HOME ownership and never calls
 * setLauncherState/onLauncherFocused/onLauncherFocusLost while a transition is running.
 */
final class SystemUiTransitionRuntime {
    private static final String TAG = "[DC][TR]";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private static WeakReference<DockLiquidGlassView> currentView = new WeakReference<>(null);
    private static Handler mainHandler;
    private static IBinder provider;

    private static long sourceGeneration = Long.MIN_VALUE;
    private static long activeTokenId;
    private static int activeDisplayId = -1;
    private static boolean visualHold;

    private SystemUiTransitionRuntime() {}

    static void install() {
        if (!INSTALLED.compareAndSet(false, true)) return;
        installCaptureRequestGate();
        installCaptureInstallGate();
        installLegacyGestureAuthorityGate();
        installExactOverviewBridge();
        MainHook.log(TAG + " transition visual runtime installed");
    }

    static void bind(DockLiquidGlassView glass, Context context) {
        if (glass == null || context == null) return;
        runOnMain(() -> {
            currentView = new WeakReference<>(glass);
            if (visualHold) {
                visualHold = false;
                activeTokenId = 0L;
                activeDisplayId = -1;
            }
        });
    }

    /** Called by HomeOwnershipResolver's existing event-driven provider listener. */
    static void onProviderChanged(IBinder next) {
        runOnMain(() -> {
            provider = next;
            if (next != null) registerCallback(next);
        });
    }

    static void beginAppToLauncherVisualHold(long generation, long tokenId, int displayId) {
        runOnMain(() -> {
            DockLiquidGlassView glass = currentView.get();
            if (glass == null || !matchesDisplay(glass, displayId)) return;
            acceptGeneration(generation);
            visualHold = true;
            activeTokenId = tokenId;
            activeDisplayId = displayId;
            // Invalidate queued/in-flight readbacks without recycling the installed APP bitmap.
            HookUtil.invoke(glass, "cancelPendingCaptureWork");
            MainHook.log(TAG + " APP_TO_LAUNCHER hold start token=" + tokenId
                    + " generation=" + generation + " display=" + displayId);
        });
    }

    static void resolveVisualHoldToOverview() {
        runOnMain(() -> {
            if (!visualHold) return;
            DockLiquidGlassView glass = currentView.get();
            visualHold = false;
            activeTokenId = 0L;
            activeDisplayId = -1;
            if (glass != null) {
                HookUtil.invoke(glass, "cancelPendingCaptureWork");
            }
            MainHook.log(TAG + " visual hold resolved by exact Overview");
        });
    }

    static void finishAppToLauncherVisualHold(long generation, long tokenId, boolean aborted) {
        runOnMain(() -> {
            if (!visualHold || generation != sourceGeneration || tokenId != activeTokenId) return;
            DockLiquidGlassView glass = currentView.get();
            visualHold = false;
            activeTokenId = 0L;
            activeDisplayId = -1;
            if (glass == null) return;

            if (aborted) {
                applyStableScene(glass, false);
                glass.prearmAppBackdrop("systemui-transition-abort");
                MainHook.log(TAG + " visual hold aborted -> APP token=" + tokenId);
                return;
            }

            // No ownership query and no settle timer. WMShell finish is the boundary; capture on
            // the next Launcher main-loop turn after the finish callback transaction has landed.
            applyStableScene(glass, true);
            glass.post(() -> glass.requestCapture("systemui-transition-home-finished"));
            MainHook.log(TAG + " visual hold finished -> HOME token=" + tokenId);
        });
    }

    static void mergeAppToLauncherVisualHold(long generation, long mergedTokenId,
                                               long playingTokenId, int displayId) {
        runOnMain(() -> {
            if (!visualHold || generation != sourceGeneration
                    || mergedTokenId != activeTokenId || activeDisplayId != displayId) return;
            activeTokenId = playingTokenId;
            MainHook.log(TAG + " visual hold merged " + mergedTokenId + " -> " + playingTokenId);
        });
    }

    static void resolveLauncherToApp(long generation, long tokenId, int displayId) {
        runOnMain(() -> {
            DockLiquidGlassView glass = currentView.get();
            if (glass == null || !matchesDisplay(glass, displayId)) return;
            acceptGeneration(generation);
            visualHold = false;
            activeTokenId = 0L;
            activeDisplayId = -1;
            HookUtil.invoke(glass, "cancelPendingCaptureWork");
            applyStableScene(glass, false);
            glass.prearmAppBackdrop("systemui-transition-app");
            MainHook.log(TAG + " LAUNCHER_TO_APP token=" + tokenId + " display=" + displayId);
        });
    }

    private static void applyStableScene(DockLiquidGlassView glass, boolean home) {
        // This is intentionally an atomic field update, not setLauncherState(): the latter also
        // invalidates lifecycle/capture state and was the source of UNKNOWN/APP/HOME churn.
        HookUtil.setField(glass, "launcherLifecycleKnown", true);
        HookUtil.setField(glass, "launcherResumed", home);
        try {
            Object sceneState = HookUtil.getField(glass, "sceneState");
            if (sceneState != null) HookUtil.invoke(sceneState, "clearGestureTarget");
        } catch (Throwable ignored) {}
        HookUtil.invoke(glass, "updateDesiredScene");
    }

    private static void acceptGeneration(long generation) {
        if (sourceGeneration == generation) return;
        sourceGeneration = generation;
        visualHold = false;
        activeTokenId = 0L;
        activeDisplayId = -1;
    }

    private static boolean matchesDisplay(DockLiquidGlassView glass, int displayId) {
        Display display = glass.getDisplay();
        return display != null && displayId >= 0 && display.getDisplayId() == displayId;
    }

    private static void installCaptureRequestGate() {
        try {
            HookUtil.hookMethod(DockLiquidGlassView.class, "requestStateCapture",
                    new Class<?>[]{String.class}, chain -> {
                        Object owner = chain.getThisObject();
                        if (visualHold && owner == currentView.get()) {
                            return null;
                        }
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });
            MainHook.log(TAG + " visual hold capture-request gate installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " capture-request gate unavailable: " + error);
        }
    }

    private static void installCaptureInstallGate() {
        try {
            int hooked = 0;
            for (Method method : DockLiquidGlassView.class.getDeclaredMethods()) {
                if (!"installCapture".equals(method.getName()) || method.getParameterCount() != 3) {
                    continue;
                }
                HookUtil.hook(method, chain -> {
                    Object owner = chain.getThisObject();
                    if (visualHold && owner == currentView.get()) {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (args.length > 0 && args[0] != null) {
                            try { HookUtil.invoke(args[0], "recycle"); }
                            catch (Throwable ignored) {}
                        }
                        return null;
                    }
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                });
                hooked++;
            }
            MainHook.log(TAG + " visual hold capture-install gate hooked=" + hooked);
        } catch (Throwable error) {
            MainHook.log(TAG + " capture-install gate unavailable: " + error);
        }
    }

    private static void installLegacyGestureAuthorityGate() {
        try {
            HookUtil.hookMethod(DockLiquidGlassView.class, "setGestureCaptureTarget",
                    new Class<?>[]{String.class}, chain -> {
                        if (chain.getThisObject() == currentView.get()) {
                            // HOME/APP/RECENTS destination constructors are no longer scene
                            // authorities. Exact Overview and WMShell transition events own them.
                            return null;
                        }
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });
            MainHook.log(TAG + " legacy gesture capture authority disabled");
        } catch (Throwable error) {
            MainHook.log(TAG + " legacy gesture gate unavailable: " + error);
        }
    }

    private static void installExactOverviewBridge() {
        try {
            HookUtil.hookMethod(DockLiquidGlassView.class, "setOverviewActive",
                    new Class<?>[]{boolean.class, String.class}, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (chain.getThisObject() == currentView.get()
                                && args.length > 0 && Boolean.TRUE.equals(args[0])) {
                            resolveVisualHoldToOverview();
                        }
                        return chain.proceed(args);
                    });
            MainHook.log(TAG + " exact Overview transition bridge installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " exact Overview bridge unavailable: " + error);
        }
    }

    private static void registerCallback(IBinder targetProvider) {
        if (targetProvider == null) return;
        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken(FreeformLeashProtocol.PROVIDER_DESCRIPTOR);
            data.writeInt(SystemUiTransitionProtocol.VERSION);
            data.writeStrongBinder(callback);
            boolean accepted = targetProvider.transact(
                    SystemUiTransitionProtocol.TRANSACTION_REGISTER_CALLBACK,
                    data, null, IBinder.FLAG_ONEWAY);
            if (!accepted) {
                MainHook.log(TAG + " provider rejected transition callback registration");
            }
        } catch (RemoteException providerGone) {
            if (provider == targetProvider) provider = null;
        } catch (Throwable error) {
            MainHook.log(TAG + " transition callback registration unavailable: " + error);
        } finally {
            data.recycle();
        }
    }

    private static final Binder callback = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code != SystemUiTransitionProtocol.TRANSACTION_EVENT) return false;
            try {
                data.enforceInterface(SystemUiTransitionProtocol.CALLBACK_DESCRIPTOR);
                int version = data.readInt();
                int type = data.readInt();
                long generation = data.readLong();
                long tokenId = data.readLong();
                long otherTokenId = data.readLong();
                int displayId = data.readInt();
                if (version != SystemUiTransitionProtocol.VERSION
                        || !SystemUiTransitionProtocol.isKnownEvent(type)) return true;
                dispatch(type, generation, tokenId, otherTokenId, displayId);
                return true;
            } catch (Throwable error) {
                MainHook.log(TAG + " malformed transition callback: " + error);
                return true;
            }
        }
    };

    private static void dispatch(int type, long generation, long tokenId,
                                 long otherTokenId, int displayId) {
        if (type == SystemUiTransitionProtocol.APP_TO_LAUNCHER_START) {
            beginAppToLauncherVisualHold(generation, tokenId, displayId);
        } else if (type == SystemUiTransitionProtocol.TRANSITION_MERGED) {
            mergeAppToLauncherVisualHold(generation, tokenId, otherTokenId, displayId);
        } else if (type == SystemUiTransitionProtocol.TRANSITION_FINISHED) {
            finishAppToLauncherVisualHold(generation, tokenId, false);
        } else if (type == SystemUiTransitionProtocol.TRANSITION_ABORTED) {
            finishAppToLauncherVisualHold(generation, tokenId, true);
        } else if (type == SystemUiTransitionProtocol.LAUNCHER_TO_APP) {
            resolveLauncherToApp(generation, tokenId, displayId);
        }
    }

    private static void runOnMain(Runnable action) {
        Handler handler = mainHandler;
        if (handler == null) {
            synchronized (SystemUiTransitionRuntime.class) {
                handler = mainHandler;
                if (handler == null) {
                    handler = new Handler(Looper.getMainLooper());
                    mainHandler = handler;
                }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else handler.post(action);
    }
}
