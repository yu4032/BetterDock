from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected exactly one match, found {count}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


Path("src/main/java/com/hellovoid/liquiddock/LauncherSceneOwnershipPolicy.java").write_text(
'''package com.hellovoid.liquiddock;

/** Distinguishes a real fullscreen APP takeover from a floating/freeform task above Launcher. */
final class LauncherSceneOwnershipPolicy {
    static final int WINDOWING_MODE_FREEFORM = 5;

    private LauncherSceneOwnershipPolicy() {}

    static boolean launcherOwnsScene(boolean launcherResumed, int foregroundWindowingMode) {
        return launcherResumed || foregroundWindowingMode == WINDOWING_MODE_FREEFORM;
    }
}
''', encoding="utf-8")

main = "src/main/java/com/hellovoid/liquiddock/MainHook.java"

replace_once(
    main,
'''            if (paused instanceof Boolean && !((Boolean) paused)) {
                launcherLifecycleKnown = true;
                launcherResumed = true;
            }
            log("[DC] liquid lifecycle seed: known=" + launcherLifecycleKnown
                + " resumed=" + launcherResumed + " paused=" + paused
                + " visible=" + visible + " focus=" + focused);
''',
'''            int windowingMode = foregroundTaskWindowingMode(launcher);
            if (paused instanceof Boolean) {
                launcherLifecycleKnown = true;
                launcherResumed = LauncherSceneOwnershipPolicy.launcherOwnsScene(
                        !((Boolean) paused), windowingMode);
            }
            log("[DC] liquid lifecycle seed: known=" + launcherLifecycleKnown
                + " resumed=" + launcherResumed + " paused=" + paused
                + " visible=" + visible + " focus=" + focused
                + " windowingMode=" + windowingMode);
''')

replace_once(
    main,
'''        } catch (Throwable e) {
            log("[DC] liquid lifecycle seed unavailable; using window gate: " + e);
        }
    }

    private static void installLiquidGlassCaptureHooks(ClassLoader cl) {
''',
'''        } catch (Throwable e) {
            log("[DC] liquid lifecycle seed unavailable; using window gate: " + e);
        }
    }

    /** Windowing mode of the current top task. HyperOS small windows are freeform tasks;
     * they may pause / defocus Launcher while the Launcher surface remains the owning scene. */
    private static int foregroundTaskWindowingMode(Object launcher) {
        if (!(launcher instanceof Activity)) return -1;
        try {
            android.app.ActivityManager am = (android.app.ActivityManager)
                    ((Activity) launcher).getSystemService(Activity.ACTIVITY_SERVICE);
            if (am == null) return -1;
            java.util.List<android.app.ActivityManager.RunningTaskInfo> tasks =
                    am.getRunningTasks(1);
            if (tasks == null || tasks.isEmpty()) return -1;
            Object mode = HookUtil.invoke(tasks.get(0), "getWindowingMode");
            return mode instanceof Integer ? (Integer) mode : -1;
        } catch (Throwable e) {
            log("[DC] foreground task windowing mode unavailable: " + e);
            return -1;
        }
    }

    private static void installLiquidGlassCaptureHooks(ClassLoader cl) {
''')

replace_once(
    main,
'''                        launcherLifecycleKnown = true;
                        launcherResumed = hasFocus;
                        log("[DC] liquid focus: " + hasFocus);
                        if (glass != null) {
                            if (!hasFocus) {
                                // Resolve the APP/layer before requesting the APP scene. Previously
                                // setLauncherState() dirtied capture first, but the collapsed Dock
                                // visibility gate blocked it and left the HOME wallpaper installed.
                                glass.onLauncherFocusLost();
                                glass.refreshForegroundAppLayer();
                                glass.setLauncherState(true, false);
                                glass.prearmAppBackdrop("focus-loss");
                            } else {
                                glass.setLauncherState(true, true);
                                glass.onLauncherFocused();
                            }
                        }
''',
'''                        launcherLifecycleKnown = true;
                        int windowingMode = foregroundTaskWindowingMode(chain.getThisObject());
                        boolean launcherOwnsScene = LauncherSceneOwnershipPolicy.launcherOwnsScene(
                                hasFocus, windowingMode);
                        launcherResumed = launcherOwnsScene;
                        log("[DC] liquid focus: " + hasFocus
                                + " windowingMode=" + windowingMode
                                + " launcherOwnsScene=" + launcherOwnsScene);
                        if (glass != null) {
                            if (!launcherOwnsScene) {
                                // Resolve the APP/layer before requesting the APP scene. A fullscreen
                                // task owns the backdrop; a freeform task does not demote Launcher.
                                glass.onLauncherFocusLost();
                                glass.refreshForegroundAppLayer();
                                glass.setLauncherState(true, false);
                                glass.prearmAppBackdrop("focus-loss");
                            } else {
                                glass.setLauncherState(true, true);
                                if (hasFocus) glass.onLauncherFocused();
                            }
                        }
''')

replace_once(
    main,
'''                HookUtil.hookMethod(Activity.class, "onPause", new Class<?>[0],
                        chain -> {
                            if (launcherClass.isInstance(chain.getThisObject())) {
                                launcherLifecycleKnown = true;
                                launcherResumed = false;
                                log("[DC] liquid lifecycle fallback: onPause");
                                DockLiquidGlassView g = liquidGlassView;
                                if (g != null) g.setLauncherState(true, false);
                            }
                            return chain.proceed(chain.getArgs().toArray(new Object[0]));
                        });
''',
'''                HookUtil.hookMethod(Activity.class, "onPause", new Class<?>[0],
                        chain -> {
                            Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                            if (launcherClass.isInstance(chain.getThisObject())) {
                                launcherLifecycleKnown = true;
                                int windowingMode = foregroundTaskWindowingMode(chain.getThisObject());
                                launcherResumed = LauncherSceneOwnershipPolicy.launcherOwnsScene(
                                        false, windowingMode);
                                log("[DC] liquid lifecycle fallback: onPause windowingMode="
                                        + windowingMode + " launcherOwnsScene=" + launcherResumed);
                                DockLiquidGlassView g = liquidGlassView;
                                if (g != null) g.setLauncherState(true, launcherResumed);
                            }
                            return result;
                        });
''')
