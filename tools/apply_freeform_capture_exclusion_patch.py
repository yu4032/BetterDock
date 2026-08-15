from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected exactly one match, found {count}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


base = Path("src/main/java/com/hellovoid/liquiddock")

(base / "FreeformCapturePolicy.java").write_text('''package com.hellovoid.liquiddock;

/** Pure policy for deciding whether a task must be removed from a desktop backdrop. */
final class FreeformCapturePolicy {
    private FreeformCapturePolicy() {}

    static boolean shouldExclude(int windowingMode, boolean visible) {
        return visible
                && windowingMode == LauncherSceneOwnershipPolicy.WINDOWING_MODE_FREEFORM;
    }
}
''', encoding="utf-8")

(base / "CaptureExclusionNames.java").write_text('''package com.hellovoid.liquiddock;

import java.util.Collection;
import java.util.LinkedHashSet;

/** Builds one deterministic, duplicate-free mode-1 SurfaceFlinger exclusion list. */
final class CaptureExclusionNames {
    private CaptureExclusionNames() {}

    static String[] merge(String dockLayer, String dragLayer,
                          Collection<String> freeformLayers) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        add(names, dockLayer);
        add(names, dragLayer);
        if (freeformLayers != null) {
            for (String name : freeformLayers) add(names, name);
        }
        return names.isEmpty() ? null : names.toArray(new String[0]);
    }

    private static void add(LinkedHashSet<String> names, String value) {
        if (value != null && !value.isEmpty()) names.add(value);
    }
}
''', encoding="utf-8")

(base / "SurfaceLayerNameResolver.java").write_text('''package com.hellovoid.liquiddock;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Small SurfaceFlinger debug-layer adapter shared by app and freeform capture policy. */
final class SurfaceLayerNameResolver {
    String resolveTopmostByOwnerUid(int ownerUid) throws Exception {
        String best = null;
        for (Object layer : queryLayers()) {
            Integer uid = ownerUid(layer);
            if (uid == null || uid != ownerUid) continue;
            String name = layerName(layer);
            if (name != null && !name.isEmpty()) best = name;
        }
        return best;
    }

    Collection<String> resolveAllByOwnerUids(Collection<Integer> ownerUids) throws Exception {
        if (ownerUids == null || ownerUids.isEmpty()) return Collections.emptyList();
        Set<Integer> wanted = new java.util.HashSet<>(ownerUids);
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Object layer : queryLayers()) {
            Integer uid = ownerUid(layer);
            if (uid == null || !wanted.contains(uid)) continue;
            String name = layerName(layer);
            if (name != null && !name.isEmpty()) names.add(name);
        }
        return names;
    }

    private List<?> queryLayers() throws Exception {
        Class<?> stub = Class.forName("android.view.ISurfaceComposer$Stub");
        java.lang.reflect.Method asInterface = stub.getMethod(
                "asInterface", android.os.IBinder.class);
        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        java.lang.reflect.Method getService = serviceManager.getMethod("getService", String.class);
        Object binder = getService.invoke(null, "SurfaceFlinger");
        Object composer = asInterface.invoke(null, binder);
        if (composer == null) return Collections.emptyList();
        Object result = composer.getClass().getMethod("getLayerDebugInfo").invoke(composer);
        return result instanceof List ? (List<?>) result : Collections.emptyList();
    }

    private static Integer ownerUid(Object layer) {
        try {
            Object value = layer.getClass().getMethod("getOwnerUid").invoke(layer);
            return value instanceof Integer ? (Integer) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String layerName(Object layer) {
        try {
            Object value = layer.getClass().getMethod("getName").invoke(layer);
            return value instanceof String ? (String) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
''', encoding="utf-8")

(base / "FreeformLayerResolver.java").write_text('''package com.hellovoid.liquiddock;

import android.app.ActivityManager;
import android.content.Context;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/** Resolves visible freeform task owners to SurfaceFlinger layer names with a short cache. */
final class FreeformLayerResolver {
    private static final int MAX_RUNNING_TASKS = 32;
    private static final long CACHE_NANOS = 250_000_000L;

    private final Context context;
    private final SurfaceLayerNameResolver surfaceLayers;

    private long taskCacheUntilNanos;
    private long layerCacheUntilNanos;
    private List<Integer> cachedOwnerUids = Collections.emptyList();
    private List<String> cachedLayerNames = Collections.emptyList();

    FreeformLayerResolver(Context context, SurfaceLayerNameResolver surfaceLayers) {
        Context app = context.getApplicationContext();
        this.context = app != null ? app : context;
        this.surfaceLayers = surfaceLayers;
    }

    synchronized void invalidate() {
        taskCacheUntilNanos = 0L;
        layerCacheUntilNanos = 0L;
    }

    synchronized boolean hasVisibleFreeformTasks() {
        refreshTaskOwners();
        return !cachedOwnerUids.isEmpty();
    }

    synchronized Collection<String> resolveVisibleLayerNames() {
        refreshTaskOwners();
        if (cachedOwnerUids.isEmpty()) return Collections.emptyList();
        long now = System.nanoTime();
        if (now < layerCacheUntilNanos) return cachedLayerNames;
        try {
            Collection<String> resolved = surfaceLayers.resolveAllByOwnerUids(cachedOwnerUids);
            cachedLayerNames = Collections.unmodifiableList(new ArrayList<>(resolved));
        } catch (Throwable ignored) {
            cachedLayerNames = Collections.emptyList();
        }
        layerCacheUntilNanos = now + CACHE_NANOS;
        return cachedLayerNames;
    }

    private void refreshTaskOwners() {
        long now = System.nanoTime();
        if (now < taskCacheUntilNanos) return;

        LinkedHashSet<Integer> ownerUids = new LinkedHashSet<>();
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningTaskInfo> tasks = am != null
                    ? am.getRunningTasks(MAX_RUNNING_TASKS) : null;
            if (tasks != null) {
                for (ActivityManager.RunningTaskInfo task : tasks) {
                    if (task == null) continue;
                    int mode = windowingMode(task);
                    if (!FreeformCapturePolicy.shouldExclude(mode, isVisible(task))) continue;
                    if (task.topActivity == null) continue;
                    String pkg = task.topActivity.getPackageName();
                    if (pkg == null || "com.miui.home".equals(pkg)) continue;
                    try {
                        ownerUids.add(context.getPackageManager().getPackageUid(pkg, 0));
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        List<Integer> next = Collections.unmodifiableList(new ArrayList<>(ownerUids));
        if (!next.equals(cachedOwnerUids)) {
            cachedOwnerUids = next;
            cachedLayerNames = Collections.emptyList();
            layerCacheUntilNanos = 0L;
        }
        taskCacheUntilNanos = now + CACHE_NANOS;
    }

    private static int windowingMode(ActivityManager.RunningTaskInfo task) {
        try {
            Object value = HookUtil.invoke(task, "getWindowingMode");
            return value instanceof Integer ? (Integer) value : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static boolean isVisible(ActivityManager.RunningTaskInfo task) {
        try {
            java.lang.reflect.Field field = task.getClass().getField("isVisible");
            Object value = field.get(task);
            if (value instanceof Boolean) return (Boolean) value;
        } catch (Throwable ignored) {
        }
        try {
            Object value = HookUtil.invoke(task, "isVisible");
            if (value instanceof Boolean) return (Boolean) value;
        } catch (Throwable ignored) {
        }
        // Older vendor builds do not expose task visibility. RunningTaskInfo is still
        // ordered by current relevance, so prefer exclusion to leaking a freeform surface.
        return true;
    }
}
''', encoding="utf-8")

replace_once(
    str(base / "CaptureSourcePolicy.java"),
'''    static Source sourceFor(CaptureScene scene, boolean localLayerAvailable,
                            boolean recentsLiveConfirmed) {
        if (scene == null || scene == CaptureScene.HOME) return Source.WALLPAPER;
        if (scene == CaptureScene.APP) return Source.FULL_DISPLAY;
        if (scene == CaptureScene.RECENTS) {
            return recentsLiveConfirmed ? Source.FULL_DISPLAY : Source.WALLPAPER;
        }
        // All Apps stays wallpaper-backed. localLayerAvailable is retained for source/API
        // compatibility with the 8ee84ed baseline but does not grant live capture authority.
        return Source.WALLPAPER;
    }
''',
'''    static Source sourceFor(CaptureScene scene, boolean localLayerAvailable,
                            boolean recentsLiveConfirmed) {
        return sourceFor(scene, localLayerAvailable, recentsLiveConfirmed, false);
    }

    /** HOME stays wallpaper-backed unless a visible freeform task requires a live desktop. */
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
        // All Apps stays wallpaper-backed. localLayerAvailable is retained for source/API
        // compatibility with the 8ee84ed baseline but does not grant live capture authority.
        return Source.WALLPAPER;
    }
''')

view = str(base / "DockLiquidGlassView.java")

replace_once(
    view,
'''    private Runnable captureTimeout;
    private LiveScreenCapture liveCapture;
    private ViewTreeObserver observedTree;
''',
'''    private Runnable captureTimeout;
    private LiveScreenCapture liveCapture;
    private final SurfaceLayerNameResolver surfaceLayerNameResolver;
    private final FreeformLayerResolver freeformLayerResolver;
    private ViewTreeObserver observedTree;
''')

replace_once(
    view,
'''        this.chromaticAberration = chromaticAberration;
        this.captureCadence = new CaptureCadence(captureFps);
        this.powerManager = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
''',
'''        this.chromaticAberration = chromaticAberration;
        this.captureCadence = new CaptureCadence(captureFps);
        this.surfaceLayerNameResolver = new SurfaceLayerNameResolver();
        this.freeformLayerResolver = new FreeformLayerResolver(
                getContext(), surfaceLayerNameResolver);
        this.powerManager = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
''')

replace_once(
    view,
'''        boolean changed = launcherLifecycleKnown != known || launcherResumed != resumed;
        launcherLifecycleKnown = known;
        launcherResumed = resumed;
''',
'''        // Focus/lifecycle callbacks are also the freeform enter/exit boundary. HyperOS may
        // report the same logical Launcher state on both sides, so always invalidate the
        // short task/layer cache and request a fresh scene sample below.
        freeformLayerResolver.invalidate();
        boolean changed = launcherLifecycleKnown != known || launcherResumed != resumed;
        launcherLifecycleKnown = known;
        launcherResumed = resumed;
''')

replace_once(
    view,
'''        if (changed) {
            logI("Liquid capture lifecycle=" + (known ? "RESUMED" : "UNKNOWN")
                    + "; window gate will decide capture");
            observationValid = false;
            requestStateCapture("lifecycle");
        }
''',
'''        if (changed) {
            logI("Liquid capture lifecycle=" + (known ? "RESUMED" : "UNKNOWN")
                    + "; window gate will decide capture");
        }
        // Even when (known,resumed) is unchanged, a freeform task may just have appeared or
        // disappeared. Force the boundary frame so HOME switches between wallpaper and the
        // live full-display-with-exclusions path immediately.
        observationValid = false;
        lastCaptureStartNanos = 0L;
        requestStateCapture(changed ? "lifecycle" : "lifecycle-scene-refresh");
''')

old_resolver = '''    /** Resolve the foreground app window's SF layer name (e.g. "#27820") via the
     *  SurfaceFlinger layer tree — the low-level way to hit exactly the app layer and
     *  avoid the Dock overlay / wallpaper layers entirely (no name-list guessing, no
     *  exclusion).  Matches by owner uid of the foreground task. */
    private String resolveAppLayerByUid(String pkg) {
        try {
            int uid;
            try {
                uid = getContext().getPackageManager().getPackageUid(pkg, 0);
            } catch (Throwable e) {
                return null;
            }
            Class<?> stub = Class.forName("android.view.ISurfaceComposer$Stub");
            java.lang.reflect.Method asInterface = stub.getMethod("asInterface",
                    android.os.IBinder.class);
            Class<?> sm = Class.forName("android.os.ServiceManager");
            java.lang.reflect.Method getService = sm.getMethod("getService", String.class);
            Object binder = getService.invoke(null, "SurfaceFlinger");
            Object composer = asInterface.invoke(null, binder);
            java.lang.reflect.Method getLayers = composer.getClass()
                    .getMethod("getLayerDebugInfo");
            Object layersObj = getLayers.invoke(composer);
            if (!(layersObj instanceof java.util.List)) return null;
            String best = null;
            for (Object layer : (java.util.List<?>) layersObj) {
                try {
                    Class<?> lc = layer.getClass();
                    Object ownerUid = lc.getMethod("getOwnerUid").invoke(layer);
                    if (!(ownerUid instanceof Integer) || (Integer) ownerUid != uid) continue;
                    Object name = lc.getMethod("getName").invoke(layer);
                    if (name != null) best = (String) name;  // last match = topmost z
                } catch (Throwable ignored) {
                }
            }
            return best;
        } catch (Throwable e) {
            logW("resolveForegroundAppLayerName failed: " + e);
            return null;
        }
    }
'''
new_resolver = '''    /** Resolve the foreground app window's topmost SF layer by owner uid. */
    private String resolveAppLayerByUid(String pkg) {
        try {
            int uid = getContext().getPackageManager().getPackageUid(pkg, 0);
            return surfaceLayerNameResolver.resolveTopmostByOwnerUid(uid);
        } catch (Throwable e) {
            logW("resolveForegroundAppLayerName failed: " + e);
            return null;
        }
    }

    private FullDisplayExclusions resolveFullDisplayExclusions() {
        boolean freeformActive = freeformLayerResolver.hasVisibleFreeformTasks();
        java.util.Collection<String> freeformLayers =
                freeformLayerResolver.resolveVisibleLayerNames();
        String[] names = CaptureExclusionNames.merge(
                dockWindowLayerName, dragLayerName, freeformLayers);
        boolean safe = !freeformActive || !freeformLayers.isEmpty();
        if (freeformActive) {
            logI("freeform capture exclusion: resolved=" + freeformLayers.size()
                    + " names=" + java.util.Arrays.toString(names));
        }
        return new FullDisplayExclusions(names, safe);
    }

    private static final class FullDisplayExclusions {
        static final FullDisplayExclusions NONE =
                new FullDisplayExclusions(null, true);

        final String[] layerNames;
        final boolean safe;

        FullDisplayExclusions(String[] layerNames, boolean safe) {
            this.layerNames = layerNames;
            this.safe = safe;
        }
    }
'''
replace_once(view, old_resolver, new_resolver)

replace_once(
    view,
'''        final CaptureScene requestScene = sceneState.desired();
        final long requestSceneRevision = sceneState.revision();
        final android.view.SurfaceControl localCaptureSurface = useFullscreen
                ? resolveLauncherOwnedCaptureSurface(requestScene) : null;
        CaptureSourcePolicy.Source selectedSource;
''',
'''        final CaptureScene requestScene = sceneState.desired();
        final long requestSceneRevision = sceneState.revision();
        final boolean liveHomeBehindFreeform = useFullscreen
                && !workstationMode
                && requestScene == CaptureScene.HOME
                && freeformLayerResolver.hasVisibleFreeformTasks();
        final android.view.SurfaceControl localCaptureSurface = useFullscreen
                ? resolveLauncherOwnedCaptureSurface(requestScene) : null;
        CaptureSourcePolicy.Source selectedSource;
''')

replace_once(
    view,
'''        } else {
            selectedSource = CaptureSourcePolicy.sourceFor(
                    requestScene, localCaptureSurface != null, isRecentsVisible());
        }
''',
'''        } else {
            selectedSource = CaptureSourcePolicy.sourceFor(
                    requestScene, localCaptureSurface != null, isRecentsVisible(),
                    liveHomeBehindFreeform);
        }
''')

replace_once(
    view,
'''                if (useFullscreen) {
                    android.view.SurfaceControl[] excludes = null;
                    if ((requestedSource == CaptureSourcePolicy.Source.FULL_DISPLAY
                            || (workstationMode
                                && requestedSource == CaptureSourcePolicy.Source.LOCAL_LAYER))
                            && dockWindowSurface != null) {
                        excludes = new android.view.SurfaceControl[]{dockWindowSurface};
                    }
                    final CaptureRequest req = request;
''',
'''                if (useFullscreen) {
                    final FullDisplayExclusions fullDisplayExclusions =
                            (requestedSource == CaptureSourcePolicy.Source.FULL_DISPLAY
                                    || (workstationMode
                                        && requestedSource == CaptureSourcePolicy.Source.LOCAL_LAYER))
                                    ? resolveFullDisplayExclusions()
                                    : FullDisplayExclusions.NONE;
                    android.view.SurfaceControl[] excludes = null;
                    if ((requestedSource == CaptureSourcePolicy.Source.FULL_DISPLAY
                            || (workstationMode
                                && requestedSource == CaptureSourcePolicy.Source.LOCAL_LAYER))
                            && dockWindowSurface != null) {
                        excludes = new android.view.SurfaceControl[]{dockWindowSurface};
                    }
                    final CaptureRequest req = request;
''')

replace_once(
    view,
'''                                    android.view.SurfaceControl[] fallbackExcludes = dockWindowSurface != null
                                            ? new android.view.SurfaceControl[]{dockWindowSurface} : null;
                                    String[] fallbackNames = dockWindowLayerName != null
                                            ? new String[]{dockWindowLayerName} : null;
                                    captureClient.captureScreenAsync(req.stripRect, captureScale,
                                            req.displayId, fallbackExcludes, fallbackNames, 1, captureCb);
''',
'''                                    if (!fullDisplayExclusions.safe) {
                                        logW("local launcher-layer capture failed; unresolved freeform "
                                                + "surface, wallpaper fallback");
                                        captureClient.captureScreenAsync(req.stripRect, captureScale,
                                                req.displayId, null, null, 2, captureCb);
                                        return;
                                    }
                                    android.view.SurfaceControl[] fallbackExcludes = dockWindowSurface != null
                                            ? new android.view.SurfaceControl[]{dockWindowSurface} : null;
                                    captureClient.captureScreenAsync(req.stripRect, captureScale,
                                            req.displayId, fallbackExcludes,
                                            fullDisplayExclusions.layerNames, 1, captureCb);
''')

replace_once(
    view,
'''                        if (workstationMode
                                && (hasValidDockWindowSurface() || dockWindowLayerName != null)) {
                            logW("local launcher-layer API unavailable; safe full-display fallback scene="
                                    + requestScene);
                            actualSource = CaptureSourcePolicy.Source.FULL_DISPLAY;
                        } else {
''',
'''                        if (workstationMode
                                && (hasValidDockWindowSurface() || dockWindowLayerName != null)
                                && fullDisplayExclusions.safe) {
                            logW("local launcher-layer API unavailable; safe full-display fallback scene="
                                    + requestScene);
                            actualSource = CaptureSourcePolicy.Source.FULL_DISPLAY;
                        } else {
''')

replace_once(
    view,
'''                    boolean wallpaperMode = actualSource == CaptureSourcePolicy.Source.WALLPAPER;
                    String[] excludeNames = null;
                    if (actualSource == CaptureSourcePolicy.Source.FULL_DISPLAY) {
                        excludeNames = dockWindowLayerName != null
                                ? new String[]{dockWindowLayerName, dragLayerName}
                                : (dragLayerName != null ? new String[]{dragLayerName} : null);
                    }
''',
'''                    if (actualSource == CaptureSourcePolicy.Source.FULL_DISPLAY
                            && !fullDisplayExclusions.safe) {
                        logW("full-display capture blocked: visible freeform task has no "
                                + "resolvable SurfaceFlinger layer; wallpaper fallback");
                        actualSource = CaptureSourcePolicy.Source.WALLPAPER;
                    }
                    boolean wallpaperMode = actualSource == CaptureSourcePolicy.Source.WALLPAPER;
                    String[] excludeNames = actualSource == CaptureSourcePolicy.Source.FULL_DISPLAY
                            ? fullDisplayExclusions.layerNames : null;
''')
