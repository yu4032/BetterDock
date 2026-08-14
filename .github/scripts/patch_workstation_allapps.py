from pathlib import Path

path = Path("src/main/java/com/hellovoid/liquiddock/HomeGridHook.java")
text = path.read_text()

old_identity_gate = '''            int width = layout.getWidth();
            int height = layout.getHeight();
            if (width <= 0 || height <= 0) return;
            // MIUI can invoke calculateXsAndYs() after Configuration has switched but
            // before this CellLayout has the new orientation bounds. Never write grid
            // geometry from the previous orientation's stable size.
            if (!sizeMatchesOrientation(layout, width, height)) return;

            boolean workstation = workstationMode || MainHook.isWorkstationMode();
            boolean workstationAllApps = workstation && isLaptopAllApps(cellLayout);

            // Laptop All Apps has its own GridType/GridConfig.  The system DEX identifies
            // it through CellLayout.isInLapTopAllApps() and
            // GRID_TYPE_IN_ALL_APPS_WORKSPACE. Preserve that dedicated geometry instead
            // of replacing it with the normal Workspace centering formula. The 8x4 count
            // still applies; cellSize below shrinks as needed to stay inside this layout.
'''
new_identity_gate = '''            int width = layout.getWidth();
            int height = layout.getHeight();
            if (width <= 0 || height <= 0) return;
            // Laptop All Apps is a dedicated CellLayout whose identity is stored on
            // CellLayout.mGridType. Detect it before applying the normal Workspace bounds
            // guard: the overlay has its own GridConfig and layout timing.
            boolean workstationAllApps = isLaptopAllApps(cellLayout);
            // MIUI can invoke calculateXsAndYs() after Configuration has switched but
            // before a normal Workspace CellLayout has the new orientation bounds. Never
            // write normal Workspace geometry from the previous orientation's stable size.
            if (!workstationAllApps && !sizeMatchesOrientation(layout, width, height)) return;

            // A genuine laptop All Apps CellLayout is self-identifying; do not require the
            // global laptop-mode callback to have arrived first.
            boolean workstation = workstationAllApps
                    || workstationMode || MainHook.isWorkstationMode();

            // Laptop All Apps has its own GridType/GridConfig. Preserve that dedicated
            // geometry instead of replacing it with the normal Workspace centering formula.
            // Detection is version-tolerant and no longer depends on one private method.
'''

old_offsets = '''            int workstationX = workstationAllApps
                    ? (portrait ? workstationAllAppsPortraitHorizontalOffset
                            : workstationAllAppsLandscapeHorizontalOffset)
                    : workstationHorizontalOffset;
            int workstationY = workstationAllApps
                    ? (portrait ? workstationAllAppsPortraitVerticalOffset
                            : workstationAllAppsLandscapeVerticalOffset)
                    : 0;
            if (workstation) {
                // Offsets are translations, not symmetric insets. Clamp them against the
                // native margins so the adjusted grid can never be pushed off-screen.
                workstationX = Math.max(-baseLeft, Math.min(baseRight, workstationX));
                workstationY = Math.max(-baseTop, Math.min(baseBottom, workstationY));
            }
            int left = baseLeft + (workstation ? workstationX
                    : (portrait ? portraitLeft : landscapeLeft));
            int right = baseRight + (workstation ? -workstationX
                    : (portrait ? portraitRight : landscapeRight));
            int top = baseTop + (workstation ? workstationY
                    : (portrait ? portraitTop : landscapeTop));
            int bottom = baseBottom + (workstation ? -workstationY
                    : (portrait ? portraitBottom : landscapeBottom));
'''
new_offsets = '''            int left;
            int right;
            int top;
            int bottom;
            if (workstationAllApps) {
                int horizontalMargin = portrait
                        ? workstationAllAppsPortraitHorizontalOffset
                        : workstationAllAppsLandscapeHorizontalOffset;
                int verticalMargin = portrait
                        ? workstationAllAppsPortraitVerticalOffset
                        : workstationAllAppsLandscapeVerticalOffset;
                int[] margins = WorkstationGridMarginPolicy.apply(
                        baseLeft, baseRight, baseTop, baseBottom,
                        horizontalMargin, verticalMargin);
                left = margins[0];
                right = margins[1];
                top = margins[2];
                bottom = margins[3];
            } else if (workstation) {
                // Normal workstation Workspace keeps its existing horizontal translation
                // semantics. Only All Apps switches to explicit symmetric margins.
                int workstationX = Math.max(-baseLeft,
                        Math.min(baseRight, workstationHorizontalOffset));
                left = baseLeft + workstationX;
                right = baseRight - workstationX;
                top = baseTop;
                bottom = baseBottom;
            } else {
                left = baseLeft + (portrait ? portraitLeft : landscapeLeft);
                right = baseRight + (portrait ? portraitRight : landscapeRight);
                top = baseTop + (portrait ? portraitTop : landscapeTop);
                bottom = baseBottom + (portrait ? portraitBottom : landscapeBottom);
            }
'''

old_classifier = '''    private static boolean isLaptopAllApps(Object cellLayout) {
        try {
            Object result = HookUtil.invoke(cellLayout, "isInLapTopAllApps");
            return Boolean.TRUE.equals(result);
        } catch (Throwable ignored) {
            return false;
        }
    }
'''
new_classifier = '''    private static boolean isLaptopAllApps(Object cellLayout) {
        // Stable identity from the launcher: CellLayout.setGridType() stores
        // GRID_TYPE_IN_ALL_APPS_WORKSPACE directly in CellLayout.mGridType. GridConfig
        // does not expose or own that value.
        String gridType = "";
        try {
            Object value = HookUtil.getField(cellLayout, "mGridType");
            if (value != null) gridType = String.valueOf(value);
        } catch (Throwable ignored) {}
        if (gridType.isEmpty()) {
            try {
                Object value = HookUtil.invoke(cellLayout, "getGridType");
                if (value != null) gridType = String.valueOf(value);
            } catch (Throwable ignored) {}
        }

        // Keep the visibility-dependent method only as a secondary compatibility signal.
        boolean exact = false;
        try {
            exact = Boolean.TRUE.equals(HookUtil.invoke(cellLayout, "isInLapTopAllApps"));
        } catch (Throwable ignored) {}

        StringBuilder ancestry = new StringBuilder();
        if (cellLayout instanceof android.view.View) {
            android.view.ViewParent parent = ((android.view.View) cellLayout).getParent();
            int depth = 0;
            while (parent != null && depth++ < 8) {
                if (ancestry.length() > 0) ancestry.append('>');
                ancestry.append(parent.getClass().getName());
                parent = parent.getParent();
            }
        }
        return WorkstationLayoutClassifier.matches(exact, gridType, ancestry.toString());
    }
'''

pairs = [
    (old_identity_gate, new_identity_gate, "identity/timing gate"),
    (old_offsets, new_offsets, "All Apps symmetric margins"),
    (old_classifier, new_classifier, "All Apps classifier"),
]

changed = False
for old, new, name in pairs:
    if new in text:
        continue
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{name}: expected exactly one old block, found {count}")
    text = text.replace(old, new, 1)
    changed = True

if changed:
    path.write_text(text)
    print("patched HomeGridHook workstation All Apps path")
else:
    print("HomeGridHook already patched")
