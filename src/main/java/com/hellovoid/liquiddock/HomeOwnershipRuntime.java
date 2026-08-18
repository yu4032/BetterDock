package com.hellovoid.liquiddock;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;

import java.lang.ref.WeakReference;

/** Launcher-process bootstrap bridge from async SystemUI HOME/APP baseline results. */
final class HomeOwnershipRuntime {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static WeakReference<DockLiquidGlassView> currentView = new WeakReference<>(null);
    private static HomeOwnershipResolver resolver;
    private static HomeOwnershipPolicy.Baseline appliedBaseline =
            HomeOwnershipPolicy.Baseline.UNKNOWN;

    private HomeOwnershipRuntime() {}

    static void bind(DockLiquidGlassView glass, Context context) {
        if (glass == null || context == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> bind(glass, context));
            return;
        }
        currentView = new WeakReference<>(glass);
        appliedBaseline = HomeOwnershipPolicy.Baseline.UNKNOWN;
        SystemUiTransitionRuntime.bind(glass, context);
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
        // Launcher focus is not a transition authority. Old compatibility hooks may still emit
        // this refresh on non-307 builds; keep it inert instead of manufacturing UNKNOWN churn.
        if ("focus".equals(reason) || "miuix307-focus".equals(reason)) return;
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
        if (SystemUiTransitionRuntime.isVisualHoldActive(glass)) {
            Api101Bridge.log("[DC] HOME bootstrap ignored during transition reason=" + reason);
            return;
        }
        HomeOwnershipPolicy.Baseline next = baseline != null
                ? baseline : HomeOwnershipPolicy.Baseline.UNKNOWN;
        appliedBaseline = next;

        if (next == HomeOwnershipPolicy.Baseline.UNKNOWN) {
            glass.setLauncherState(false, false);
            Api101Bridge.log("[DC] HOME bootstrap baseline=UNKNOWN reason=" + reason);
            return;
        }

        if (next == HomeOwnershipPolicy.Baseline.APP) {
            glass.setLauncherState(true, false);
            Api101Bridge.log("[DC] HOME bootstrap baseline=APP reason=" + reason);
            return;
        }

        glass.setLauncherState(true, true);
        Api101Bridge.log("[DC] HOME bootstrap baseline=HOME reason=" + reason);
    }

    static HomeOwnershipPolicy.Baseline appliedBaselineForTests() {
        return appliedBaseline;
    }
}
