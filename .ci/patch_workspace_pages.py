from pathlib import Path

path = Path('src/main/java/com/hellovoid/liquiddock/HomeGridHook.java')
s = path.read_text()
old = '''    private static void refreshWorkspaceGrid(android.view.View workspace) {
        try {
            int count = (Integer) HookUtil.invoke(workspace, "getScreenCount");
            for (int i = 0; i < count; i++) {
                Object cell = HookUtil.invoke(workspace, "getCellLayout",
                        new Class[]{int.class}, i);
                if (!(cell instanceof android.view.View)) continue;
                HookUtil.invoke(cell, "calculateXsAndYs");
                android.view.View page = (android.view.View) cell;
                page.forceLayout();
                page.requestLayout();
                page.invalidate();
            }
            workspace.forceLayout();
            workspace.requestLayout();
            workspace.invalidate();
        } catch (Throwable e) {
            MainHook.log("[DC] rotation grid refresh failed: " + e);
        }
    }
'''
new = '''    private static void refreshWorkspaceGrid(android.view.View workspace) {
        try {
            java.util.ArrayList<android.view.View> pages = new java.util.ArrayList<>();
            collectWorkspaceCellLayouts(workspace, pages);
            if (pages.isEmpty()) {
                MainHook.log("[DC] rotation grid refresh: no CellLayout descendants");
                return;
            }
            for (android.view.View page : pages) {
                if (!sizeMatchesOrientation(page, page.getWidth(), page.getHeight())) continue;
                HookUtil.invoke(page, "calculateXsAndYs");
                page.forceLayout();
                page.requestLayout();
                page.invalidate();
            }
            workspace.forceLayout();
            workspace.requestLayout();
            workspace.invalidate();
            MainHook.log("[DC] rotation grid refreshed pages=" + pages.size()
                    + " ws=" + workspace.getWidth() + "x" + workspace.getHeight());
        } catch (Throwable e) {
            MainHook.log("[DC] rotation grid refresh failed: " + e);
        }
    }

    private static void collectWorkspaceCellLayouts(android.view.View view,
                                                     java.util.List<android.view.View> out) {
        if (view == null) return;
        if ("com.miui.home.launcher.CellLayout".equals(view.getClass().getName())) {
            out.add(view);
            return;
        }
        if (!(view instanceof android.view.ViewGroup)) return;
        android.view.ViewGroup group = (android.view.ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectWorkspaceCellLayouts(group.getChildAt(i), out);
        }
    }
'''
if old not in s:
    raise SystemExit('refreshWorkspaceGrid target not found')
s = s.replace(old, new, 1)

# Bound the orientation wait without ever forcing a refresh from old-orientation bounds.
old2 = '''                if (orientationReady && stableFrames >= 2) {
                    refreshWorkspaceGrid(workspace);
                    workspace.postDelayed(() -> refreshWorkspaceGridIfReady(workspace), 180L);
                    workspace.postDelayed(() -> refreshWorkspaceGridIfReady(workspace), 500L);
                    return;
                }
                workspace.postOnAnimation(this);
'''
new2 = '''                if (orientationReady && stableFrames >= 2) {
                    refreshWorkspaceGrid(workspace);
                    workspace.postDelayed(() -> refreshWorkspaceGridIfReady(workspace), 180L);
                    workspace.postDelayed(() -> refreshWorkspaceGridIfReady(workspace), 500L);
                    return;
                }
                if (frames >= 180) {
                    MainHook.log("[DC] rotation grid wait timed out: ws="
                            + width + "x" + height + " orientation="
                            + workspace.getResources().getConfiguration().orientation);
                    return;
                }
                workspace.postOnAnimation(this);
'''
if old2 not in s:
    raise SystemExit('rotation wait target not found')
s = s.replace(old2, new2, 1)
path.write_text(s)
