package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Captures user-settled home-grid mutations without depending on vendor drag/resize callbacks.
 *
 * <p>CellLayout.onLayout is only a change signal. The complete Workspace ItemInfo tree is read
 * after a debounce, then a stable fingerprint de-duplicates callbacks. setupViews and physical
 * rotations open a transition window so native transform/restoration intermediate states can
 * never overwrite a remembered orientation.</p>
 */
final class HomeGridMutationCaptureHook {
    private static final String LAUNCHER = "com.miui.home.launcher.Launcher";
    private static final String CELL_LAYOUT = "com.miui.home.launcher.CellLayout";
    private static final String PREFS_NAME = "liquiddock_orientation_layout_memory";
    private static final long MUTATION_SETTLE_MS = 180L;
    private static final long TRANSITION_SETTLE_MS = 650L;

    private static final Object STATE_LOCK = new Object();
    private static final Object RUNTIME_LOCK = new Object();
    private static final HomeGridMutationCapturePolicy CAPTURE_POLICY =
            new HomeGridMutationCapturePolicy();

    private static volatile HomeGridProfile profile;
    private static volatile HomeGridOrientationRuntime runtime;
    private static volatile HomeGridOrientation lastOrientation;
    private static WeakReference<View> workspaceRef = new WeakReference<>(null);
    private static int mutationGeneration;
    private static int transitionGeneration;
    private static boolean installed;

    private HomeGridMutationCaptureHook() {}

    static void install(ClassLoader classLoader, boolean customGridEnabled,
                        HomeGridProfile selectedProfile) {
        if (installed || !customGridEnabled || selectedProfile == null) return;
        profile = selectedProfile;
        try {
            Class<?> launcher = Class.forName(LAUNCHER, false, classLoader);
            Class<?> cellLayout = Class.forName(CELL_LAYOUT, false, classLoader);
            installSetupViewsHook(launcher);
            installConfigurationHook(launcher);
            installCellLayoutHook(cellLayout);
            installed = true;
            MainHook.log("[DC] orientation mutation capture installed profile="
                    + selectedProfile.persistedValue());
        } catch (Throwable error) {
            MainHook.log("[DC] orientation mutation capture unavailable: " + error);
        }
    }

    private static void installSetupViewsHook(Class<?> launcher) {
        HookUtil.hookMethod(launcher, "setupViews", new Class<?>[0], chain -> {
            if (MainHook.isWorkstationMode()) {
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            }

            int transition = beginLayoutTransition();
            Object result;
            try {
                result = chain.proceed(chain.getArgs().toArray(new Object[0]));
            } catch (Throwable error) {
                abortTransition(transition);
                throw error;
            }

            try {
                Object owner = chain.getThisObject();
                View workspace = workspaceFrom(owner);
                HomeGridOrientationRuntime active = runtimeFor(owner);
                if (workspace == null || active == null) {
                    abortTransition(transition);
                    return result;
                }
                workspaceRef = new WeakReference<>(workspace);
                HomeGridOrientation orientation = orientationOf(
                        workspace.getResources().getConfiguration());
                lastOrientation = orientation;
                scheduleTransitionEnd(workspace, orientation, transition);
            } catch (Throwable error) {
                abortTransition(transition);
                MainHook.log("[DC] orientation mutation setup baseline failed: " + error);
            }
            return result;
        });
    }

    private static void installConfigurationHook(Class<?> launcher) {
        HookUtil.hookMethod(launcher, "onConfigurationChanged",
                new Class<?>[]{Configuration.class}, chain -> {
                    Object owner = chain.getThisObject();
                    Configuration targetConfig = (Configuration) chain.getArgs().get(0);
                    HomeGridOrientation targetOrientation = orientationOf(targetConfig);
                    HomeGridOrientation sourceOrientation = lastOrientation;
                    if (sourceOrientation == null) sourceOrientation = targetOrientation.other();
                    boolean physicalRotation = sourceOrientation != targetOrientation;
                    int transition = physicalRotation && !MainHook.isWorkstationMode()
                            ? beginLayoutTransition() : -1;

                    Object result;
                    try {
                        result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    } catch (Throwable error) {
                        if (transition >= 0) abortTransition(transition);
                        throw error;
                    }

                    try {
                        lastOrientation = targetOrientation;
                        View workspace = workspaceFrom(owner);
                        if (workspace != null) workspaceRef = new WeakReference<>(workspace);
                        if (transition >= 0) {
                            if (workspace == null || runtimeFor(owner) == null) {
                                abortTransition(transition);
                            } else {
                                scheduleTransitionEnd(workspace, targetOrientation, transition);
                            }
                        }
                    } catch (Throwable error) {
                        if (transition >= 0) abortTransition(transition);
                        MainHook.log("[DC] orientation mutation rotation baseline failed: " + error);
                    }
                    return result;
                });
    }

    private static void installCellLayoutHook(Class<?> cellLayout) {
        HookUtil.hookMethod(cellLayout, "onLayout",
                new Class<?>[]{boolean.class, int.class, int.class, int.class, int.class},
                chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    if (!MainHook.isWorkstationMode()) scheduleMutationCapture();
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
        if (launcher != null) {
            try {
                Object candidate = HookUtil.getField(launcher, "mWorkspace");
                if (candidate instanceof View) return (View) candidate;
            } catch (Throwable ignored) {}
        }
        return workspaceRef.get();
    }

    private static HomeGridOrientation orientationOf(Configuration configuration) {
        return configuration != null
                && configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                ? HomeGridOrientation.PORTRAIT
                : HomeGridOrientation.LANDSCAPE;
    }

    private static int beginLayoutTransition() {
        synchronized (STATE_LOCK) {
            mutationGeneration++;
            transitionGeneration++;
            CAPTURE_POLICY.beginTransition();
            return transitionGeneration;
        }
    }

    private static void abortTransition(int generation) {
        synchronized (STATE_LOCK) {
            if (generation != transitionGeneration) return;
            CAPTURE_POLICY.endTransition();
        }
    }

    private static void scheduleTransitionEnd(View workspace,
                                              HomeGridOrientation orientation,
                                              int generation) {
        workspace.postDelayed(
                () -> finishTransition(workspace, orientation, generation),
                TRANSITION_SETTLE_MS);
    }

    private static void finishTransition(View workspace,
                                         HomeGridOrientation orientation,
                                         int generation) {
        synchronized (STATE_LOCK) {
            if (generation != transitionGeneration) return;
        }
        if (MainHook.isWorkstationMode() || workspaceRef.get() != workspace) {
            abortTransition(generation);
            return;
        }

        List<HomeGridItemPosition> positions = collectPositions(workspace);
        synchronized (STATE_LOCK) {
            if (generation != transitionGeneration) return;
            if (positions == null) {
                CAPTURE_POLICY.endTransition();
            } else {
                CAPTURE_POLICY.endTransition(
                        orientation, HomeGridLayoutFingerprint.of(positions));
            }
        }
    }

    private static void scheduleMutationCapture() {
        View workspace = workspaceRef.get();
        HomeGridOrientationRuntime active = runtime;
        if (workspace == null || active == null || profile == null) return;

        final int generation;
        synchronized (STATE_LOCK) {
            generation = ++mutationGeneration;
        }
        workspace.postDelayed(
                () -> captureSettledMutation(workspace, active, generation),
                MUTATION_SETTLE_MS);
    }

    private static void captureSettledMutation(View workspace,
                                               HomeGridOrientationRuntime active,
                                               int generation) {
        synchronized (STATE_LOCK) {
            if (generation != mutationGeneration) return;
        }
        if (MainHook.isWorkstationMode() || workspaceRef.get() != workspace) return;

        HomeGridOrientation orientation = lastOrientation;
        if (orientation == null) {
            orientation = orientationOf(workspace.getResources().getConfiguration());
        }
        List<HomeGridItemPosition> positions = collectPositions(workspace);
        if (positions == null) return;

        long fingerprint = HomeGridLayoutFingerprint.of(positions);
        if (!CAPTURE_POLICY.shouldCapture(orientation, fingerprint)) return;

        HomeGridLayoutSnapshot other = active.preflightOther(orientation, positions);
        MainHook.log("[DC] orientation mutation captured=" + orientation
                + " items=" + positions.size()
                + " other=" + (other == null ? "invalidated" : "ready"));
    }

    /** Returns null on duplicate ids or malformed ItemInfo; partial captures are forbidden. */
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
                // Structural views may carry unrelated tags; only complete ItemInfo tags count.
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
}
