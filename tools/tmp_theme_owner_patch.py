from pathlib import Path

p = Path('src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java')
s = p.read_text()

def replace(old, new, count=1):
    global s
    found = s.count(old)
    if found != count:
        raise SystemExit(f'expected {count} matches, found {found}: {old[:80]!r}')
    s = s.replace(old, new, count)

replace(
    '    private static boolean installed;\n'
    '    private static View workspaceRef;\n'
    '    private static WeakReference<Object> hotSeatsRef = new WeakReference<>(null);\n',
    '    private static boolean installed;\n'
    '    private static View workspaceRef;\n'
    '    private static WeakReference<Object> launcherRef = new WeakReference<>(null);\n'
    '    private static WeakReference<Object> hotSeatsRef = new WeakReference<>(null);\n')

replace(
    '                            Object launcher = chain.getThisObject();\n'
    '                            Object hotSeats = HookUtil.getField(launcher, "mHotSeats");\n',
    '                            Object launcher = chain.getThisObject();\n'
    '                            launcherRef = new WeakReference<>(launcher);\n'
    '                            Object hotSeats = HookUtil.getField(launcher, "mHotSeats");\n')

replace(
    '        Object hotSeats = hotSeatsRef.get();\n'
    '        if (hotSeats == null) {\n'
    '            MainHook.log("[DC] MiuiX 307 hierarchy rebind deferred; HotSeats owner gone");\n'
    '            return false;\n'
    '        }\n',
    '        Object hotSeats = resolveCurrentHotSeats();\n'
    '        if (hotSeats == null) {\n'
    '            MainHook.log("[DC] MiuiX 307 hierarchy rebind deferred; HotSeats owner gone");\n'
    '            return false;\n'
    '        }\n',
    1)

replace(
    '        Object hotSeats = hotSeatsRef.get();\n'
    '        if (!(hotSeats instanceof View)) return;\n'
    '        View owner = (View) hotSeats;\n'
    '        View root = owner.getRootView();\n',
    '        Object hotSeats = resolveCurrentHotSeats();\n'
    '        View owner = workspaceRef != null && workspaceRef.isAttachedToWindow()\n'
    '                ? workspaceRef : hotSeats instanceof View ? (View) hotSeats : null;\n'
    '        if (owner == null) return;\n'
    '        View root = owner.getRootView();\n')

marker = '    private static View resolveBackground(Object hotSeats) {\n'
helper = '''    /** Re-read the current HotSeats from Launcher because theme changes may replace it. */
    private static Object resolveCurrentHotSeats() {
        Object launcher = launcherRef.get();
        if (launcher != null) {
            try {
                Object current = HookUtil.getField(launcher, "mHotSeats");
                if (current != null) {
                    hotSeatsRef = new WeakReference<>(current);
                    return current;
                }
            } catch (Throwable ignored) {}
        }
        return hotSeatsRef.get();
    }

'''
replace(marker, helper + marker)

p.write_text(s)
