from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, got {count}")
    return text.replace(old, new, 1)

# 1) Remove the external startCapture hook.  The proven fix lives inside the
# mode-2 capture path and therefore does not disturb Dock cadence/observation.
module_path = Path("src/main/java/com/hellovoid/liquiddock/ModuleMain.java")
module = module_path.read_text(encoding="utf-8")
module = replace_once(module, '''        try {
            // Install the module-internal HOME capture guard before MainHook registers the
            // Launcher gesture hooks that can trigger the first HOME capture.
            HomeCaptureBarrier.install();

            ClassLoader classLoader = param.getClassLoader();
''', '''        try {
            ClassLoader classLoader = param.getClassLoader();
''', "ModuleMain external barrier")
module_path.write_text(module, encoding="utf-8")

barrier_path = Path("src/main/java/com/hellovoid/liquiddock/HomeCaptureBarrier.java")
if barrier_path.exists():
    barrier_path.unlink()

# 2) Restore the previously device-verified 0f49d4d + 50fc172 HOME settle
# behavior directly inside DockLiquidGlassView.
dock_path = Path("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java")
dock = dock_path.read_text(encoding="utf-8")

dock = replace_once(dock, '''        sceneState.setGestureTarget(target, System.nanoTime());
        updateDesiredScene();
''', '''        sceneState.setGestureTarget(target, System.nanoTime());
        // GestureToHome is emitted before launcher focus while Dock icons are still
        // flying into place.  Open the settle window here, but do not block the normal
        // capture scheduler: cached HOME wallpaper crops remain fully live.
        if ("HOME".equals(target)) {
            beginHomeSettle("gesture-home");
        }
        updateDesiredScene();
''', "gesture HOME settle")

dock = replace_once(dock, '''    void onLauncherFocused() {
        mainHandler.postDelayed(() -> {
            if (!isCaptureAllowed()) return;
            requestStateCapture("focus-home");
        }, 500L);
    }
''', '''    void onLauncherFocused() {
        // Extend the window opened by GestureToHome.  This does not pause Dock motion;
        // only a new HOME SurfaceFlinger wrapper read is deferred below.
        beginHomeSettle("launcher-focus");
    }
''', "launcher focus settle")

settle_anchor = '''    /** After installing a frame, if rotation stabilization is active, compare content
     *  signatures and schedule another capture until the wallpaper converges. */
'''
settle_code = '''    // HOME transition settle.  HyperOS' Wallpaper BBQ wrapper can temporarily contain
    // the Dock icon fly-in/collapse animation.  During this window, cache serves are
    // deliberately still allowed, so geometry changes and spring-back motion continue
    // to recrop/install at normal cadence; only a new mode-2 SF read is postponed.
    private static final long HOME_SETTLE_MS = 650L;
    private long homeSettleUntilNanos;
    private boolean homeSettleCapturePending;

    private boolean isHomeSettleActive() {
        return System.nanoTime() < homeSettleUntilNanos;
    }

    private void beginHomeSettle(String reason) {
        long until = System.nanoTime() + HOME_SETTLE_MS * 1_000_000L;
        homeSettleUntilNanos = Math.max(homeSettleUntilNanos, until);
        logI("home settle opened reason=" + reason);
        scheduleHomeSettledCapture();
    }

    private void scheduleHomeSettledCapture() {
        if (homeSettleCapturePending) return;
        long remaining = homeSettleUntilNanos - System.nanoTime();
        if (remaining <= 0L) {
            requestStateCapture("home-settled");
            return;
        }
        homeSettleCapturePending = true;
        mainHandler.postDelayed(() -> {
            homeSettleCapturePending = false;
            if (!attached) return;
            if (isHomeSettleActive()) {
                scheduleHomeSettledCapture();
                return;
            }
            updateDesiredScene();
            if (sceneState.desired() == CaptureScene.HOME) {
                requestStateCapture("home-settled");
            }
        }, Math.max(1L, (remaining + 999_999L) / 1_000_000L));
    }

'''
dock = replace_once(dock, settle_anchor, settle_code + settle_anchor, "settle helper insertion")

dock = replace_once(dock, '''                    if (wallpaperMode && tryServeWallpaperFromCache(
                            req, requestScene, requestSceneRevision, attempt)) {
                        return;
                    }
                    logI("capture mode=" + (wallpaperMode ? 2 : 1)
''', '''                    if (wallpaperMode && tryServeWallpaperFromCache(
                            req, requestScene, requestSceneRevision, attempt)) {
                        return;
                    }
                    // Cache miss during the icon fly-in is the only operation we defer.
                    // The scheduler itself stays alive and a clean HOME frame is requested
                    // once the wrapper has settled.
                    if (wallpaperMode && isHomeSettleActive()) {
                        mainHandler.post(() -> {
                            if (activeCaptureAttempt != attempt) return;
                            retireCaptureAttempt(attempt);
                            sourceDirty = true;
                            scheduleHomeSettledCapture();
                            logI("HOME SF capture deferred attempt=" + attempt
                                    + " during Dock settle");
                        });
                        return;
                    }
                    logI("capture mode=" + (wallpaperMode ? 2 : 1)
''', "async mode2 settle defer")

dock = replace_once(dock, '''                    client.captureScreenAsync(req.stripRect, captureScale, req.displayId,
                            wallpaperMode ? null : excludes, excludeNames,
                            wallpaperMode ? 2 : 1,
''', '''                    // Exclude the Floating Dock in both modes.  On HyperOS the wallpaper
                    // wrapper can reuse Dock content during fly-in/collapse; the explicit
                    // surface exclusion prevents that content from being composited even if
                    // a capture happens just outside the settle window.
                    client.captureScreenAsync(req.stripRect, captureScale, req.displayId,
                            excludes, excludeNames,
                            wallpaperMode ? 2 : 1,
''', "mode2 Dock exclusion")

dock = replace_once(dock, '''                } else {
                    strip = client.captureWallpaper(request.stripRect, captureScale, request.displayId);
''', '''                } else {
                    final boolean syncWallpaperMode = requestScene == CaptureScene.HOME;
                    if (syncWallpaperMode && isHomeSettleActive()) {
                        mainHandler.post(() -> {
                            if (activeCaptureAttempt != attempt) return;
                            retireCaptureAttempt(attempt);
                            sourceDirty = true;
                            scheduleHomeSettledCapture();
                            logI("HOME sync capture deferred attempt=" + attempt
                                    + " during Dock settle");
                        });
                        return;
                    }
                    strip = client.captureWallpaper(request.stripRect, captureScale, request.displayId);
''', "sync HOME settle defer")

dock_path.write_text(dock, encoding="utf-8")

# 3) Preserve the matching idempotent layout-param fix from 0f49d4d.  A blind
# setLayoutParams at the end of the Dock animation forces an avoidable layout pass.
main_path = Path("src/main/java/com/hellovoid/liquiddock/MainHook.java")
main = main_path.read_text(encoding="utf-8")
main = replace_once(main, '''                if (glassLp != null) {
                    // Match the stroke overlay exactly (bgW/bgH already include the
                    // updateBackgroundView spacing/offset adjustments).
                    glassLp.width = bgW; glassLp.height = bgH;
                    liquidGlassView.setLayoutParams(glassLp);
                }
''', '''                if (glassLp != null) {
                    // Match the stroke overlay exactly, but only request a parent layout
                    // when size really changed.  Re-applying identical LayoutParams at the
                    // animation tail causes a visible scheduling/layout hitch on HyperOS.
                    if (glassLp.width != bgW || glassLp.height != bgH) {
                        glassLp.width = bgW; glassLp.height = bgH;
                        liquidGlassView.setLayoutParams(glassLp);
                    }
                }
''', "idempotent glass layout")
main_path.write_text(main, encoding="utf-8")

# 4) Delete the obsolete explicit Compose sync helpers.  LiquidDockApp's local
# SharedPreferences listener owns ordinary UI -> Remote Preferences propagation.
compose_path = Path("src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt")
compose = compose_path.read_text(encoding="utf-8")
compose = replace_once(compose, '''// API101 Remote Preferences are the only runtime config transport.  The settings UI
// keeps AndroidX default preferences as its local state store; every write is mirrored
// to LSPosed's Remote Preferences without su, chmod, or cross-UID files.
private fun syncConfigNow(prefs: SharedPreferences, ctx: Context) {
    if (!LiquidDockApp.syncToRemote(prefs)) {
        android.util.Log.w("LiquidDock", "Remote Preferences service is not connected yet")
    }
}
private fun requestJsonSync(prefs: SharedPreferences, ctx: Context) {
    syncConfigNow(prefs, ctx)
}

''', '''// Ordinary UI writes are mirrored to API101 Remote Preferences by LiquidDockApp's
// SharedPreferences listener.  No per-control JSON/file/root synchronization exists.

''', "remove sync helper functions")
compose = replace_once(compose,
    'onClick = { syncConfigNow(prefs, activity); activity.restartLauncher() }',
    'onClick = { activity.restartLauncher() }',
    "restart action sync helper")
compose = replace_once(compose, '''    var value by remember(key) { mutableStateOf(prefs.getBoolean(key, default)) }
    val context = LocalContext.current
    SwitchPreference(
''', '''    var value by remember(key) { mutableStateOf(prefs.getBoolean(key, default)) }
    SwitchPreference(
''', "BooleanSetting context cleanup")
compose = replace_once(compose,
    'onCheckedChange = { value = it; prefs.edit().putBoolean(key, it).apply(); onChanged(it); requestJsonSync(prefs, context) },',
    'onCheckedChange = { value = it; prefs.edit().putBoolean(key, it).apply(); onChanged(it) },',
    "BooleanSetting sync helper call")
compose = replace_once(compose, '''        editor.apply()
        requestJsonSync(prefs, context)
    }
''', '''        editor.apply()
    }
''', "IntSetting sync helper call")
compose = replace_once(compose, '''    Toast.makeText(activity, "默认预设已应用", Toast.LENGTH_LONG).show()
    syncConfigNow(PreferenceManager.getDefaultSharedPreferences(activity), activity)
    activity.restartLauncher()
''', '''    Toast.makeText(activity, "默认预设已应用", Toast.LENGTH_LONG).show()
    activity.restartLauncher()
''', "preset sync helper call")
if "syncConfigNow(" in compose or "requestJsonSync(" in compose:
    raise SystemExit("obsolete sync helper reference remains in ComposeSettingsActivity")
compose_path.write_text(compose, encoding="utf-8")

print("Applied proven HOME settle + Remote Preferences UI cleanup")
