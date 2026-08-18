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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Launcher-side consumer of SystemUI transition events.
 *
 * SystemUI owns transition classification/token safety, but it no longer freezes the installed
 * backdrop. APP↔Launcher is a live composed animation and LiquidDock must continue sampling it.
 * The legacy visualHold field/method names are retained for protocol/diagnostic compatibility;
 * they now mean "tracked transition capture is active", never "block request/install".
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
    /** Legacy diagnostic name: true means APP_TO_LAUNCHER transition capture is active. */
    private static boolean visualHold;
    private static long transitionSequence;

    private SystemUiTransitionRuntime() {}

    static void install() {
        if (!INSTALLED.compareAndSet(false, true)) return;
        // Deliberately no capture request/install gates. Transition events control scene authority
        // and continuous cadence only; every animation frame remains eligible for installation.
        installLegacyGestureAuthorityGate();
        installExactOverviewBridge();
        MainHook.log(TAG + " continuous transition capture runtime installed");
    }

    static void bind(DockLiquidGlassView glass, Context context) {
        if (glass == null || context == null) return;
        runOnMain(() -> {
            currentView = new WeakReference<>(glass);
            transitionSequence++;
            visualHold = false;
            activeTokenId = 0L;
            activeDisplayId = -1;
            AppGestureBackdropHoldHook.setSystemUiTransitionActive(false, "rebind");
        });
    }

    static boolean isVisualHoldActive(DockLiquidGlassView glass) {
        return visualHold && glass != null && currentView.get() == glass;
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
            if (!acceptGeneration(generation)) return;
            transitionSequence++;
            visualHold = true;
            activeTokenId = tokenId;
            activeDisplayId = displayId;
            AppGestureBackdropHoldHook.setSystemUiTransitionActive(
                    true, "app-to-launcher-token-" + tokenId);
            // Do not cancel the frame already in flight: it is part of the animation and should
            // install normally. This request merely makes the transition boundary immediately dirty.
            glass.requestCapture("systemui-transition-start");
            MainHook.log(TAG + " APP_TO_LAUNCHER continuous capture start token=" + tokenId
                    + " generation=" + generation + " display=" + displayId);
        });
    }

    static void resolveVisualHoldToOverview() {
        runOnMain(() -> {
            if (!visualHold) return;
            visualHold = false;
            activeTokenId = 0L;
            activeDisplayId = -1;
            transitionSequence++;
            // Exact Overview owns its own 60 FPS RECENTS continuation loop.
            AppGestureBackdropHoldHook.stopAllTransitionCapture("exact-overview");
            MainHook.log(TAG + " transition capture transferred to exact Overview");
        });
    }

    static void finishAppToLauncherVisualHold(long generation, long tokenId, boolean aborted) {
        runOnMain(() -> {
            if (!visualHold || generation != sourceGeneration || tokenId != activeTokenId) return;
            DockLiquidGlassView glass = currentView.get();
            transitionSequence++;
            visualHold = false;
            activeTokenId = 0L;
            activeDisplayId = -1;

            if (glass == null) {
                AppGestureBackdropHoldHook.setSystemUiTransitionActive(false, "finish-no-view");
                return;
            }

            if (aborted) {
                applyStableScene(glass, false);
                AppGestureBackdropHoldHook.setSystemUiTransitionActive(
                        false, "abort-token-" + tokenId);
                glass.prearmAppBackdrop("systemui-transition-abort");
                glass.requestCapture("systemui-transition-abort");
                MainHook.log(TAG + " transition aborted -> APP token=" + tokenId);
                return;
            }

            // HOME becomes authoritative only at the real Shell finish. Up to this point the scene
            // remains APP and mode-1 continuously samples the transformed app/Launcher composition.
            applyStableScene(glass, true);
            AppGestureBackdropHoldHook.setSystemUiTransitionActive(
                    false, "home-finish-token-" + tokenId);
            glass.requestCapture("systemui-transition-home-finished");
            // Shell finish can precede presentation of its final Surface transaction by one VSYNC.
            // Unlike the old design, this is NOT a hold: captures/installations remain enabled;
            // this simply asks for one additional stable HOME sample on the following frame.
            final long sequence = transitionSequence;
            glass.postOnAnimation(() -> {
                if (sequence != transitionSequence || currentView.get() != glass) return;
                glass.requestCapture("systemui-transition-home-post-vsync");
            });
            MainHook.log(TAG + " transition HOME finish; continuous capture settled token="
                    + tokenId);
        });
    }

    static void mergeAppToLauncherVisualHold(long generation, long mergedTokenId,
                                               long playingTokenId, int displayId) {
        runOnMain(() -> {
            if (!visualHold || generation != sourceGeneration
                    || mergedTokenId != activeTokenId || activeDisplayId != displayId) return;
            transitionSequence++;
            activeTokenId = playingTokenId;
            AppGestureBackdropHoldHook.setSystemUiTransitionActive(
                    true, "merged-token-" + playingTokenId);
            MainHook.log(TAG + " transition capture merged " + mergedTokenId
                    + " -> " + playingTokenId);
        });
    }

    static void resolveLauncherToApp(long generation, long tokenId, int displayId) {
        runOnMain(() -> {
            DockLiquidGlassView glass = currentView.get();
            if (glass == null || !matchesDisplay(glass, displayId)) return;
            if (!acceptGeneration(generation)) return;
            transitionSequence++;
            visualHold = false;
            activeTokenId = 0L;
            activeDisplayId = -1;
            AppGestureBackdropHoldHook.setSystemUiTransitionActive(
                    false, "launcher-to-app-token-" + tokenId);
            applyStableScene(glass, false);
            glass.prearmAppBackdrop("systemui-transition-app");
            glass.requestCapture("systemui-transition-app");
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

    private static boolean acceptGeneration(long generation) {
        if (sourceGeneration != Long.MIN_VALUE && generation < sourceGeneration) return false;
        if (sourceGeneration == generation) return true;
        sourceGeneration = generation;
        transitionSequence++;
        visualHold = false;
        activeTokenId = 0L;
        activeDisplayId = -1;
        AppGestureBackdropHoldHook.setSystemUiTransitionActive(false, "generation-reset");
        return true;
    }

    private static boolean matchesDisplay(DockLiquidGlassView glass, int displayId) {
        Display display = glass.getDisplay();
        return display != null && displayId >= 0 && display.getDisplayId() == displayId;
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
