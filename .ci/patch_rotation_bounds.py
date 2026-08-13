from pathlib import Path

path = Path('src/main/java/com/hellovoid/liquiddock/HomeGridHook.java')
s = path.read_text()

old = '''            int width = layout.getWidth();
            int height = layout.getHeight();
            if (width <= 0 || height <= 0) return;

            boolean workstation = workstationMode || MainHook.isWorkstationMode();
'''
new = '''            int width = layout.getWidth();
            int height = layout.getHeight();
            if (width <= 0 || height <= 0) return;
            // MIUI can invoke calculateXsAndYs() after Configuration has switched but
            // before this CellLayout has the new orientation bounds. Never write grid
            // geometry from the previous orientation's stable size.
            if (!sizeMatchesOrientation(layout, width, height)) return;

            boolean workstation = workstationMode || MainHook.isWorkstationMode();
'''
if old not in s:
    raise SystemExit('applyCellLayoutOffsets target not found')
s = s.replace(old, new, 1)

old = '''                frames++;
                // Wait until the new-orientation Workspace bounds have survived two
                // consecutive frames. A bounded fallback avoids waiting forever on OEM
                // builds that keep animating insets during rotation.
                if ((width > 0 && height > 0 && stableFrames >= 2) || frames >= 18) {
                    refreshWorkspaceGrid(workspace);
                    workspace.postDelayed(() -> refreshWorkspaceGrid(workspace), 180L);
                    workspace.postDelayed(() -> refreshWorkspaceGrid(workspace), 500L);
                    return;
                }
                workspace.postOnAnimation(this);
'''
new = '''                frames++;
                // Stable old bounds are still wrong bounds. Only settle after Workspace
                // dimensions match the new Configuration orientation and stay unchanged
                // for two consecutive frames. Do not force a frame-count fallback: that
                // was the source of the persistent landscape-down / portrait-right drift.
                boolean orientationReady = width > 0 && height > 0
                    && sizeMatchesOrientation(workspace, width, height);
                if (orientationReady && stableFrames >= 2) {
                    refreshWorkspaceGrid(workspace);
                    workspace.postDelayed(() -> refreshWorkspaceGridIfReady(workspace), 180L);
                    workspace.postDelayed(() -> refreshWorkspaceGridIfReady(workspace), 500L);
                    return;
                }
                workspace.postOnAnimation(this);
'''
if old not in s:
    raise SystemExit('scheduleStableRotationRefresh target not found')
s = s.replace(old, new, 1)

marker = '''    static void scheduleAllPageRefresh() {
'''
helper = '''    private static boolean sizeMatchesOrientation(android.view.View view,
                                                  int width, int height) {
        int orientation = view.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) return height >= width;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) return width >= height;
        return true;
    }

    private static void refreshWorkspaceGridIfReady(android.view.View workspace) {
        int width = workspace.getWidth();
        int height = workspace.getHeight();
        if (width <= 0 || height <= 0
                || !sizeMatchesOrientation(workspace, width, height)) return;
        refreshWorkspaceGrid(workspace);
    }

'''
if marker not in s:
    raise SystemExit('helper insertion point not found')
s = s.replace(marker, helper + marker, 1)

path.write_text(s)
