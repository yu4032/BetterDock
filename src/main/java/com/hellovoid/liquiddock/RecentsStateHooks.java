package com.hellovoid.liquiddock;

import android.view.View;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Stock HyperOS Recents authority and fallback event integration. */
final class RecentsStateHooks {
    private final Supplier<DockLiquidGlassView> glassProvider;
    private final BooleanSupplier workstationModeProvider;
    private final Consumer<String> logger;

    RecentsStateHooks(Supplier<DockLiquidGlassView> glassProvider,
                      BooleanSupplier workstationModeProvider,
                      Consumer<String> logger) {
        this.glassProvider = glassProvider;
        this.workstationModeProvider = workstationModeProvider;
        this.logger = logger;
    }

    boolean installStock(ClassLoader cl) {
        boolean mainObserverHooked = false;
        boolean recentsListenerHooked = false;
        try {
            Class<?> observer = Class.forName(
                    "com.miui.home.launcher.dock.v3.state.DockStateManager$mainStateObserver$1",
                    false, cl);
            Class<?> recentParam = Class.forName(
                    "com.miui.home.launcher.dock.v3.state.partial.RecentParam", false, cl);
            HookUtil.hookMethod(observer, "onEnterRecent", new Class<?>[]{recentParam},
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        DockLiquidGlassView glass = glassProvider.get();
                        if (glass != null) glass.setOverviewActive(true, "stock-main-enter");
                        return result;
                    });
            HookUtil.hookMethod(observer, "onExitRecent", new Class<?>[0],
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        DockLiquidGlassView glass = glassProvider.get();
                        if (glass != null) glass.setOverviewActive(false, "stock-main-exit");
                        return result;
                    });
            mainObserverHooked = true;
        } catch (Throwable e) {
            logger.accept("[DC] stock Recents main observer hook unavailable: " + e);
        }
        try {
            Class<?> listener = Class.forName(
                    "com.miui.home.launcher.dock.v3.state.DockStateManager$recentsListener$1",
                    false, cl);
            HookUtil.hookMethod(listener, "onRecentViewShow", new Class<?>[0],
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        DockLiquidGlassView glass = glassProvider.get();
                        if (glass != null) glass.setOverviewActive(true, "stock-view-show");
                        return result;
                    });
            HookUtil.hookMethod(listener, "onRecentViewHide", new Class<?>[0],
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        DockLiquidGlassView glass = glassProvider.get();
                        if (glass != null) glass.setOverviewActive(false, "stock-view-hide");
                        return result;
                    });
            HookUtil.hookMethod(listener, "onRecentViewAnimationComplete", new Class<?>[0],
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        DockLiquidGlassView glass = glassProvider.get();
                        if (glass != null) glass.requestCapture("stock-recents-animation-complete");
                        return result;
                    });
            recentsListenerHooked = true;
        } catch (Throwable e) {
            logger.accept("[DC] stock Recents listener hook unavailable: " + e);
        }
        boolean installed = mainObserverHooked && recentsListenerHooked;
        logger.accept("[DC] stock Recents authority=" + installed
                + " main=" + mainObserverHooked + " listener=" + recentsListenerHooked);
        return installed;
    }

    void installFallback(ClassLoader cl) {
        hookOverviewStateEvent(cl, "EnterOverviewStateEvent", true);
        hookOverviewStateEvent(cl, "ExitOverviewStateEvent", false);
    }

    void bindRecentsView(DockLiquidGlassView glass, Object launcher) {
        try {
            Object panel = HookUtil.getField(launcher, "mOverviewPanel");
            if (panel instanceof View) glass.setRecentsView((View) panel);
        } catch (Throwable e) {
            logger.accept("[DC] recents bind failed: " + e);
        }
    }

    private void hookOverviewStateEvent(ClassLoader cl, String eventName, boolean active) {
        try {
            Class<?> eventClass = Class.forName("com.miui.home.recents.event." + eventName, false, cl);
            for (java.lang.reflect.Constructor<?> ctor : eventClass.getDeclaredConstructors()) {
                HookUtil.hook(ctor, chain -> {
                    Object r = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    DockLiquidGlassView glass = glassProvider.get();
                    if (glass != null && !workstationModeProvider.getAsBoolean())
                        glass.setOverviewActive(active, eventName);
                    if (!workstationModeProvider.getAsBoolean())
                        logger.accept("[DC] liquid overview active=" + active + " event=" + eventName);
                    return r;
                });
            }
        } catch (Throwable e) {
            logger.accept("[DC] " + eventName + " capture state hook unavailable: " + e);
        }
    }
}
