from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, found {count}\n--- OLD ---\n{old}")
    path.write_text(text.replace(old, new, 1))


root = Path("src/main/java/com/hellovoid/liquiddock")
glass = root / "MiuixGlassHook.java"
pipeline = root / "Miuix307MaterialPipeline.java"

replace_once(
    glass,
    "    private static int nativeBlurRadiusPx = -1;\n"
    "    private static boolean nativeBlurRadiusFailureLogged;\n",
    "    private static int nativeBlurRadiusPx = -1;\n"
    "    private static boolean nativeBlurRadiusFailureLogged;\n"
    "    // Log the themed shadow suppression once per live background instance. HotSeats can\n"
    "    // reapply MiShadow on every translation frame, so per-call logging would be unusable.\n"
    "    private static View compatMiShadowLoggedFor;\n",
)

replace_once(
    glass,
    "    static boolean isBoundTo(View dockBg) {\n"
    "        if (dockBg == null || dockBg != backgroundRef) return false;\n"
    "        ViewGroup parent = dockBg.getParent() instanceof ViewGroup\n"
    "                ? (ViewGroup) dockBg.getParent() : null;\n"
    "        DockLiquidGlassHostView host = hostRef;\n"
    "        return parent != null && host != null && host.getParent() == parent;\n"
    "    }\n",
    "    static boolean isBoundTo(View dockBg) {\n"
    "        if (dockBg == null || dockBg != backgroundRef) return false;\n"
    "        ViewGroup parent = dockBg.getParent() instanceof ViewGroup\n"
    "                ? (ViewGroup) dockBg.getParent() : null;\n"
    "        DockLiquidGlassHostView host = hostRef;\n"
    "        return parent != null && host != null && host.getParent() == parent;\n"
    "    }\n\n"
    "    /**\n"
    "     * Device DEX/logs show the legacy themed background receives HotSeats' 143px MiShadow\n"
    "     * in addition to its own backdrop blur. Suppress that shadow only for the exact themed\n"
    "     * background instance currently bound to a live Prismal host. Default MiuiX material,\n"
    "     * stale themed instances and every other MiShadowUtils consumer must pass unchanged.\n"
    "     */\n"
    "    static boolean shouldSuppressCompatMiShadow(View dockBg) {\n"
    "        if (dockBg == null || dockBg != backgroundRef) return false;\n"
    "        if (!COMPAT_BACKGROUND_CLASS.equals(dockBg.getClass().getName())) return false;\n"
    "        ViewGroup parent = dockBg.getParent() instanceof ViewGroup\n"
    "                ? (ViewGroup) dockBg.getParent() : null;\n"
    "        DockLiquidGlassHostView host = hostRef;\n"
    "        if (parent == null || host == null || host.getParent() != parent) return false;\n"
    "        if (compatMiShadowLoggedFor != dockBg) {\n"
    "            compatMiShadowLoggedFor = dockBg;\n"
    "            MainHook.log(TAG + \" compat BlurBackground2 MiShadow suppressed\");\n"
    "        }\n"
    "        return true;\n"
    "    }\n",
)

replace_once(
    glass,
    "        hostRef = null;\n"
    "        glassRef = null;\n"
    "        backgroundRef = null;\n"
    "        nativeBlurRadiusPx = -1;\n",
    "        hostRef = null;\n"
    "        glassRef = null;\n"
    "        backgroundRef = null;\n"
    "        compatMiShadowLoggedFor = null;\n"
    "        nativeBlurRadiusPx = -1;\n",
)

replace_once(
    glass,
    "        } else if (nativeVisualOwner) {\n"
    "            // BlurBackground2.addBlur() already enables the vendor pass-window blur and also\n"
    "            // owns MiShadow / outline state. Do not clear or re-enable that stack here; the\n"
    "            // pre-draw preserver below only clamps its existing blur radius.\n"
    "            MainHook.log(TAG + \" compat BlurBackground2 keeps vendor visual owner radius=\"\n"
    "                    + blurPx);\n"
    "        }\n",
    "        } else if (nativeVisualOwner) {\n"
    "            // BlurBackground2.addBlur() already enables the vendor pass-window blur and owns\n"
    "            // its backdrop/outline state. Keep that stack, but suppress HotSeats' separate\n"
    "            // 143px MiShadow at MiShadowUtils so it cannot add a second wide shadow blur.\n"
    "            MainHook.log(TAG + \" compat BlurBackground2 keeps vendor blur/outline radius=\"\n"
    "                    + blurPx);\n"
    "        }\n",
)

replace_once(
    glass,
    "    /** Both 307 implementations own the native blur/shadow/outline visual stack. */\n",
    "    /** Both 307 implementations own native backdrop blur/outline; compat MiShadow is separate. */\n",
)

replace_once(
    glass,
    "            // Keep the vendor geometry/background/shadow stack alive underneath Prismal.\n"
    "            // Both supported 307 backgrounds own native blur; Prismal only refracts/highlights.\n",
    "            // Keep the vendor geometry/background stack alive underneath Prismal. Both\n"
    "            // supported 307 backgrounds own native blur; compat MiShadow is filtered earlier.\n",
)

replace_once(
    pipeline,
    "            Miuix307DragCaptureHook.install(classLoader);\n"
    "            installHomeGesturePrearm(classLoader);\n",
    "            Miuix307DragCaptureHook.install(classLoader);\n"
    "            installHomeGesturePrearm(classLoader);\n"
    "            installCompatMiShadowSuppression(classLoader);\n",
)

replace_once(
    pipeline,
    "    /** Native MiuiX implementation exposes explicit width/height/radius setters. */\n",
    "    /**\n"
    "     * HotSeats applies the same MiShadow utility to several UI surfaces. Intercept the utility\n"
    "     * once, but skip the original call only for the exact live compat Dock background selected\n"
    "     * by MiuixGlassHook. This leaves default MiuiX, Recents and shortcut-menu shadows intact.\n"
    "     */\n"
    "    private static void installCompatMiShadowSuppression(ClassLoader classLoader) {\n"
    "        try {\n"
    "            Class<?> shadowUtils = Class.forName(\n"
    "                    \"com.miui.home.launcher.common.MiShadowUtils\", false, classLoader);\n"
    "            int hooked = 0;\n"
    "            for (Method method : shadowUtils.getDeclaredMethods()) {\n"
    "                Class<?>[] params = method.getParameterTypes();\n"
    "                if (!\"applyViewShadow\".equals(method.getName())\n"
    "                        || !Modifier.isStatic(method.getModifiers())\n"
    "                        || method.getReturnType() != void.class\n"
    "                        || params.length == 0\n"
    "                        || !View.class.isAssignableFrom(params[0])) {\n"
    "                    continue;\n"
    "                }\n"
    "                HookUtil.hook(method, chain -> {\n"
    "                    Object[] args = chain.getArgs().toArray(new Object[0]);\n"
    "                    if (args.length > 0 && args[0] instanceof View\n"
    "                            && MiuixGlassHook.shouldSuppressCompatMiShadow((View) args[0])) {\n"
    "                        return null;\n"
    "                    }\n"
    "                    return chain.proceed(args);\n"
    "                });\n"
    "                hooked++;\n"
    "            }\n"
    "            if (hooked == 0) {\n"
    "                MainHook.log(\"[DC] MiuiX 307 compat MiShadow suppression unavailable: no overload\");\n"
    "            } else {\n"
    "                MainHook.log(\"[DC] MiuiX 307 compat MiShadow suppression installed count=\"\n"
    "                        + hooked);\n"
    "            }\n"
    "        } catch (Throwable error) {\n"
    "            MainHook.log(\"[DC] MiuiX 307 compat MiShadow suppression unavailable: \" + error);\n"
    "        }\n"
    "    }\n\n"
    "    /** Native MiuiX implementation exposes explicit width/height/radius setters. */\n",
)

print("themed MiShadow suppression patch applied")
