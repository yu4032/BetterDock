package com.hellovoid.liquiddock;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;

import java.lang.ref.WeakReference;

/** Launcher-process bridge from async SystemUI baseline results to the current glass view. */
final class HomeOwnershipRuntime {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static WeakReference<DockLiquidGlassView> currentView = new WeakReference<>(null);
    private static HomeOwnershipResolver resolver;
    private static HomeOwnershipPolicy.Baseline appliedBaseline =
            HomeOwnershipPolicy.Baseline.UNKNOWN;
    // Retained only to choose transition side effects (APP prearm / HOME settle). It is never
    // used as capture ownership while appliedBaseline is UNKNOWN.
    private static HomeOwnershipPolicy.Baseline lastConfirmedBaseline =
            HomeOwnershipPolicy.Baseline.UNKNOWN;

    private HomeOwnershipRuntime() {}

    static void bind(DockLiquidGlassView glass, Context context) {
        if (glass == null || context == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> bind(glass, context));
            return;
        }
        currentView = new WeakReference<>(glass);
        WallpaperZoomRuntime.bind(glass);
        appliedBaseline = HomeOwnershipPolicy.Baseline.UNKNOWN;
        lastConfirmedBaseline = HomeOwnershipPolicy.Baseline.UNKNOWN;
        glass.setLauncherState(false, false);

        if (resolver == null) {
            resolver = new HomeOwnershipResolver(context, HomeOwnershipRuntime::applyBaseline);
        }
        request("bind");
        // setupViews can bind before the view has an attached Display. Retry once on the next
        // View turn; a missing display remains UNKNOWN rather than guessing display 0.
        glass.post(() -> {
            if (currentView.get() == glass) request("bind-post");
        });
    }

    static void request(String reason) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> request(reason));
            return;
        }
        DockLiquidGlassView glass = currentView.get();
        HomeOwnershipResolver currentResolver = resolver;
        if (glass == null || currentResolver == null) return;
        Display display = glass.getDisplay();
        int displayId = display != null ? display.getDisplayId() : -1;
        currentResolver.request(displayId, reason);
    }

    private static void applyBaseline(HomeOwnershipPolicy.Baseline baseline, String reason) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> applyBaseline(baseline, reason));
            return;
        }
        DockLiquidGlassView glass = currentView.get();
        if (glass == null) return;
        HomeOwnershipPolicy.Baseline next = baseline != null
                ? baseline : HomeOwnershipPolicy.Baseline.UNKNOWN;
        HomeOwnershipPolicy.Baseline previousConfirmed = lastConfirmedBaseline;
        appliedBaseline = next;

        if (next == HomeOwnershipPolicy.Baseline.UNKNOWN) {
            glass.setLauncherState(false, false);
            Api101Bridge.log("[DC] HOME ownership baseline=UNKNOWN reason=" + reason);
            return;
        }

        if (next == HomeOwnershipPolicy.Baseline.APP) {
            if (previousConfirmed != HomeOwnershipPolicy.Baseline.APP) {
                glass.onLauncherFocusLost();
            }
            glass.setLauncherState(true, false);
            if (previousConfirmed != HomeOwnershipPolicy.Baseline.APP) {
                glass.prearmAppBackdrop("systemui-ownership-" + reason);
            }
            lastConfirmedBaseline = HomeOwnershipPolicy.Baseline.APP;
            Api101Bridge.log("[DC] HOME ownership baseline=APP reason=" + reason);
            return;
        }

        glass.setLauncherState(true, true);
        if (previousConfirmed == HomeOwnershipPolicy.Baseline.APP) {
            glass.onLauncherFocused();
        }
        lastConfirmedBaseline = HomeOwnershipPolicy.Baseline.HOME;
        Api101Bridge.log("[DC] HOME ownership baseline=HOME reason=" + reason);
    }

    static void onRecentsExitAnimationChanged(boolean active) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> onRecentsExitAnimationChanged(active));
            return;
        }
        DockLiquidGlassView glass = currentView.get();
        if (glass == null) return;
        glass.setOverviewActive(active,
                active ? "launcher-exit-animation-start" : "launcher-exit-animation-end");
        Api101Bridge.log("[DC] Recents exit animation active=" + active);
    }

    static HomeOwnershipPolicy.Baseline appliedBaselineForTests() {
        return appliedBaseline;
    }
}
