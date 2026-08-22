from pathlib import Path

p = Path('src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java')
s = p.read_text()

old_const = '    private static final String ITEM_ICON = "com.miui.home.launcher.ItemIcon";\n'
new_const = (
    '    private static final String ITEM_ICON = "com.miui.home.launcher.ItemIcon";\n'
    '    private static final int MAX_STARTUP_RECOVERY_FRAMES = 24;\n'
)
if old_const not in s:
    raise SystemExit('ITEM_ICON constant anchor not found')
s = s.replace(old_const, new_const, 1)

old_maps = (
    '    private static final Map<ViewGroup, View.OnAttachStateChangeListener> FOLDER_ATTACH_LISTENERS =\n'
    '            Collections.synchronizedMap(new WeakHashMap<>());\n'
)
new_maps = old_maps + (
    '    private static final Map<ViewGroup, Boolean> FOLDER_RECOVERY_PENDING =\n'
    '            Collections.synchronizedMap(new WeakHashMap<>());\n'
)
if old_maps not in s:
    raise SystemExit('folder listener map anchor not found')
s = s.replace(old_maps, new_maps, 1)

old_attach = '''    private static void attachFromFolderIcon(ViewGroup icon, LiquidDockConfig.Glass glassConfig) {
        observeFolderIconAttach(icon, glassConfig);
        try {
            Object value = HookUtil.getField(icon, "mIconImageView");
            if (value instanceof View) attachMaterial((View) value, glassConfig);
        } catch (Throwable error) {
            MainHook.log(TAG + " material resolve failed: " + error);
        }
    }
'''
new_attach = '''    private static void attachFromFolderIcon(ViewGroup icon, LiquidDockConfig.Glass glassConfig) {
        observeFolderIconAttach(icon, glassConfig);
        try {
            Object value = HookUtil.getField(icon, "mIconImageView");
            if (value instanceof View) {
                LauncherGlassSinkView sink = attachMaterial((View) value, glassConfig);
                // Launcher restart can call setIconImageView after FolderIcon is attached but
                // before its real ViewRoot/Surface is stable. Adding an attach listener at that
                // point does not replay onViewAttachedToWindow, so recover on later UI frames.
                if (sink == null && icon.isAttachedToWindow()) {
                    scheduleFolderRecovery(icon, glassConfig, 0);
                }
            }
        } catch (Throwable error) {
            MainHook.log(TAG + " material resolve failed: " + error);
        }
    }
'''
if old_attach not in s:
    raise SystemExit('attachFromFolderIcon anchor not found')
s = s.replace(old_attach, new_attach, 1)

anchor = '''

    private static void observeFolderIconAttach(
            ViewGroup icon, LiquidDockConfig.Glass glassConfig) {
'''
helper = '''

    private static void scheduleFolderRecovery(
            ViewGroup icon, LiquidDockConfig.Glass glassConfig, int attempt) {
        if (icon == null) return;
        if (attempt == 0) {
            synchronized (FOLDER_RECOVERY_PENDING) {
                if (FOLDER_RECOVERY_PENDING.containsKey(icon)) return;
                FOLDER_RECOVERY_PENDING.put(icon, Boolean.TRUE);
            }
        }
        if (attempt >= MAX_STARTUP_RECOVERY_FRAMES) {
            FOLDER_RECOVERY_PENDING.remove(icon);
            MainHook.log(TAG + " startup recovery exhausted for "
                    + icon.getClass().getSimpleName());
            return;
        }
        WeakReference<ViewGroup> iconRef = new WeakReference<>(icon);
        icon.postOnAnimation(() -> {
            ViewGroup current = iconRef.get();
            if (current == null || !current.isAttachedToWindow()) {
                if (current != null) FOLDER_RECOVERY_PENDING.remove(current);
                return;
            }
            LauncherGlassSinkView sink = null;
            try {
                Object value = HookUtil.getField(current, "mIconImageView");
                if (value instanceof View) {
                    sink = attachMaterial((View) value, glassConfig);
                }
            } catch (Throwable error) {
                MainHook.log(TAG + " startup material recovery failed: " + error);
            }
            if (sink == null && attempt < MAX_STARTUP_RECOVERY_FRAMES) {
                scheduleFolderRecovery(current, glassConfig, attempt + 1);
            } else {
                FOLDER_RECOVERY_PENDING.remove(current);
            }
        });
    }
'''
if anchor not in s:
    raise SystemExit('observeFolderIconAttach anchor not found')
s = s.replace(anchor, helper + anchor, 1)

p.write_text(s)
