package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Launcher adapter for per-orientation layout memory.
 *
 * <p>The adapter intentionally stays above MIUI's native occupancy implementation: it captures
 * and restores the same ItemInfo tag fields already used by LiquidDock's workstation layout
 * backup, while LayoutTransformRuleGridChanged keeps ownership of native rotation matrices.</p>
 */
final class HomeGridOrientationMemoryHook {
    private static final String LAUNCHER = "com.miui.home.launcher.Launcher";
    private static final String PREFS_NAME = "liquiddock_orientation_layout_memory";
    private static final long SETTLE_DELAY_MS = 500L;
    private static final long MID_DELAY_MS = 180L;

    private static final Object RUNTIME_LOCK = new Object();
    private static volatile HomeGridProfile profile;
    private static volatile HomeGridOrientationRuntime runtime;
    private static volatile HomeGridOrientation lastOrientation;
    private static WeakReference<View> workspaceRef = new WeakReference<>(null);

    private HomeGridOrientationMemoryHook() {}

    static void install(ClassLoader classLoader, boolean customGridEnabled,
                        HomeGridProfile selectedProfile) {
        if (!customGridEnabled || selectedProfile == null) return;
        profile = selectedProfile;
        try {
            Class<?> launcher = Class.forName(LAUNCHER, false, classLoader);
            installSetupViewsHook(launcher);
            installConfigurationHook(launcher);
            MainHook.log("[DC] orientation layout memory installed profile="
                    + selectedProfile.persistedValue());
        } catch (Throwable error) {
            MainHook.log("[DC] orientation layout memory unavailable: " + error);
        }
    }

    private static void installSetupViewsHook(Class<?> launcher) {
        HookUtil.hookMethod(launcher, "setupViews", chain -> {
            Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
            try {
                Object owner = chain.getThisObject();
                View workspace = workspaceFrom(owner);
                HomeGridOrientationRuntime active = runtimeFor(owner);
                if (workspace == null || active == null) return result;
                workspaceRef = new WeakReference<>(workspace);
                HomeGridOrientation currentOrientation = orientationOf(
                        workspace.getResources().getConfiguration());
                lastOrientation = currentOrientation;
                scheduleTargetResolution(workspace, currentOrientation, active);
            } catch (Throwable error) {
                MainHook.log("[DC] orientation layout setup resolve failed: " + error);
            }
            return result;
        });
    }

    private static void installConfigurationHook(Class<?> launcher) {
        HookUtil.hookMethod(launcher, "onConfigurationChanged",
                new Class[]{Configuration.class}, chain -> {
                    Object owner = chain.getThisObject();
                    Configuration targetConfig = (Configuration) chain.getArgs().get(0);
                    HomeGridOrientation targetOrientation = orientationOf(targetConfig);
                    HomeGridOrientation sourceOrientation = lastOrientation;
                    if (sourceOrientation == null) sourceOrientation = targetOrientation.other();

                    HomeGridOrientationRuntime active = runtimeFor(owner);
                    View sourceWorkspace = workspaceFrom(owner);
                    List<HomeGridItemPosition> sourcePositions = sourceWorkspace == null
                            ? null : collectPositions(sourceWorkspace);
                    boolean physicalRotation = sourceOrientation != targetOrientation;
                    if (physicalRotation && active != null && sourcePositions != null) {
                        active.captureCurrent(sourceOrientation, sourcePositions);
                    }

                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));

                    try {
                        View targetWorkspace = workspaceFrom(owner);
                        if (targetWorkspace != null) workspaceRef = new WeakReference<>(targetWorkspace);
                        lastOrientation = targetOrientation;
                        if (physicalRotation && targetWorkspace != null && active != null) {
                            scheduleTargetResolution(targetWorkspace, targetOrientation, active);
                        }
                    } catch (Throwable error) {
                        MainHook.log("[DC] orientation layout rotation resolve failed: " + error);
                    }
                    return result;
                });
    }

    private static HomeGridOrientationRuntime runtimeFor(Object launcher) {
        HomeGridOrientationRuntime current = runtime;
        if (current != null) return current;
        if (!(launcher instanceof Context) || profile == null) return null;
        synchronized (RUNTIME_LOCK) {
            if (runtime != null) return runtime;
            SharedPreferences preferences = ((Context) launcher).getSharedPreferences(
                    PREFS_NAME, Context.MODE_PRIVATE);
            HomeGridOrientationMemory memory = new HomeGridOrientationMemory(
                    new HomeGridSharedPreferencesMemoryStore(preferences));
            runtime = new HomeGridOrientationRuntime(profile, memory);
            return runtime;
        }
    }

    private static View workspaceFrom(Object launcher) {
        if (launcher == null) return workspaceRef.get();
        try {
            Object candidate = HookUtil.getField(launcher, "mWorkspace");
            if (candidate instanceof View) return (View) candidate;
        } catch (Throwable ignored) {}
        return workspaceRef.get();
    }

    private static HomeGridOrientation orientationOf(Configuration configuration) {
        return configuration != null
                && configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                ? HomeGridOrientation.PORTRAIT
                : HomeGridOrientation.LANDSCAPE;
    }

    private static void scheduleTargetResolution(View workspace,
                                                 HomeGridOrientation targetOrientation,
                                                 HomeGridOrientationRuntime active) {
        if (workspace == null || targetOrientation == null || active == null) return;
        workspace.post(() -> resolveTarget(workspace, targetOrientation, active, false));
        workspace.postDelayed(
                () -> resolveTarget(workspace, targetOrientation, active, false), MID_DELAY_MS);
        workspace.postDelayed(
                () -> resolveTarget(workspace, targetOrientation, active, true), SETTLE_DELAY_MS);
    }

    private static void resolveTarget(View workspace,
                                      HomeGridOrientation targetOrientation,
                                      HomeGridOrientationRuntime active,
                                      boolean finalAttempt) {
        List<HomeGridItemPosition> current = collectPositions(workspace);
        if (current == null) return;
        HomeGridLayoutSnapshot remembered = active.rememberedTarget(targetOrientation, current);
        if (remembered != null) {
            if (applySnapshotAtomically(workspace, remembered)) {
                HomeGridHook.scheduleAllPageRefresh();
            }
            return;
        }
        if (finalAttempt) {
            HomeGridLayoutSnapshot captured = active.captureCurrent(targetOrientation, current);
            if (captured != null) {
                MainHook.log("[DC] orientation layout captured native target="
                        + targetOrientation + " items=" + captured.size());
            }
        }
    }

    /** Returns null on duplicate ids or unreadable item metadata; partial captures are forbidden. */
    private static List<HomeGridItemPosition> collectPositions(View root) {
        if (root == null) return null;
        ArrayList<HomeGridItemPosition> positions = new ArrayList<>();
        HashSet<Long> ids = new HashSet<>();
        if (!collectPositionsRecursive(root, positions, ids)) return null;
        return positions;
    }

    private static boolean collectPositionsRecursive(View view,
                                                     List<HomeGridItemPosition> out,
                                                     Set<Long> ids) {
        Object tag = view.getTag();
        if (tag != null) {
            try {
                long id = HookUtil.getLongField(tag, "id");
                if (id >= 0) {
                    long screenId = HookUtil.getLongField(tag, "screenId");
                    int cellX = HookUtil.getIntField(tag, "cellX");
                    int cellY = HookUtil.getIntField(tag, "cellY");
                    int spanX = HookUtil.getIntField(tag, "spanX");
                    int spanY = HookUtil.getIntField(tag, "spanY");
                    if (spanX <= 0 || spanY <= 0 || !ids.add(id)) return false;
                    out.add(new HomeGridItemPosition(
                            id, screenId, cellX, cellY, spanX, spanY));
                }
            } catch (Throwable ignored) {
                // Many structural views have arbitrary tags. Ignore tags that are not ItemInfo.
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                if (!collectPositionsRecursive(group.getChildAt(index), out, ids)) return false;
            }
        }
        return true;
    }

    /**
     * Preflight every target id and screen before mutating any ItemInfo. Cross-screen moves are
     * deliberately rejected because changing screenId alone cannot safely reparent the View.
     */
    private static boolean applySnapshotAtomically(View workspace,
                                                   HomeGridLayoutSnapshot snapshot) {
        if (workspace == null || snapshot == null) return false;
        HashMap<Long, Object> tags = new HashMap<>();
        if (!collectItemTags(workspace, tags) || tags.size() != snapshot.size()) return false;

        for (HomeGridItemPosition target : snapshot.positions()) {
            Object tag = tags.get(target.itemId());
            if (tag == null) return false;
            try {
                if (HookUtil.getLongField(tag, "screenId") != target.screenId()) return false;
            } catch (Throwable error) {
                return false;
            }
        }

        try {
            for (HomeGridItemPosition target : snapshot.positions()) {
                Object tag = tags.get(target.itemId());
                HookUtil.setLongField(tag, "screenId", target.screenId());
                HookUtil.setIntField(tag, "cellX", target.cellX());
                HookUtil.setIntField(tag, "cellY", target.cellY());
                HookUtil.setIntField(tag, "spanX", target.spanX());
                HookUtil.setIntField(tag, "spanY", target.spanY());
            }
        } catch (Throwable error) {
            MainHook.log("[DC] orientation snapshot apply failed: " + error);
            return false;
        }

        requestLayoutRecursively(workspace);
        workspace.invalidate();
        MainHook.log("[DC] orientation layout restored target=" + snapshot.orientation()
                + " items=" + snapshot.size());
        return true;
    }

    private static boolean collectItemTags(View view, Map<Long, Object> out) {
        Object tag = view.getTag();
        if (tag != null) {
            try {
                long id = HookUtil.getLongField(tag, "id");
                if (id >= 0) {
                    HookUtil.getLongField(tag, "screenId");
                    HookUtil.getIntField(tag, "cellX");
                    HookUtil.getIntField(tag, "cellY");
                    HookUtil.getIntField(tag, "spanX");
                    HookUtil.getIntField(tag, "spanY");
                    if (out.put(id, tag) != null) return false;
                }
            } catch (Throwable ignored) {
                // Structural/non-ItemInfo tag.
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                if (!collectItemTags(group.getChildAt(index), out)) return false;
            }
        }
        return true;
    }

    private static void requestLayoutRecursively(View view) {
        view.requestLayout();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                requestLayoutRecursively(group.getChildAt(index));
            }
        }
    }
}
