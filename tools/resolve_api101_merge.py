from pathlib import Path
import re

ROOT = Path('.')


def resolve_conflicts(path: str, resolutions):
    p = ROOT / path
    text = p.read_text()
    out = []
    pos = 0
    blocks = 0
    while True:
        start = text.find('<<<<<<< ', pos)
        if start < 0:
            out.append(text[pos:])
            break
        out.append(text[pos:start])
        nl = text.find('\n', start)
        mid = text.find('\n=======\n', nl)
        if mid < 0:
            raise RuntimeError(f'malformed conflict in {path}')
        end_marker = text.find('\n>>>>>>> ', mid + 9)
        if end_marker < 0:
            raise RuntimeError(f'malformed conflict end in {path}')
        end_nl = text.find('\n', end_marker + 1)
        if end_nl < 0:
            end_nl = len(text)
        ours = text[nl + 1:mid]
        theirs = text[mid + len('\n=======\n'):end_marker]
        if blocks >= len(resolutions):
            raise RuntimeError(f'unexpected extra conflict {blocks + 1} in {path}')
        choice = resolutions[blocks]
        if choice == 'ours':
            replacement = ours
        elif choice == 'theirs':
            replacement = theirs
        elif callable(choice):
            replacement = choice(ours, theirs)
        else:
            replacement = choice
        if replacement and not replacement.endswith('\n'):
            replacement += '\n'
        out.append(replacement)
        pos = end_nl + (1 if end_nl < len(text) else 0)
        blocks += 1
    if blocks != len(resolutions):
        raise RuntimeError(f'{path}: expected {len(resolutions)} conflicts, saw {blocks}')
    p.write_text(''.join(out))


def replace_once(path: str, old: str, new: str):
    p = ROOT / path
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f'{path}: expected one match, found {count}\n--- OLD ---\n{old}')
    p.write_text(text.replace(old, new, 1))


def write(path: str, content: str):
    (ROOT / path).write_text(content)


write('src/main/java/com/hellovoid/liquiddock/CaptureSourcePolicy.java', '''package com.hellovoid.liquiddock;

/** Selects the compositor source while keeping speculative Launcher transitions wallpaper-backed. */
final class CaptureSourcePolicy {
    enum Source { WALLPAPER, FULL_DISPLAY }

    private CaptureSourcePolicy() {}

    /** API101 compatibility: only authoritative live scenes use the composed display. */
    static Source sourceFor(CaptureScene scene) {
        if (scene == CaptureScene.APP || scene == CaptureScene.RECENTS) {
            return Source.FULL_DISPLAY;
        }
        return Source.WALLPAPER;
    }

    /** Legacy call shape: RECENTS has not yet crossed its authoritative lifecycle boundary. */
    static Source sourceFor(CaptureScene scene, boolean localLayerAvailable) {
        return sourceFor(scene, localLayerAvailable, false, false);
    }

    static Source sourceFor(CaptureScene scene, boolean localLayerAvailable,
                            boolean recentsLiveConfirmed) {
        return sourceFor(scene, localLayerAvailable, recentsLiveConfirmed, false);
    }

    /** HOME becomes live only while a visible freeform task overlays the Launcher. */
    static Source sourceFor(CaptureScene scene, boolean localLayerAvailable,
                            boolean recentsLiveConfirmed, boolean homeLiveBackdrop) {
        if (scene == null) return Source.WALLPAPER;
        if (scene == CaptureScene.HOME) {
            return homeLiveBackdrop ? Source.FULL_DISPLAY : Source.WALLPAPER;
        }
        if (scene == CaptureScene.APP) return Source.FULL_DISPLAY;
        if (scene == CaptureScene.RECENTS) {
            return recentsLiveConfirmed ? Source.FULL_DISPLAY : Source.WALLPAPER;
        }
        return Source.WALLPAPER;
    }

    /** API101 intentionally has no LayerCapture path. Workstation All Apps and Recents use the
     * composed display with Dock/freeform exclusions; other workstation scenes stay wallpaper-backed. */
    static Source sourceForWorkstationScene(CaptureScene scene, boolean localLayerAvailable) {
        if (scene == CaptureScene.RECENTS || scene == CaptureScene.ALL_APPS) {
            return Source.FULL_DISPLAY;
        }
        return Source.WALLPAPER;
    }
}
''')

write('src/test/java/com/hellovoid/liquiddock/CaptureSourcePolicyTest.java', '''package com.hellovoid.liquiddock;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/** Covers API101 compatibility plus confirmed Recents/freeform/workstation source selection. */
public class CaptureSourcePolicyTest {
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String sourceFor(String sceneName) throws Exception {
        Class<?> policy = Class.forName("com.hellovoid.liquiddock.CaptureSourcePolicy");
        Class<?> scene = Class.forName("com.hellovoid.liquiddock.CaptureScene");
        Method sourceFor = policy.getDeclaredMethod("sourceFor", scene);
        sourceFor.setAccessible(true);
        Object sceneValue = Enum.valueOf((Class<? extends Enum>) scene, sceneName);
        return ((Enum<?>) sourceFor.invoke(null, sceneValue)).name();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String sourceFor(String sceneName, boolean localLayerAvailable,
                                    boolean recentsLiveConfirmed) throws Exception {
        Class<?> policy = Class.forName("com.hellovoid.liquiddock.CaptureSourcePolicy");
        Class<?> scene = Class.forName("com.hellovoid.liquiddock.CaptureScene");
        final Method method;
        try {
            method = policy.getDeclaredMethod("sourceFor", scene, boolean.class, boolean.class);
        } catch (NoSuchMethodException e) {
            fail("CaptureSourcePolicy must expose confirmed-Recents source selection");
            return null;
        }
        method.setAccessible(true);
        Object sceneValue = Enum.valueOf((Class<? extends Enum>) scene, sceneName);
        return ((Enum<?>) method.invoke(null, sceneValue, localLayerAvailable,
                recentsLiveConfirmed)).name();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String workstationSourceFor(String sceneName) throws Exception {
        Class<?> policy = Class.forName("com.hellovoid.liquiddock.CaptureSourcePolicy");
        Class<?> scene = Class.forName("com.hellovoid.liquiddock.CaptureScene");
        Method method = policy.getDeclaredMethod("sourceForWorkstationScene", scene, boolean.class);
        method.setAccessible(true);
        Object sceneValue = Enum.valueOf((Class<? extends Enum>) scene, sceneName);
        return ((Enum<?>) method.invoke(null, sceneValue, false)).name();
    }

    @Test public void policyExposesOnlyWallpaperAndFullDisplay() throws Exception {
        Class<?> source = Class.forName("com.hellovoid.liquiddock.CaptureSourcePolicy$Source");
        String[] names = Arrays.stream(source.getEnumConstants())
                .map(v -> ((Enum<?>) v).name()).sorted().toArray(String[]::new);
        assertEquals("[FULL_DISPLAY, WALLPAPER]", Arrays.toString(names));
    }

    @Test public void api101CompatibilityKeepsAuthoritativeRecentsLive() throws Exception {
        assertEquals("FULL_DISPLAY", sourceFor("APP"));
        assertEquals("FULL_DISPLAY", sourceFor("RECENTS"));
        assertEquals("WALLPAPER", sourceFor("HOME"));
        assertEquals("WALLPAPER", sourceFor("ALL_APPS"));
    }

    @Test public void speculativeRecentsStaysWallpaperUntilConfirmed() throws Exception {
        assertEquals("WALLPAPER", sourceFor("RECENTS", false, false));
        assertEquals("FULL_DISPLAY", sourceFor("RECENTS", false, true));
    }

    @Test public void workstationLiveScenesUseSafeComposedDisplay() throws Exception {
        assertEquals("FULL_DISPLAY", workstationSourceFor("RECENTS"));
        assertEquals("FULL_DISPLAY", workstationSourceFor("ALL_APPS"));
        assertEquals("WALLPAPER", workstationSourceFor("HOME"));
        assertEquals("WALLPAPER", workstationSourceFor("APP"));
    }
}
''')

resolve_conflicts('src/main/java/com/hellovoid/liquiddock/MainHook.java', [
    'ours', 'ours', 'ours', 'ours',
    '''        WorkstationDockGeometryHook.onWorkstationModeChanged(enabled);\n        log("[DC] workstation mode changed=" + enabled);'''
])
main = ROOT / 'src/main/java/com/hellovoid/liquiddock/MainHook.java'
text = main.read_text()
pattern = re.compile(r'\n    /\*\* Windowing mode of the current top task\.[\s\S]*?\n    }\n\n    private static void installLiquidGlassCaptureHooks')
m = pattern.search(text)
if not m:
    raise RuntimeError('MainHook: foregroundTaskWindowingMode helper not found')
text = text[:m.start()] + '\n\n    private static void installLiquidGlassCaptureHooks' + text[m.end():]
main.write_text(text)

resolve_conflicts('src/main/java/com/hellovoid/liquiddock/DockStrokeRenderer.java',
                  ['theirs', 'theirs', 'theirs', 'theirs'])


def c1(_o, _t):
    return '''    private volatile LiveScreenCapture liveCapture;\n    private final SurfaceLayerNameResolver surfaceLayerNameResolver;\n    private final FreeformLayerResolver freeformLayerResolver;'''

def c2(_o, _t):
    return '''    private int workstationRecentsSessionToken;\n    private final WorkstationCaptureBurst workstationCaptureBurst = new WorkstationCaptureBurst();\n    private boolean workstationSuspendWhenBurstSettles;'''

def c4(_o, _t):
    return '''        if (workstationMode) return;\n        final int token = ++allAppsPrearmToken;\n        sceneState.prearmAllApps(System.nanoTime());\n        updateDesiredScene();\n        observationValid = false;\n        lastCaptureStartNanos = 0L;\n        requestStateCapture("all-apps-prearm-" + reason);\n        mainHandler.postDelayed(() -> {\n            if (token != allAppsPrearmToken || sceneState.allAppsActive()) return;\n            if (!sceneState.allAppsPrearmExpired(System.nanoTime())) return;\n            sceneState.clearAllAppsPrearm();\n            updateDesiredScene();\n            requestStateCapture("all-apps-prearm-expired");\n        }, 920L);\n    }\n\n    void setAllAppsActive(boolean active) {\n        if (Looper.myLooper() != Looper.getMainLooper()) {\n            mainHandler.post(() -> setAllAppsActive(active));\n            return;\n        }\n        allAppsPrearmToken++;\n        sceneState.clearAllAppsPrearm();\n        boolean stateChanged = sceneState.allAppsActive() != active;\n        sceneState.setAllAppsActive(active);\n        if (stateChanged && workstationMode) {\n            workstationSuspendWhenBurstSettles = !active;\n            startWorkstationCaptureBurst(active ? "all-apps-enter" : "all-apps-exit");\n        }'''

def c5(_o, _t):
    return '''    /** Stock Recents callbacks own confirmed Overview state; gesture targets remain prearm-only. */'''

def c7(_o, _t):
    return '''        final int session = ++workstationRecentsSessionToken;\n        workstationSuspendWhenBurstSettles = false;\n        startWorkstationCaptureBurst("workstation-recents-enter");'''

def c8(_o, _t):
    return '''        workstationRecentsSessionToken++;\n        workstationCaptureBurst.stop();\n        workstationSuspendWhenBurstSettles = false;'''

def c9(_o, _t):
    return '''        final long requestWallpaperTransformRevision = wallpaperTransformRevision;\n        final long requestHomeWallpaperCaptureEpoch = homeWallpaperCaptureEpoch;\n        final boolean liveHomeBehindFreeform = useFullscreen\n                && !workstationMode\n                && requestScene == CaptureScene.HOME\n                && freeformLayerResolver.hasVisibleFreeformTasks();\n        CaptureSourcePolicy.Source selectedSource;\n        if (!useFullscreen || (workstationMode && requestScene == CaptureScene.APP)) {\n            selectedSource = CaptureSourcePolicy.Source.WALLPAPER;\n        } else if (workstationMode) {\n            selectedSource = CaptureSourcePolicy.sourceForWorkstationScene(requestScene, false);\n        } else {\n            selectedSource = CaptureSourcePolicy.sourceFor(\n                    requestScene, false, isRecentsVisible(), liveHomeBehindFreeform);'''

def c10(_o, _t):
    return '''        // Every mutable exclusion input is snapshotted before the worker request.\n        boolean needsDockExclude = useFullscreen\n                && requestedSource == CaptureSourcePolicy.Source.FULL_DISPLAY;\n        final float requestCaptureScale = captureScale;\n        final android.view.SurfaceControl requestDockWindowSurface =\n                needsDockExclude && dockExcludeRecovery.includeSurfaceControl()\n                        ? dockWindowSurface : null;\n        final String requestDockWindowLayerName = dockWindowLayerName;\n        final String requestDragLayerName = dragLayerName;\n        final int requestWallpaperId = requestedSource == CaptureSourcePolicy.Source.WALLPAPER\n                ? currentWallpaperId() : -1;'''

def c12(_o, _t):
    return '''                    boolean wallpaperMode = actualSource == CaptureSourcePolicy.Source.WALLPAPER;\n                    String[] excludeNames = actualSource == CaptureSourcePolicy.Source.FULL_DISPLAY\n                            ? fullDisplayExclusions.layerNames : null;\n                    if (wallpaperMode\n                            && !(workstationMode && workstationCaptureBurst.isActive())\n                            && tryServeWallpaperFromCache(\n                            req, requestScene, requestSceneRevision, attempt,\n                            requestWallpaperId, requestWallpaperTransformRevision)) {'''

resolve_conflicts('src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java', [
    c1, c2, 'ours', c4, c5, 'ours', c7, c8, c9, c10, 'ours', c12,
    'theirs', 'theirs', 'theirs'
])

glass_path = 'src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java'
replace_once(glass_path,
'''    /** Resolve the newest live type-2997 root.  HyperOS can retain the retired root in\n''',
'''    private FullDisplayExclusions resolveFullDisplayExclusions() {\n        boolean freeformActive = freeformLayerResolver.hasVisibleFreeformTasks();\n        java.util.Collection<String> freeformLayers =\n                freeformLayerResolver.resolveVisibleLayerNames();\n        String[] names = CaptureExclusionNames.merge(\n                dockWindowLayerName, dragLayerName, freeformLayers);\n        boolean safe = !freeformActive || !freeformLayers.isEmpty();\n        if (freeformActive) {\n            logI("freeform capture exclusion: resolved=" + freeformLayers.size()\n                    + " names=" + java.util.Arrays.toString(names));\n        }\n        return new FullDisplayExclusions(names, safe);\n    }\n\n    private static final class FullDisplayExclusions {\n        static final FullDisplayExclusions NONE = new FullDisplayExclusions(null, true);\n        final String[] layerNames;\n        final boolean safe;\n        FullDisplayExclusions(String[] layerNames, boolean safe) {\n            this.layerNames = layerNames;\n            this.safe = safe;\n        }\n    }\n\n    /** Resolve the newest live type-2997 root.  HyperOS can retain the retired root in\n''')
replace_once(glass_path, '        if (!active) allAppsCaptureRoot = null;\n', '')
replace_once(glass_path,
'''        }\n        if (workstationMode && selectedSource == CaptureSourcePolicy.Source.FULL_DISPLAY) {\n            if (!hasValidDockWindowSurface()) dockWindowSurface = resolveWindowSurfaceControl();\n            // Never run an unsafe workstation full-display fallback: if the Dock cannot be\n            // excluded by handle or layer name, wallpaper is preferable to sampling icons.\n            if (!hasValidDockWindowSurface() && dockWindowLayerName == null) {\n                selectedSource = CaptureSourcePolicy.Source.WALLPAPER;\n            }\n        }\n        final CaptureSourcePolicy.Source requestedSource = selectedSource;\n''',
'''        }\n        FullDisplayExclusions selectedExclusions = FullDisplayExclusions.NONE;\n        if (selectedSource == CaptureSourcePolicy.Source.FULL_DISPLAY) {\n            if (!refreshDockWindowSurfaceCache("capture")) {\n                if (workstationMode) {\n                    selectedSource = CaptureSourcePolicy.Source.WALLPAPER;\n                } else {\n                    sourceDirty = true;\n                    scheduleCaptureFailureRetry("dock-surface-unavailable", generation);\n                    return;\n                }\n            }\n            if (selectedSource == CaptureSourcePolicy.Source.FULL_DISPLAY\n                    && dockExcludeRecovery.suspended()) {\n                if (workstationMode) {\n                    selectedSource = CaptureSourcePolicy.Source.WALLPAPER;\n                } else {\n                    sourceDirty = true;\n                    logW("FULL_DISPLAY capture suspended after repeated Dock exclude EINVAL; "\n                            + "waiting for a new Floating Dock generation");\n                    return;\n                }\n            }\n            if (selectedSource == CaptureSourcePolicy.Source.FULL_DISPLAY\n                    && dockExcludeRecovery.includeSurfaceControl()\n                    && !hasValidDockWindowSurface()) {\n                dockExcludeRecovery.onInvalidArgument();\n                invalidateDockWindowSurfaceHandle();\n                logW("Dock SurfaceControl failed freshness/validity check; "\n                        + "using fresh layer-name-only exclusion");\n            }\n            if (selectedSource == CaptureSourcePolicy.Source.FULL_DISPLAY) {\n                selectedExclusions = resolveFullDisplayExclusions();\n                if (!selectedExclusions.safe) {\n                    logW("full-display capture blocked: visible freeform task has no resolvable "\n                            + "SurfaceFlinger layer; wallpaper fallback");\n                    selectedSource = CaptureSourcePolicy.Source.WALLPAPER;\n                    selectedExclusions = FullDisplayExclusions.NONE;\n                }\n            }\n        }\n        final CaptureSourcePolicy.Source requestedSource = selectedSource;\n        final FullDisplayExclusions requestFullDisplayExclusions = selectedExclusions;\n''')
replace_once(glass_path,
'''                    final FullDisplayExclusions fullDisplayExclusions =\n                            (requestedSource == CaptureSourcePolicy.Source.FULL_DISPLAY\n                                    || (workstationMode\n                                        && requestedSource == CaptureSourcePolicy.Source.LOCAL_LAYER))\n                                    ? resolveFullDisplayExclusions()\n                                    : FullDisplayExclusions.NONE;\n''',
'''                    final FullDisplayExclusions fullDisplayExclusions =\n                            requestFullDisplayExclusions;\n''')

write('src/main/java/com/hellovoid/liquiddock/ForegroundTaskResolver.java', '''package com.hellovoid.liquiddock;

import android.app.ActivityManager;
import android.content.Context;

import java.util.List;
import java.util.function.Consumer;

/** Samples the top task as noisy foreground evidence; contains no scene mutation. */
final class ForegroundTaskResolver {
    static final class Observation {
        final ForegroundOwnership ownership;
        final String packageName;
        final int windowingMode;

        Observation(ForegroundOwnership ownership, String packageName, int windowingMode) {
            this.ownership = ownership;
            this.packageName = packageName;
            this.windowingMode = windowingMode;
        }
    }

    private final Consumer<String> logger;

    ForegroundTaskResolver(Consumer<String> logger) { this.logger = logger; }

    Observation resolve(Context context) {
        if (context == null) return unknown();
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return unknown();
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
            if (tasks == null || tasks.isEmpty() || tasks.get(0).topActivity == null) return unknown();
            ActivityManager.RunningTaskInfo top = tasks.get(0);
            String pkg = top.topActivity.getPackageName();
            if (pkg == null || pkg.isEmpty()) return unknown();
            int mode = windowingMode(top);
            boolean launcherOwned = "com.miui.home".equals(pkg)
                    || LauncherSceneOwnershipPolicy.launcherOwnsScene(false, mode);
            return new Observation(launcherOwned
                    ? ForegroundOwnership.HOME : ForegroundOwnership.EXTERNAL, pkg, mode);
        } catch (Throwable e) {
            logger.accept("[DC] top task resolve unavailable: " + e);
            return unknown();
        }
    }

    /** Compatibility probe retained for source contracts; scene code uses resolve(). */
    String resolveTopPackage(Context context) { return resolve(context).packageName; }

    private static Observation unknown() {
        return new Observation(ForegroundOwnership.UNKNOWN, null, -1);
    }

    private static int windowingMode(ActivityManager.RunningTaskInfo task) {
        try {
            Object value = HookUtil.invoke(task, "getWindowingMode");
            return value instanceof Integer ? (Integer) value : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }
}
''')

controller_path = 'src/main/java/com/hellovoid/liquiddock/LauncherSceneController.java'
replace_once(controller_path,
'''    void seed(Object launcher) {\n        if (launcher == null) return;\n        try {\n            Object paused = HookUtil.invoke(launcher, "isPause");\n            Object visible = HookUtil.invoke(launcher, "isVisible");\n            Object focused = HookUtil.invoke(launcher, "isWindowFocus");\n            if (paused instanceof Boolean && !((Boolean) paused)) {\n                launcherLifecycleKnown = true;\n                if (!launcherAwayObserved) launcherResumed = true;\n            }\n            logger.accept("[DC] liquid lifecycle seed: known=" + launcherLifecycleKnown\n                    + " resumed=" + launcherResumed + " paused=" + paused\n                    + " visible=" + visible + " focus=" + focused);\n        } catch (Throwable e) {\n            logger.accept("[DC] liquid lifecycle seed unavailable; using window gate: " + e);\n        }\n        DockLiquidGlassView glass = glassProvider.get();\n        if (glass != null && launcherAwayObserved) glass.setLauncherAwayHint(true);\n        if (launcher instanceof Context) {\n            boolean allowHomeCommit = launcherLifecycleKnown && launcherResumed\n                    && !launcherAwayObserved;\n            boolean allowExternalCommit = launcherLifecycleKnown && !launcherResumed;\n            observeForegroundOwnership((Context) launcher, glass, "seed",\n                    allowHomeCommit, allowExternalCommit);\n        }\n    }\n''',
'''    void seed(Object launcher) {\n        if (launcher == null) return;\n        DockLiquidGlassView glass = glassProvider.get();\n        ForegroundTaskResolver.Observation observation = launcher instanceof Context\n                ? foregroundTaskResolver.resolve((Context) launcher)\n                : new ForegroundTaskResolver.Observation(ForegroundOwnership.UNKNOWN, null, -1);\n        try {\n            Object paused = HookUtil.invoke(launcher, "isPause");\n            Object visible = HookUtil.invoke(launcher, "isVisible");\n            Object focused = HookUtil.invoke(launcher, "isWindowFocus");\n            if (paused instanceof Boolean) {\n                launcherLifecycleKnown = true;\n                launcherResumed = LauncherSceneOwnershipPolicy.launcherOwnsScene(\n                        !((Boolean) paused), observation.windowingMode);\n                if (launcherResumed) launcherAwayObserved = false;\n            }\n            logger.accept("[DC] liquid lifecycle seed: known=" + launcherLifecycleKnown\n                    + " resumed=" + launcherResumed + " paused=" + paused\n                    + " visible=" + visible + " focus=" + focused\n                    + " windowingMode=" + observation.windowingMode);\n        } catch (Throwable e) {\n            logger.accept("[DC] liquid lifecycle seed unavailable; using window gate: " + e);\n        }\n        if (glass != null && launcherAwayObserved) glass.setLauncherAwayHint(true);\n        if (launcher instanceof Context) {\n            boolean allowHomeCommit = launcherLifecycleKnown && launcherResumed\n                    && !launcherAwayObserved;\n            boolean allowExternalCommit = launcherLifecycleKnown && !launcherResumed;\n            applyForegroundObservation(observation, glass, "seed",\n                    allowHomeCommit, allowExternalCommit);\n        }\n    }\n''')
replace_once(controller_path,
'''                        logger.accept("[DC] liquid focus hint: " + hasFocus);\n                        if (!hasFocus && chain.getThisObject() instanceof Context) {\n                            updateModuleSettingsForeground(\n                                    foregroundTaskResolver.resolve(\n                                            (Context) chain.getThisObject()),\n                                    glass, "focus-loss");\n                        }\n                        if (!hasFocus) {\n                            launcherLifecycleKnown = true;\n                            launcherResumed = false;\n                            launcherAwayObserved = true;\n                            foregroundOwnership = ForegroundOwnership.EXTERNAL;\n                            foregroundAuthorityGate.resetHomeCandidate();\n                            if (glass != null) {\n                                glass.onLauncherFocusLost();\n                                glass.setForegroundOwnership(ForegroundOwnership.EXTERNAL);\n                                glass.setLauncherAwayHint(true);\n                                glass.refreshForegroundAppLayer();\n                                glass.setLauncherState(true, false);\n                                glass.prearmAppBackdrop("focus-loss");\n                            }\n                        } else if (!confirmLauncherHomeFocus(\n                                chain.getThisObject(), glass, "focus-gain")) {\n                            scheduleLauncherHomeFocusRecheck(chain.getThisObject(), glass);\n                        }\n''',
'''                        logger.accept("[DC] liquid focus hint: " + hasFocus);\n                        ForegroundTaskResolver.Observation observation =\n                                chain.getThisObject() instanceof Context\n                                        ? foregroundTaskResolver.resolve((Context) chain.getThisObject())\n                                        : new ForegroundTaskResolver.Observation(\n                                                ForegroundOwnership.UNKNOWN, null, -1);\n                        updateModuleSettingsForeground(observation, glass,\n                                hasFocus ? "focus-gain" : "focus-loss");\n                        if (!hasFocus && LauncherSceneOwnershipPolicy.launcherOwnsScene(\n                                false, observation.windowingMode)) {\n                            launcherLifecycleKnown = true;\n                            launcherResumed = true;\n                            launcherAwayObserved = false;\n                            foregroundOwnership = ForegroundOwnership.HOME;\n                            foregroundAuthorityGate.resetHomeCandidate();\n                            if (glass != null) {\n                                glass.onAuthoritativeHomeConfirmed();\n                                glass.setLauncherState(true, true);\n                            }\n                            logger.accept("[DC] liquid focus freeform-owned windowingMode="\n                                    + observation.windowingMode);\n                        } else if (!hasFocus) {\n                            launcherLifecycleKnown = true;\n                            launcherResumed = false;\n                            launcherAwayObserved = true;\n                            foregroundOwnership = ForegroundOwnership.EXTERNAL;\n                            foregroundAuthorityGate.resetHomeCandidate();\n                            if (glass != null) {\n                                glass.onLauncherFocusLost();\n                                glass.setForegroundOwnership(ForegroundOwnership.EXTERNAL);\n                                glass.setLauncherAwayHint(true);\n                                glass.refreshForegroundAppLayer();\n                                glass.setLauncherState(true, false);\n                                glass.prearmAppBackdrop("focus-loss");\n                            }\n                        } else if (!confirmLauncherHomeFocus(\n                                chain.getThisObject(), glass, "focus-gain")) {\n                            scheduleLauncherHomeFocusRecheck(chain.getThisObject(), glass);\n                        }\n''')
replace_once(controller_path,
'''                HookUtil.hookMethod(Activity.class, "onPause", new Class<?>[0], chain -> {\n                    if (launcherClass.isInstance(chain.getThisObject())) {\n                        launcherLifecycleKnown = true;\n                        launcherResumed = false;\n                        launcherAwayObserved = true;\n                        foregroundOwnership = ForegroundOwnership.EXTERNAL;\n                        foregroundAuthorityGate.resetHomeCandidate();\n                        logger.accept("[DC] liquid lifecycle fallback: onPause");\n                        DockLiquidGlassView glass = glassProvider.get();\n                        if (glass != null) {\n                            glass.onLauncherFocusLost();\n                            glass.setForegroundOwnership(ForegroundOwnership.EXTERNAL);\n                            glass.setLauncherAwayHint(true);\n                            glass.setLauncherState(true, false);\n                        }\n                    }\n                    return chain.proceed(chain.getArgs().toArray(new Object[0]));\n                });\n''',
'''                HookUtil.hookMethod(Activity.class, "onPause", new Class<?>[0], chain -> {\n                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));\n                    if (launcherClass.isInstance(chain.getThisObject())) {\n                        DockLiquidGlassView glass = glassProvider.get();\n                        ForegroundTaskResolver.Observation observation =\n                                foregroundTaskResolver.resolve((Context) chain.getThisObject());\n                        launcherLifecycleKnown = true;\n                        if (LauncherSceneOwnershipPolicy.launcherOwnsScene(\n                                false, observation.windowingMode)) {\n                            launcherResumed = true;\n                            launcherAwayObserved = false;\n                            foregroundOwnership = ForegroundOwnership.HOME;\n                            foregroundAuthorityGate.resetHomeCandidate();\n                            if (glass != null) {\n                                glass.onAuthoritativeHomeConfirmed();\n                                glass.setLauncherState(true, true);\n                            }\n                            logger.accept("[DC] liquid lifecycle fallback: onPause freeform "\n                                    + "windowingMode=" + observation.windowingMode);\n                        } else {\n                            launcherResumed = false;\n                            launcherAwayObserved = true;\n                            foregroundOwnership = ForegroundOwnership.EXTERNAL;\n                            foregroundAuthorityGate.resetHomeCandidate();\n                            logger.accept("[DC] liquid lifecycle fallback: onPause external "\n                                    + "windowingMode=" + observation.windowingMode);\n                            if (glass != null) {\n                                glass.onLauncherFocusLost();\n                                glass.setForegroundOwnership(ForegroundOwnership.EXTERNAL);\n                                glass.setLauncherAwayHint(true);\n                                glass.setLauncherState(true, false);\n                            }\n                        }\n                    }\n                    return result;\n                });\n''')

recents_path = 'src/main/java/com/hellovoid/liquiddock/RecentsStateHooks.java'
replace_once(recents_path,
'''                    DockLiquidGlassView glass = glassProvider.get();\n                    if (glass != null && !workstationModeProvider.getAsBoolean())\n                        glass.setOverviewActive(active, eventName);\n                    if (!workstationModeProvider.getAsBoolean())\n                        logger.accept("[DC] liquid overview active=" + active + " event=" + eventName);\n''',
'''                    DockLiquidGlassView glass = glassProvider.get();\n                    if (glass != null) glass.setOverviewActive(active, eventName);\n                    logger.accept("[DC] liquid overview active=" + active\n                            + " event=" + eventName\n                            + " workstation=" + workstationModeProvider.getAsBoolean());\n''')

write('src/test/java/com/hellovoid/liquiddock/LauncherSceneOwnershipPolicyTest.java', '''package com.hellovoid.liquiddock;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class LauncherSceneOwnershipPolicyTest {
    @Test public void freeformForegroundKeepsLauncherSceneWhenLifecyclePauses() {
        assertTrue(LauncherSceneOwnershipPolicy.launcherOwnsScene(false, 5));
    }
    @Test public void fullscreenForegroundStillMovesCaptureToApp() {
        assertFalse(LauncherSceneOwnershipPolicy.launcherOwnsScene(false, 1));
    }
    @Test public void resumedLauncherAlwaysOwnsItsScene() {
        assertTrue(LauncherSceneOwnershipPolicy.launcherOwnsScene(true, 1));
    }
    @Test public void sceneControllerRoutesTaskWindowingModeThroughOwnershipPolicy()
            throws IOException {
        String resolver = Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/ForegroundTaskResolver.java"),
                StandardCharsets.UTF_8);
        String controller = Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/LauncherSceneController.java"),
                StandardCharsets.UTF_8);
        assertTrue(resolver.contains("getWindowingMode"));
        assertTrue(resolver.contains("LauncherSceneOwnershipPolicy.launcherOwnsScene"));
        assertTrue(controller.contains("observation.windowingMode"));
        assertTrue(controller.contains("LauncherSceneOwnershipPolicy.launcherOwnsScene"));
    }
}
''')

workstation_test = 'src/test/java/com/hellovoid/liquiddock/WorkstationLiveBackdropContractTest.java'
if (ROOT / workstation_test).exists():
    text = (ROOT / workstation_test).read_text()
    text = text.replace(
'''    private static String mainHookSource() throws IOException {\n        return Files.readString(\n                Paths.get("src/main/java/com/hellovoid/liquiddock/MainHook.java"),\n                StandardCharsets.UTF_8);\n    }\n''',
'''    private static String recentsHooksSource() throws IOException {\n        return Files.readString(\n                Paths.get("src/main/java/com/hellovoid/liquiddock/RecentsStateHooks.java"),\n                StandardCharsets.UTF_8);\n    }\n''')
    old = '''    @Test public void exactOverviewLifecycleIsForwardedInWorkstationMode() throws IOException {\n        String source = mainHookSource();\n        assertFalse("workstation must not suppress exact Enter/ExitOverviewStateEvent callbacks",\n                source.contains("if (glass != null && !workstationMode)\\n"\n                        + "                        glass.setOverviewActive(active, eventName);"));\n        assertTrue("overview lifecycle must reach DockLiquidGlassView in every mode",\n                source.contains("if (glass != null)\\n"\n                        + "                        glass.setOverviewActive(active, eventName);"));\n    }\n'''
    new = '''    @Test public void exactOverviewLifecycleIsForwardedInWorkstationMode() throws IOException {\n        String source = recentsHooksSource();\n        assertFalse("workstation must not suppress exact Overview callbacks",\n                source.contains("glass != null && !workstationModeProvider.getAsBoolean()"));\n        assertTrue("overview lifecycle must reach DockLiquidGlassView in every mode",\n                source.contains("if (glass != null) glass.setOverviewActive(active, eventName);"));\n    }\n'''
    if old not in text:
        raise RuntimeError('WorkstationLiveBackdropContractTest expected block not found')
    (ROOT / workstation_test).write_text(text.replace(old, new, 1))

recents_test = 'src/test/java/com/hellovoid/liquiddock/RecentsCaptureConfirmationContractTest.java'
if (ROOT / recents_test).exists():
    replace_once(recents_test,
'''        assertTrue("runtime source selection must still pass confirmed Overview state explicitly",\n                startCapture.contains("requestScene, localCaptureSurface != null, isRecentsVisible(),"));\n''',
'''        assertTrue("runtime source selection must still pass confirmed Overview state explicitly",\n                startCapture.contains("requestScene, false, isRecentsVisible(), liveHomeBehindFreeform"));\n''')

for path in [
    'src/main/java/com/hellovoid/liquiddock/CaptureSourcePolicy.java',
    'src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java',
    'src/main/java/com/hellovoid/liquiddock/DockStrokeRenderer.java',
    'src/main/java/com/hellovoid/liquiddock/MainHook.java',
    'src/test/java/com/hellovoid/liquiddock/CaptureSourcePolicyTest.java',
]:
    text = (ROOT / path).read_text()
    if any(marker in text for marker in ('<<<<<<<', '=======', '>>>>>>>')):
        raise RuntimeError(f'unresolved merge marker in {path}')

combined = '\n'.join((ROOT / p).read_text() for p in [
    'src/main/java/com/hellovoid/liquiddock/LiveScreenCapture.java',
    'src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java',
    'src/main/java/com/hellovoid/liquiddock/MainHook.java',
    'src/main/java/com/hellovoid/liquiddock/CaptureSourcePolicy.java',
])
for forbidden in ('LOCAL_LAYER', 'LayerCaptureArgs', 'captureLayers', 'captureLayerAsync',
                  'resolveLauncherOwnedCaptureSurface', 'resolveViewRootSurfaceControl',
                  'allAppsCaptureRoot'):
    if forbidden in combined:
        raise RuntimeError(f'abandoned LayerCapture path leaked into merge: {forbidden}')
