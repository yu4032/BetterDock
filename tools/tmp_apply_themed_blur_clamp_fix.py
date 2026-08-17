from pathlib import Path

root = Path('.')
glass_path = root / 'src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java'
pipeline_path = root / 'src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java'

glass = glass_path.read_text()
pipeline = pipeline_path.read_text()

old_field = '''    private static int nativeBlurRadiusPx = -1;\n    private static boolean nativeBlurRadiusFailureLogged;\n    // Log the themed shadow suppression once per live background instance. HotSeats can\n    // reapply MiShadow on every translation frame, so per-call logging would be unusable.\n    private static View compatMiShadowLoggedFor;\n'''
new_field = '''    private static int nativeBlurRadiusPx = -1;\n    private static boolean nativeBlurRadiusFailureLogged;\n    // BlurBackground2 can issue the same hard-coded utility blur repeatedly during layout.\n    // Keep one concise diagnostic per themed background instance.\n    private static View compatBackgroundBlurLoggedFor;\n'''
assert old_field in glass
glass = glass.replace(old_field, new_field, 1)

old_shadow_method = '''    /**\n     * Device DEX/logs show the legacy themed background receives HotSeats' 143px MiShadow\n     * in addition to its own backdrop blur. Suppress that shadow only for the exact themed\n     * background instance currently bound to a live Prismal host. Default MiuiX material,\n     * stale themed instances and every other MiShadowUtils consumer must pass unchanged.\n     */\n    static boolean shouldSuppressCompatMiShadow(View dockBg) {\n        if (dockBg == null || dockBg != backgroundRef) return false;\n        if (!COMPAT_BACKGROUND_CLASS.equals(dockBg.getClass().getName())) return false;\n        ViewGroup parent = dockBg.getParent() instanceof ViewGroup\n                ? (ViewGroup) dockBg.getParent() : null;\n        DockLiquidGlassHostView host = hostRef;\n        if (parent == null || host == null || host.getParent() != parent) return false;\n        if (compatMiShadowLoggedFor != dockBg) {\n            compatMiShadowLoggedFor = dockBg;\n            MainHook.log(TAG + " compat BlurBackground2 MiShadow suppressed");\n        }\n        return true;\n    }\n\n'''
new_blur_method = '''    /**\n     * BlurBackground2.addBlur() hard-codes a 100-unit background blur before delegating to\n     * BlurUtilities.setBackgroundBlur(View,int,float[],int[][]). The full default->theme device\n     * trace shows this is the themed-only visual difference: default MiuiX uses the configured\n     * radius while both implementations intentionally keep the same HotSeats MiShadow. Clamp\n     * only positive themed utility radii; preserve vendor disable semantics and blend arrays.\n     * This deliberately does not require a live Prismal binding because the vendor can call the\n     * utility while constructing the replacement background, before hierarchy rebind completes.\n     */\n    static int clampCompatBackgroundBlurRadius(\n            View dockBg, int requestedRadius, LiquidDockConfig config) {\n        if (dockBg == null || config == null || requestedRadius <= 0) return requestedRadius;\n        if (!COMPAT_BACKGROUND_CLASS.equals(dockBg.getClass().getName())) return requestedRadius;\n        int targetRadius = Math.round(\n                config.glass.blur * dockBg.getResources().getDisplayMetrics().density);\n        if (requestedRadius != targetRadius && compatBackgroundBlurLoggedFor != dockBg) {\n            compatBackgroundBlurLoggedFor = dockBg;\n            MainHook.log(TAG + " compat BlurBackground2 background blur clamped "\n                    + requestedRadius + " -> " + targetRadius);\n        }\n        return targetRadius;\n    }\n\n'''
assert old_shadow_method in glass
glass = glass.replace(old_shadow_method, new_blur_method, 1)

glass = glass.replace('        compatMiShadowLoggedFor = null;\n',
                      '        compatBackgroundBlurLoggedFor = null;\n', 1)

glass = glass.replace(
'''            // BlurBackground2.addBlur() already enables the vendor pass-window blur and owns\n            // its backdrop/outline state. Keep that stack, but suppress HotSeats' separate\n            // 143px MiShadow at MiShadowUtils so it cannot add a second wide shadow blur.\n''',
'''            // BlurBackground2 owns its vendor backdrop/outline stack. Its separate utility\n            // background-blur radius is clamped at BlurUtilities; keep the normal HotSeats\n            // MiShadow because the working default MiuiX path uses the same shadow parameters.\n''', 1)

glass = glass.replace(
'    /** Both 307 implementations own native backdrop blur/outline; compat MiShadow is separate. */\n',
'    /** Both 307 implementations own the native backdrop/outline/shadow visual stack. */\n', 1)

glass = glass.replace(
'''            // Keep the vendor geometry/background stack alive underneath Prismal. Both\n            // supported 307 backgrounds own native blur; compat MiShadow is filtered earlier.\n''',
'''            // Keep the complete vendor geometry/background stack alive underneath Prismal.\n            // The themed hard-coded radius is clamped at BlurUtilities before it reaches View.\n''', 1)

old_install = '            installCompatMiShadowSuppression(classLoader);\n'
new_install = '            installCompatBackgroundBlurClamp(classLoader, config);\n'
assert old_install in pipeline
pipeline = pipeline.replace(old_install, new_install, 1)

start = pipeline.index('    /**\n     * HotSeats applies the same MiShadow utility')
end_marker = '    /** Native MiuiX implementation exposes explicit width/height/radius setters. */\n'
end = pipeline.index(end_marker, start)
new_method = '''    /**\n     * BlurBackground2.addBlur() delegates its hard-coded positive radius through this exact\n     * utility boundary before reflection reaches hidden View.setBackgroundBlur. Clamp only the\n     * themed HotSeats background argument; default MiuiX and every other BlurUtilities consumer\n     * pass through unchanged, as do radius<=0 disable calls and both vendor blend arrays.\n     */\n    private static void installCompatBackgroundBlurClamp(\n            ClassLoader classLoader, LiquidDockConfig config) {\n        try {\n            HookUtil.hookMethod(classLoader,\n                    "com.miui.home.launcher.common.BlurUtilities", "setBackgroundBlur",\n                    chain -> {\n                        Object[] args = chain.getArgs().toArray(new Object[0]);\n                        if (args.length >= 2 && args[0] instanceof View\n                                && args[1] instanceof Integer) {\n                            args[1] = MiuixGlassHook.clampCompatBackgroundBlurRadius(\n                                    (View) args[0], (Integer) args[1], config);\n                        }\n                        return chain.proceed(args);\n                    }, View.class, int.class, float[].class, int[][].class);\n            MainHook.log("[DC] MiuiX 307 compat background blur clamp installed");\n        } catch (Throwable error) {\n            MainHook.log("[DC] MiuiX 307 compat background blur clamp unavailable: " + error);\n        }\n    }\n\n'''
pipeline = pipeline[:start] + new_method + pipeline[end:]

pipeline = pipeline.replace(
'''        // Decompiled BlurBackground2.addBlur() is invoked by both attach and radius updates.\n        // Run our sync after those originals so MiuixGlassHook can clear the just-reapplied blur.\n''',
'''        // Decompiled BlurBackground2.addBlur() is invoked by both attach and radius updates.\n        // Its hard-coded utility radius is already clamped before the original reaches View;\n        // run geometry sync afterwards to keep the pass-window radius and Prismal shape aligned.\n''', 1)

# Guard against retaining the disproven workaround.
assert 'installCompatMiShadowSuppression' not in pipeline
assert 'shouldSuppressCompatMiShadow' not in pipeline
assert 'shouldSuppressCompatMiShadow' not in glass
assert 'compat BlurBackground2 MiShadow suppressed' not in glass
assert 'installCompatBackgroundBlurClamp(classLoader, config)' in pipeline
assert 'View.class, int.class, float[].class, int[][].class' in pipeline
assert 'clampCompatBackgroundBlurRadius' in glass
assert 'requestedRadius <= 0' in glass

glass_path.write_text(glass)
pipeline_path.write_text(pipeline)
