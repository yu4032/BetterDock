from pathlib import Path

p = Path('src/main/java/com/hellovoid/liquiddock/HomeGridHook.java')
s = p.read_text()

old_field = '''    private static final java.util.WeakHashMap<android.view.View, Boolean>\n        loggedWidgetViews = new java.util.WeakHashMap<>();\n'''
new_field = '''    private static final java.util.WeakHashMap<android.view.View, Boolean>\n        loggedWidgetViews = new java.util.WeakHashMap<>();\n    private static final java.util.WeakHashMap<android.view.View, Long>\n        preparedCellLayoutGeometry = new java.util.WeakHashMap<>();\n'''
if old_field not in s:
    raise SystemExit('field insertion target not found')
s = s.replace(old_field, new_field, 1)

old = '''        HookUtil.hookMethod(cellLayout, "onLayout",\n            new Class[]{boolean.class, int.class, int.class, int.class, int.class},\n            chain -> {\n                Object[] args = chain.getArgs().toArray(new Object[0]);\n                Object result = chain.proceed(args);\n                android.view.ViewGroup layout = (android.view.ViewGroup) chain.getThisObject();\n                for (int i = 0; i < layout.getChildCount(); i++)\n                    adaptTwoByOneWidget(layout, layout.getChildAt(i));\n                return result;\n            });\n'''
new = '''        HookUtil.hookMethod(cellLayout, "onLayout",\n            new Class[]{boolean.class, int.class, int.class, int.class, int.class},\n            chain -> {\n                Object[] args = chain.getArgs().toArray(new Object[0]);\n                android.view.ViewGroup layout = (android.view.ViewGroup) chain.getThisObject();\n\n                // setupViews can create off-screen pages before they have usable bounds.\n                // The Workspace-level 0/180/500 ms refresh therefore fixes page 1 while\n                // page 2 may still keep MIUI's stock 6x4-derived mXs/mYs. Prime each\n                // CellLayout from its own first valid bounds, before MIUI positions its\n                // children, so lazy/off-screen pages cannot miss the 8x4 geometry pass.\n                prepareCellLayoutGeometryForLayout(layout);\n\n                Object result = chain.proceed(args);\n                for (int i = 0; i < layout.getChildCount(); i++)\n                    adaptTwoByOneWidget(layout, layout.getChildAt(i));\n                return result;\n            });\n'''
if old not in s:
    raise SystemExit('onLayout hook target not found')
s = s.replace(old, new, 1)

marker = '''    private static void rebuildCellCoordinates(Object cellLayout) {\n'''
helper = '''    private static void prepareCellLayoutGeometryForLayout(android.view.View layout) {\n        if (!grid8x4Enabled) return;\n        int width = layout.getWidth();\n        int height = layout.getHeight();\n        if (width <= 0 || height <= 0 || !sizeMatchesOrientation(layout, width, height)) return;\n\n        int orientation = layout.getResources().getConfiguration().orientation;\n        long signature = (((long) orientation & 0xffL) << 56)\n                ^ (((long) width & 0x0fffffffL) << 28)\n                ^ ((long) height & 0x0fffffffL);\n        synchronized (preparedCellLayoutGeometry) {\n            Long previous = preparedCellLayoutGeometry.get(layout);\n            if (previous != null && previous == signature) return;\n            // Mark before applying so a nested/requested layout cannot recurse forever.\n            preparedCellLayoutGeometry.put(layout, signature);\n        }\n\n        applyCellLayoutOffsets(layout);\n        rebuildCellCoordinates(layout);\n        MainHook.log("[DC] CellLayout geometry prepared "\n                + width + "x" + height + " orientation=" + orientation);\n    }\n\n'''
if marker not in s:
    raise SystemExit('helper insertion target not found')
s = s.replace(marker, helper + marker, 1)

p.write_text(s)
