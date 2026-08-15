from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected exactly one match, found {count}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


# 1) Pure workstation capture-burst convergence state.
Path("src/main/java/com/hellovoid/liquiddock/WorkstationCaptureBurst.java").write_text(
'''package com.hellovoid.liquiddock;

/** Workstation-only capture burst: keep sampling until the backdrop converges. */
final class WorkstationCaptureBurst {
    private boolean active;
    private boolean signatureValid;
    private long lastSignature;
    private int stableComparisons;

    void start() {
        active = true;
        signatureValid = false;
        stableComparisons = 0;
    }

    void stop() {
        active = false;
        signatureValid = false;
        stableComparisons = 0;
    }

    boolean isActive() {
        return active;
    }

    /** Returns true while another sample is required. */
    boolean onFrame(long signature) {
        if (!active) return false;
        if (!signatureValid) {
            signatureValid = true;
            lastSignature = signature;
            stableComparisons = 0;
            return true;
        }
        if (lastSignature != signature) {
            lastSignature = signature;
            stableComparisons = 0;
            return true;
        }
        stableComparisons++;
        if (stableComparisons >= 2) {
            active = false;
            return false;
        }
        return true;
    }
}
''', encoding="utf-8")


# 2) Workstation launcher-owned scenes prefer their own ViewRoot; safe full-display fallback otherwise.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/CaptureSourcePolicy.java",
'''        // All Apps stays wallpaper-backed. localLayerAvailable is retained for source/API
        // compatibility with the 8ee84ed baseline but does not grant live capture authority.
        return Source.WALLPAPER;
    }
}
''',
'''        // All Apps stays wallpaper-backed. localLayerAvailable is retained for source/API
        // compatibility with the 8ee84ed baseline but does not grant live capture authority.
        return Source.WALLPAPER;
    }

    /** Workstation All Apps/Recents are live Launcher scenes. */
    static Source sourceForWorkstationScene(CaptureScene scene, boolean localLayerAvailable) {
        if (scene == CaptureScene.ALL_APPS || scene == CaptureScene.RECENTS) {
            return localLayerAvailable ? Source.LOCAL_LAYER : Source.FULL_DISPLAY;
        }
        // Outside the two workstation-owned live scenes, keep the existing wallpaper baseline.
        return Source.WALLPAPER;
    }
}
''')


view_path = "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java"

# 3) Workstation burst state lives beside the existing workstation session latches.
replace_once(
    view_path,
'''    private boolean workstationMode;
    private boolean workstationRecentsActive;
    private boolean workstationRecentsWasVisible;
    private long lastCaptureStartNanos;
''',
'''    private boolean workstationMode;
    private boolean workstationRecentsActive;
    private boolean workstationRecentsWasVisible;
    private final WorkstationCaptureBurst workstationCaptureBurst = new WorkstationCaptureBurst();
    private boolean workstationSuspendWhenBurstSettles;
    private long lastCaptureStartNanos;
''')

# 4) Observation changes in an active workstation scene re-arm a bounded sampling burst.
replace_once(
    view_path,
'''        if (updateObservation()) {
            requestStateCapture("observation");
        }
''',
'''        if (updateObservation()) {
            if (workstationMode && (sceneState.allAppsActive() || isRecentsVisible())) {
                workstationCaptureBurst.start();
            }
            requestStateCapture("observation");
        }
''')

# 5) All Apps entry and exit are explicit workstation refresh boundaries.
replace_once(
    view_path,
'''    void setAllAppsActive(boolean active, View captureRoot) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setAllAppsActive(active, captureRoot));
            return;
        }
        boolean rootChanged = active && captureRoot != null && allAppsCaptureRoot != captureRoot;
        if (active && captureRoot != null) allAppsCaptureRoot = captureRoot;
        if (!active) allAppsCaptureRoot = null;
        boolean stateChanged = sceneState.allAppsActive() != active;
        sceneState.setAllAppsActive(active);
        if (!stateChanged && !rootChanged) return;
        observationValid = false;
        lastCaptureStartNanos = 0L;
        updateDesiredScene();
        requestStateCapture(active ? "all-apps-enter" : "all-apps-exit");
    }
''',
'''    void setAllAppsActive(boolean active, View captureRoot) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setAllAppsActive(active, captureRoot));
            return;
        }
        boolean rootChanged = active && captureRoot != null && allAppsCaptureRoot != captureRoot;
        if (active && captureRoot != null) allAppsCaptureRoot = captureRoot;
        boolean stateChanged = sceneState.allAppsActive() != active;
        sceneState.setAllAppsActive(active);
        if (!stateChanged && !rootChanged) return;
        observationValid = false;
        lastCaptureStartNanos = 0L;
        if (workstationMode) {
            workstationSuspendWhenBurstSettles = !active;
            if (active) {
                startWorkstationCaptureBurst("all-apps-enter");
            } else {
                startWorkstationCaptureBurst("all-apps-exit");
            }
        }
        updateDesiredScene();
        requestStateCapture(active ? "all-apps-enter" : "all-apps-exit");
        if (!active) allAppsCaptureRoot = null;
    }
''')

# 6) Exact Overview lifecycle also refreshes/re-arms the workstation boundary.
replace_once(
    view_path,
'''        overviewActive = active;
        observationValid = false;
        lastCaptureStartNanos = 0L;
        if (active) {
            sceneState.setGestureTarget("RECENTS", System.nanoTime());
        } else if (sceneState.desired() == CaptureScene.RECENTS) {
            sceneState.clearGestureTarget();
        }
        updateDesiredScene();
        requestStateCapture(active ? "overview-enter-" + reason : "overview-exit-" + reason);
''',
'''        overviewActive = active;
        observationValid = false;
        lastCaptureStartNanos = 0L;
        if (workstationMode && workstationRecentsActive) {
            workstationSuspendWhenBurstSettles = !active;
            if (active) {
                startWorkstationCaptureBurst("workstation-recents-enter");
            } else {
                startWorkstationCaptureBurst("workstation-recents-exit");
            }
        }
        if (active) {
            sceneState.setGestureTarget("RECENTS", System.nanoTime());
        } else if (sceneState.desired() == CaptureScene.RECENTS) {
            sceneState.clearGestureTarget();
        }
        updateDesiredScene();
        requestStateCapture(active ? "overview-enter-" + reason : "overview-exit-" + reason);
''')

# 7) Do not hard-suspend a closing Recents session while its exit burst is still sampling.
replace_once(
    view_path,
'''            if (visible) {
                workstationRecentsWasVisible = true;
            } else if (workstationRecentsWasVisible) {
                suspendWorkstationGlass("workstation-recents-hidden");
                return;
            }
''',
'''            if (visible) {
                workstationRecentsWasVisible = true;
            } else if (workstationRecentsWasVisible) {
                if (workstationCaptureBurst.isActive()) {
                    workstationSuspendWhenBurstSettles = true;
                } else {
                    suspendWorkstationGlass("workstation-recents-hidden");
                    return;
                }
            }
''')

# 8) Active workstation live scenes bypass normal Floating-Dock visibility gating.
replace_once(
    view_path,
'''        // A real APP focus transition pre-arms only a short bounded window. This bypasses
        // Dock visibility long enough to install one or two mode-1 frames while the Dock is
        // still collapsed, but cannot turn into the normal hidden-APP capture loop.
        if (appBackdropPrearmActive && sceneState.desired() == CaptureScene.APP) {
''',
'''        // Workstation All Apps/Recents intentionally draw through a different Dock container;
        // their capture burst must not depend on the normal Floating Dock View being visible.
        if (workstationMode && (workstationCaptureBurst.isActive()
                || sceneState.allAppsActive() || workstationRecentsActive)) {
            lastAllowedNanos = System.nanoTime();
            return true;
        }

        // A real APP focus transition pre-arms only a short bounded window. This bypasses
        // Dock visibility long enough to install one or two mode-1 frames while the Dock is
        // still collapsed, but cannot turn into the normal hidden-APP capture loop.
        if (appBackdropPrearmActive && sceneState.desired() == CaptureScene.APP) {
''')

# 9) Leaving workstation resets the workstation-only convergence state.
replace_once(
    view_path,
'''        workstationRecentsActive = false;
        workstationRecentsWasVisible = false;
        cancelPendingCaptureWork();
''',
'''        workstationRecentsActive = false;
        workstationRecentsWasVisible = false;
        workstationCaptureBurst.stop();
        workstationSuspendWhenBurstSettles = false;
        cancelPendingCaptureWork();
''')

# 10) Dedicated Recents button owns both enter and exit refresh boundaries.
replace_once(
    view_path,
'''        // A second press is the exit toggle. Keep live capture through the closing
        // animation; recents visibility dropping will suspend the glass afterward.
        if (workstationRecentsActive) {
            logI("workstation Recents exit requested; waiting for panel hide");
            return;
        }

        workstationRecentsActive = true;
        workstationRecentsWasVisible = false;
        cancelPendingCaptureWork();
        captureGeneration++;
        appVisualSignatureValid = false;
        dynamicAppActiveUntilNanos = 0L;
        long now = System.nanoTime();
        sceneState.setWorkstationSuspended(false, now, isRecentsVisible(),
                launcherLifecycleKnown, launcherResumed);
        // Exact button boundary is authoritative long enough for the overview animation
        // to become visible; once visible, normal Recents visibility owns the scene.
        sceneState.setGestureTarget("RECENTS", now);
        setVisibility(VISIBLE);
        // Never reveal the normal HotSeats background in workstation. The independent
        // DockContainerView remains underneath; this glass draws only the live Recents frame.
        geometrySource.setAlpha(0f);
        nativeBackgroundHiddenByGlass = true;
        sourceDirty = true;
        observationValid = false;
        lastCaptureStartNanos = 0L;
        requestStateCapture("workstation-recents-button");
''',
'''        // A second press is the exit toggle. Force a fresh frame and keep a bounded
        // workstation burst alive through the closing animation.
        if (workstationRecentsActive) {
            workstationSuspendWhenBurstSettles = true;
            startWorkstationCaptureBurst("workstation-recents-exit");
            requestStateCapture("workstation-recents-exit");
            logI("workstation Recents exit requested; adaptive capture armed");
            return;
        }

        workstationRecentsActive = true;
        workstationRecentsWasVisible = false;
        workstationSuspendWhenBurstSettles = false;
        startWorkstationCaptureBurst("workstation-recents-enter");
        long now = System.nanoTime();
        // Exact button boundary is authoritative long enough for the overview animation
        // to become visible; once visible, normal Recents visibility owns the scene.
        sceneState.setGestureTarget("RECENTS", now);
        sourceDirty = true;
        observationValid = false;
        lastCaptureStartNanos = 0L;
        requestStateCapture("workstation-recents-enter");
''')

# 11) Shared workstation burst activation/suspension helpers.
replace_once(
    view_path,
'''    private void suspendWorkstationGlass(String reason) {
        cancelPendingCaptureWork();
''',
'''    private void startWorkstationCaptureBurst(String reason) {
        if (!workstationMode) return;
        resetCaptureCircuit(reason);
        workstationCaptureBurst.start();
        cancelPendingCaptureWork();
        appVisualSignatureValid = false;
        dynamicAppActiveUntilNanos = 0L;
        sceneState.setWorkstationSuspended(false, System.nanoTime(), isRecentsVisible(),
                launcherLifecycleKnown, launcherResumed);
        setVisibility(VISIBLE);
        // Never reveal the normal HotSeats background in workstation. The independent
        // workstation Dock remains underneath this glass composition.
        geometrySource.setAlpha(0f);
        nativeBackgroundHiddenByGlass = true;
        sourceDirty = true;
        observationValid = false;
        lastCaptureStartNanos = 0L;
        logI("workstation capture burst started reason=" + reason);
    }

    private void finishWorkstationCaptureBurstIfSettled() {
        if (!workstationMode || workstationCaptureBurst.isActive()) return;
        logI("workstation capture burst stable scene=" + sceneState.desired());
        if (!workstationSuspendWhenBurstSettles) return;
        // If the closing scene is still visibly active, its lifecycle callback will perform
        // the final suspension when it actually disappears.
        if (sceneState.allAppsActive() || isRecentsVisible()) return;
        suspendWorkstationGlass("workstation-background-stable");
    }

    private void suspendWorkstationGlass(String reason) {
        workstationCaptureBurst.stop();
        workstationSuspendWhenBurstSettles = false;
        cancelPendingCaptureWork();
''')

# 12) Workstation bursts always use live capture; workstation All Apps/Recents use dedicated policy.
replace_once(
    view_path,
'''        final boolean useFullscreen = fullscreenCapture;

        final long generation = captureGeneration;
        updateDesiredScene();
        final CaptureScene requestScene = sceneState.desired();
        final long requestSceneRevision = sceneState.revision();
        final android.view.SurfaceControl localCaptureSurface = useFullscreen
                ? resolveLauncherOwnedCaptureSurface(requestScene) : null;
        final CaptureSourcePolicy.Source requestedSource;
        if (!useFullscreen || (workstationMode && requestScene == CaptureScene.APP)) {
            requestedSource = CaptureSourcePolicy.Source.WALLPAPER;
        } else {
            requestedSource = CaptureSourcePolicy.sourceFor(
                    requestScene, localCaptureSurface != null, isRecentsVisible());
        }
''',
'''        final boolean useFullscreen = fullscreenCapture
                || (workstationMode && workstationCaptureBurst.isActive());

        final long generation = captureGeneration;
        updateDesiredScene();
        final CaptureScene requestScene = sceneState.desired();
        final long requestSceneRevision = sceneState.revision();
        final android.view.SurfaceControl localCaptureSurface = useFullscreen
                ? resolveLauncherOwnedCaptureSurface(requestScene) : null;
        CaptureSourcePolicy.Source selectedSource;
        if (!useFullscreen) {
            selectedSource = CaptureSourcePolicy.Source.WALLPAPER;
        } else if (workstationMode) {
            selectedSource = CaptureSourcePolicy.sourceForWorkstationScene(
                    requestScene, localCaptureSurface != null);
        } else {
            selectedSource = CaptureSourcePolicy.sourceFor(
                    requestScene, localCaptureSurface != null, isRecentsVisible());
        }
        if (workstationMode && selectedSource == CaptureSourcePolicy.Source.FULL_DISPLAY) {
            if (!hasValidDockWindowSurface()) dockWindowSurface = resolveWindowSurfaceControl();
            // Never run an unsafe workstation full-display fallback: if the Dock cannot be
            // excluded by handle or layer name, wallpaper is preferable to sampling icons.
            if (!hasValidDockWindowSurface() && dockWindowLayerName == null) {
                selectedSource = CaptureSourcePolicy.Source.WALLPAPER;
            }
        }
        final CaptureSourcePolicy.Source requestedSource = selectedSource;
''')

# 13) Full-display fallback, including workstation local-layer fallback, always prepares Dock exclusion.
replace_once(
    view_path,
'''        boolean needsDockExclude = useFullscreen
                && requestedSource == CaptureSourcePolicy.Source.FULL_DISPLAY
                && !workstationMode;
''',
'''        boolean needsDockExclude = useFullscreen
                && (requestedSource == CaptureSourcePolicy.Source.FULL_DISPLAY
                    || (workstationMode
                        && requestedSource == CaptureSourcePolicy.Source.LOCAL_LAYER));
''')

replace_once(
    view_path,
'''                    android.view.SurfaceControl[] excludes = null;
                    if (requestedSource == CaptureSourcePolicy.Source.FULL_DISPLAY
                            && dockWindowSurface != null) {
                        excludes = new android.view.SurfaceControl[]{dockWindowSurface};
                    }
''',
'''                    android.view.SurfaceControl[] excludes = null;
                    if ((requestedSource == CaptureSourcePolicy.Source.FULL_DISPLAY
                            || (workstationMode
                                && requestedSource == CaptureSourcePolicy.Source.LOCAL_LAYER))
                            && dockWindowSurface != null) {
                        excludes = new android.view.SurfaceControl[]{dockWindowSurface};
                    }
''')

# 14) Local-layer failure in workstation falls back to mode-1 only when Dock exclusion exists.
replace_once(
    view_path,
'''                            @Override public void onError(Throwable error) {
                                logW("local launcher-layer capture failed; wallpaper fallback: " + error);
                                captureClient.captureScreenAsync(req.stripRect, captureScale,
                                        req.displayId, null, null, 2, captureCb);
                            }
''',
'''                            @Override public void onError(Throwable error) {
                                if (workstationMode
                                        && (hasValidDockWindowSurface() || dockWindowLayerName != null)) {
                                    logW("local launcher-layer capture failed; safe full-display fallback: "
                                            + error);
                                    android.view.SurfaceControl[] fallbackExcludes = dockWindowSurface != null
                                            ? new android.view.SurfaceControl[]{dockWindowSurface} : null;
                                    String[] fallbackNames = dockWindowLayerName != null
                                            ? new String[]{dockWindowLayerName} : null;
                                    captureClient.captureScreenAsync(req.stripRect, captureScale,
                                            req.displayId, fallbackExcludes, fallbackNames, 1, captureCb);
                                } else {
                                    logW("local launcher-layer capture failed; wallpaper fallback: " + error);
                                    captureClient.captureScreenAsync(req.stripRect, captureScale,
                                            req.displayId, null, null, 2, captureCb);
                                }
                            }
''')

replace_once(
    view_path,
'''                        logW("local launcher-layer API unavailable; wallpaper fallback scene="
                                + requestScene);
                        actualSource = CaptureSourcePolicy.Source.WALLPAPER;
''',
'''                        if (workstationMode
                                && (hasValidDockWindowSurface() || dockWindowLayerName != null)) {
                            logW("local launcher-layer API unavailable; safe full-display fallback scene="
                                    + requestScene);
                            actualSource = CaptureSourcePolicy.Source.FULL_DISPLAY;
                        } else {
                            logW("local launcher-layer API unavailable; wallpaper fallback scene="
                                    + requestScene);
                            actualSource = CaptureSourcePolicy.Source.WALLPAPER;
                        }
''')

# 15) A workstation burst must bypass wallpaper cache so every convergence sample is real.
replace_once(
    view_path,
'''                    if (wallpaperMode && tryServeWallpaperFromCache(
                            req, requestScene, requestSceneRevision, attempt)) {
''',
'''                    if (wallpaperMode
                            && !(workstationMode && workstationCaptureBurst.isActive())
                            && tryServeWallpaperFromCache(
                            req, requestScene, requestSceneRevision, attempt)) {
''')

# 16) Successful frames drive workstation convergence; normal Recents retains its existing loop.
replace_once(
    view_path,
'''                rotationStabilizeTick(visualProbe.signature);
                // Config is hot-reloaded by the 1s ticker; no duplicate counter needed here.
                if (sourceDirty) requestStateCapture();
''',
'''                rotationStabilizeTick(visualProbe.signature);
                if (workstationMode && workstationCaptureBurst.isActive()) {
                    if (workstationCaptureBurst.onFrame(visualProbe.signature)) {
                        requestStateCapture("workstation-background-changing");
                    } else {
                        finishWorkstationCaptureBurstIfSettled();
                    }
                }
                // Config is hot-reloaded by the 1s ticker; no duplicate counter needed here.
                if (sourceDirty) requestStateCapture();
''')

replace_once(
    view_path,
'''                if (isRecentsVisible()) requestStateCapture("recents-continue");
''',
'''                if (!workstationMode && isRecentsVisible()) {
                    requestStateCapture("recents-continue");
                }
''')

# The wallpaper-cache completion has the same normal-Recents loop; workstation bursts never
# use this cache, but keep the isolation explicit.
replace_once(
    view_path,
'''            if (isRecentsVisible()) requestStateCapture("recents-continue");
''',
'''            if (!workstationMode && isRecentsVisible()) {
                requestStateCapture("recents-continue");
            }
''')

print("workstation live backdrop patch applied")
