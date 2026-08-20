package com.hellovoid.liquiddock;

import android.content.res.Configuration;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only first stage of the ColorOS-inspired 10x6 rotation bridge.
 *
 * The bridge intentionally does not mutate ItemInfo or Launcher persistence yet. It records the
 * stable source-orientation layout, compares MIUI's native target result with the pure planner,
 * and inventories the target device's CellLayout occupancy APIs. A later experiment can enable
 * write-back only after those private signatures are confirmed on-device.
 */
final class HomeGridRotationBridge {
    static final boolean OBSERVE_ONLY = true;

    private static boolean installed;
    private static HomeGridProfile profile = HomeGridProfile.GRID_8X4;
    private static WeakReference<android.view.View> workspaceRef = new WeakReference<>(null);
    private static final Map<Long, OrientationMemory> memory = new HashMap<>();
    private static Boolean lastStablePortrait;
    private static int scheduleGeneration;
    private static boolean apiInventoryLogged;

    private HomeGridRotationBridge() {}

    static void install(ClassLoader classLoader, boolean customGridEnabled,
                        HomeGridProfile selectedProfile) {
        if (installed || !customGridEnabled
                || selectedProfile != HomeGridProfile.GRID_10X6) {
            return;
        }
        installed = true;
        profile = selectedProfile;

        try {
            Class<?> launcher = Class.forName(
                    "com.miui.home.launcher.Launcher", false, classLoader);
            HookUtil.hookMethod(launcher, "onConfigurationChanged",
                    new Class[]{Configuration.class}, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        android.view.View beforeWorkspace = workspaceFromLauncher(
                                chain.getThisObject());
                        Boolean sourcePortrait = lastStablePortrait;
                        if (beforeWorkspace != null && sourcePortrait != null) {
                            captureWorkspace(beforeWorkspace, sourcePortrait, "pre-rotation");
                        }

                        Object result = chain.proceed(args);

                        Configuration next = args.length > 0 && args[0] instanceof Configuration
                                ? (Configuration) args[0] : null;
                        boolean targetPortrait = next != null
                                ? next.orientation == Configuration.ORIENTATION_PORTRAIT
                                : !Boolean.TRUE.equals(sourcePortrait);
                        android.view.View afterWorkspace = workspaceFromLauncher(
                                chain.getThisObject());
                        if (afterWorkspace != null) {
                            workspaceRef = new WeakReference<>(afterWorkspace);
                            scheduleRotationDiagnostics(afterWorkspace, targetPortrait);
                        }
                        return result;
                    });

            HookUtil.hookMethod(classLoader, "com.miui.home.launcher.Launcher",
                    "setupViews", chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        android.view.View workspace = workspaceFromLauncher(chain.getThisObject());
                        if (workspace != null) {
                            workspaceRef = new WeakReference<>(workspace);
                            boolean portrait = workspace.getResources().getConfiguration().orientation
                                    == Configuration.ORIENTATION_PORTRAIT;
                            scheduleBaselineCapture(workspace, portrait);
                        }
                        return result;
                    });

            MainHook.log("[DC][GRID10] read-only rotation bridge installed");
        } catch (Throwable error) {
            installed = false;
            MainHook.log("[DC][GRID10] rotation bridge unavailable: " + error);
        }
    }

    private static android.view.View workspaceFromLauncher(Object launcher) {
        try {
            Object candidate = HookUtil.getField(launcher, "mWorkspace");
            return candidate instanceof android.view.View
                    ? (android.view.View) candidate : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void scheduleBaselineCapture(android.view.View workspace, boolean portrait) {
        final int generation = ++scheduleGeneration;
        workspace.post(new Runnable() {
            private int frames;
            private int stableFrames;
            private int lastWidth = -1;
            private int lastHeight = -1;

            @Override public void run() {
                if (generation != scheduleGeneration || !workspace.isAttachedToWindow()) return;
                int width = workspace.getWidth();
                int height = workspace.getHeight();
                if (width > 0 && height > 0 && width == lastWidth && height == lastHeight) {
                    stableFrames++;
                } else {
                    lastWidth = width;
                    lastHeight = height;
                    stableFrames = 0;
                }
                frames++;
                if (stableFrames >= 2 && workspaceHasTargetGrid(workspace, portrait)) {
                    captureWorkspace(workspace, portrait, "baseline");
                    lastStablePortrait = portrait;
                    return;
                }
                if (frames < 180) workspace.postOnAnimation(this);
            }
        });
    }

    private static void scheduleRotationDiagnostics(android.view.View workspace,
                                                    boolean targetPortrait) {
        final int generation = ++scheduleGeneration;
        workspace.post(new Runnable() {
            private int frames;
            private int stableFrames;
            private int lastWidth = -1;
            private int lastHeight = -1;

            @Override public void run() {
                if (generation != scheduleGeneration || !workspace.isAttachedToWindow()) return;
                int width = workspace.getWidth();
                int height = workspace.getHeight();
                if (width > 0 && height > 0 && width == lastWidth && height == lastHeight) {
                    stableFrames++;
                } else {
                    lastWidth = width;
                    lastHeight = height;
                    stableFrames = 0;
                }
                frames++;
                if (stableFrames >= 2 && workspaceHasTargetGrid(workspace, targetPortrait)) {
                    diagnoseNativeTarget(workspace, targetPortrait);
                    lastStablePortrait = targetPortrait;
                    return;
                }
                if (frames >= 180) {
                    MainHook.log("[DC][GRID10] diagnostic wait timed out target="
                            + (targetPortrait ? "portrait" : "landscape")
                            + " ws=" + width + "x" + height);
                    return;
                }
                workspace.postOnAnimation(this);
            }
        });
    }

    private static boolean workspaceHasTargetGrid(android.view.View workspace, boolean portrait) {
        ArrayList<android.view.ViewGroup> pages = new ArrayList<>();
        collectCellLayouts(workspace, pages);
        if (pages.isEmpty()) return false;
        int expectedColumns = profile.columns(portrait);
        int expectedRows = profile.rows(portrait);
        for (android.view.ViewGroup page : pages) {
            int columns = readInt(page, "mHCells", -1);
            int rows = readInt(page, "mVCells", -1);
            if (columns == expectedColumns && rows == expectedRows) return true;
        }
        return false;
    }

    private static void captureWorkspace(android.view.View workspace, boolean portrait,
                                         String reason) {
        ArrayList<android.view.ViewGroup> pages = new ArrayList<>();
        collectCellLayouts(workspace, pages);
        int expectedColumns = profile.columns(portrait);
        int expectedRows = profile.rows(portrait);
        int captured = 0;
        synchronized (memory) {
            for (android.view.ViewGroup page : pages) {
                int columns = readInt(page, "mHCells", -1);
                int rows = readInt(page, "mVCells", -1);
                if (columns != expectedColumns || rows != expectedRows) continue;
                for (int i = 0; i < page.getChildCount(); i++) {
                    Object item = page.getChildAt(i).getTag();
                    ItemSnapshot snapshot = snapshot(item);
                    if (snapshot == null) continue;
                    OrientationMemory state = memory.computeIfAbsent(
                            snapshot.id, ignored -> new OrientationMemory());
                    HomeGridRotationPlanner.Position position = snapshot.position();
                    if (portrait) state.portrait = position;
                    else state.landscape = position;
                    captured++;
                }
                logCellLayoutApiOnce(page);
            }
        }
        MainHook.log("[DC][GRID10] captured " + reason + " orientation="
                + (portrait ? "portrait" : "landscape") + " items=" + captured);
    }

    private static void diagnoseNativeTarget(android.view.View workspace, boolean targetPortrait) {
        ArrayList<android.view.ViewGroup> pages = new ArrayList<>();
        collectCellLayouts(workspace, pages);
        ArrayList<HomeGridRotationPlanner.Item> plannerItems = new ArrayList<>();
        Map<Long, HomeGridRotationPlanner.Position> rememberedTarget = new HashMap<>();
        Map<Long, ItemSnapshot> nativeTarget = new HashMap<>();

        int targetColumns = profile.columns(targetPortrait);
        int targetRows = profile.rows(targetPortrait);
        int sourceColumns = profile.columns(!targetPortrait);
        int sourceRows = profile.rows(!targetPortrait);

        synchronized (memory) {
            for (android.view.ViewGroup page : pages) {
                int columns = readInt(page, "mHCells", -1);
                int rows = readInt(page, "mVCells", -1);
                if (columns != targetColumns || rows != targetRows) continue;
                logCellLayoutApiOnce(page);
                for (int i = 0; i < page.getChildCount(); i++) {
                    Object item = page.getChildAt(i).getTag();
                    ItemSnapshot current = snapshot(item);
                    if (current == null) continue;
                    nativeTarget.put(current.id, current);
                    OrientationMemory state = memory.get(current.id);
                    if (state == null) continue;
                    HomeGridRotationPlanner.Position source = targetPortrait
                            ? state.landscape : state.portrait;
                    HomeGridRotationPlanner.Position remembered = targetPortrait
                            ? state.portrait : state.landscape;
                    if (source == null) continue;
                    plannerItems.add(new HomeGridRotationPlanner.Item(
                            current.id,
                            current.screenId,
                            source.x,
                            source.y,
                            source.spanX,
                            source.spanY,
                            current.spanX,
                            current.spanY));
                    if (remembered != null) rememberedTarget.put(current.id, remembered);
                }
            }
        }

        HomeGridRotationPlanner.Plan plan = HomeGridRotationPlanner.plan(
                sourceColumns, sourceRows, targetColumns, targetRows,
                plannerItems, rememberedTarget);
        boolean nativeValid = isNativeLayoutValid(nativeTarget.values(), targetColumns, targetRows);
        int differences = 0;
        for (HomeGridRotationPlanner.Item item : plannerItems) {
            ItemSnapshot nativeItem = nativeTarget.get(item.id);
            HomeGridRotationPlanner.Position planned = plan.position(item.id);
            if (nativeItem == null) continue;
            if (planned == null) {
                MainHook.log("[DC][GRID10][PLAN] unresolved id=" + item.id
                        + " native=" + nativeItem.compact());
                differences++;
                continue;
            }
            if (!planned.equals(nativeItem.position())) {
                MainHook.log("[DC][GRID10][PLAN] id=" + item.id
                        + " native=" + nativeItem.compact()
                        + " proposed=" + compact(planned));
                differences++;
            }
        }
        MainHook.log("[DC][GRID10][PLAN] target="
                + (targetPortrait ? "portrait" : "landscape")
                + " items=" + plannerItems.size()
                + " nativeValid=" + nativeValid
                + " differences=" + differences
                + " observeOnly=" + OBSERVE_ONLY);
    }

    private static boolean isNativeLayoutValid(Iterable<ItemSnapshot> items,
                                               int columns, int rows) {
        Map<Long, boolean[][]> occupancy = new HashMap<>();
        for (ItemSnapshot item : items) {
            if (item.x < 0 || item.y < 0 || item.spanX <= 0 || item.spanY <= 0
                    || item.x + item.spanX > columns || item.y + item.spanY > rows) {
                return false;
            }
            boolean[][] cells = occupancy.computeIfAbsent(item.screenId,
                    ignored -> new boolean[columns][rows]);
            for (int x = item.x; x < item.x + item.spanX; x++) {
                for (int y = item.y; y < item.y + item.spanY; y++) {
                    if (cells[x][y]) return false;
                    cells[x][y] = true;
                }
            }
        }
        return true;
    }

    private static void logCellLayoutApiOnce(android.view.ViewGroup page) {
        if (apiInventoryLogged) return;
        apiInventoryLogged = true;
        String[] targets = new String[]{
                "updateCellOccupiedMarks", "relayoutByOccupiedCells",
                "setupLayoutParam", "saveCurrentLayout"
        };
        Class<?> current = page.getClass();
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                for (String target : targets) {
                    if (!target.equals(method.getName())) continue;
                    MainHook.log("[DC][GRID10][API] " + method.toGenericString());
                }
            }
            current = current.getSuperclass();
        }
    }

    private static ItemSnapshot snapshot(Object item) {
        if (item == null) return null;
        long id = readLong(item, "id", Long.MIN_VALUE);
        long screenId = readLong(item, "screenId", Long.MIN_VALUE);
        int x = readInt(item, "cellX", Integer.MIN_VALUE);
        int y = readInt(item, "cellY", Integer.MIN_VALUE);
        int spanX = readInt(item, "spanX", Integer.MIN_VALUE);
        int spanY = readInt(item, "spanY", Integer.MIN_VALUE);
        if (id == Long.MIN_VALUE || screenId == Long.MIN_VALUE
                || x == Integer.MIN_VALUE || y == Integer.MIN_VALUE
                || spanX <= 0 || spanY <= 0) {
            return null;
        }
        return new ItemSnapshot(id, screenId, x, y, spanX, spanY);
    }

    private static void collectCellLayouts(android.view.View view,
                                           List<android.view.ViewGroup> out) {
        if (view == null) return;
        if ("com.miui.home.launcher.CellLayout".equals(view.getClass().getName())
                && view instanceof android.view.ViewGroup) {
            out.add((android.view.ViewGroup) view);
            return;
        }
        if (!(view instanceof android.view.ViewGroup)) return;
        android.view.ViewGroup group = (android.view.ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectCellLayouts(group.getChildAt(i), out);
        }
    }

    private static int readInt(Object target, String fieldName, int fallback) {
        Object value = readNumber(target, fieldName);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static long readLong(Object target, String fieldName, long fallback) {
        Object value = readNumber(target, fieldName);
        return value instanceof Number ? ((Number) value).longValue() : fallback;
    }

    private static Object readNumber(Object target, String fieldName) {
        if (target == null) return null;
        try {
            Field field = HookUtil.findField(target.getClass(), fieldName);
            return field.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String compact(HomeGridRotationPlanner.Position position) {
        return position.screenId + ":" + position.x + "," + position.y
                + "/" + position.spanX + "x" + position.spanY;
    }

    private static final class OrientationMemory {
        HomeGridRotationPlanner.Position landscape;
        HomeGridRotationPlanner.Position portrait;
    }

    private static final class ItemSnapshot {
        final long id;
        final long screenId;
        final int x;
        final int y;
        final int spanX;
        final int spanY;

        ItemSnapshot(long id, long screenId, int x, int y, int spanX, int spanY) {
            this.id = id;
            this.screenId = screenId;
            this.x = x;
            this.y = y;
            this.spanX = spanX;
            this.spanY = spanY;
        }

        HomeGridRotationPlanner.Position position() {
            return new HomeGridRotationPlanner.Position(screenId, x, y, spanX, spanY);
        }

        String compact() {
            return screenId + ":" + x + "," + y + "/" + spanX + "x" + spanY;
        }
    }
}
