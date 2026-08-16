from pathlib import Path

SOURCE = Path("src/main/java/com/hellovoid/liquiddock/MainHook.java")

OLD = '        hookOverviewStateEvent(cl, "ExitOverviewStateEvent", false);\n'
NEW = (
    '        // ExitOverviewStateEvent is emitted when the exit transition starts; the visual\n'
    '        // lifetime ends only when RecentsContainer.setIsExitRecentsAnimating(false).\n'
)

text = SOURCE.read_text()
old_count = text.count(OLD)
new_count = text.count(NEW)
if old_count == 1 and new_count == 0:
    SOURCE.write_text(text.replace(OLD, NEW, 1))
    print("patched MainHook Recents exit boundary")
elif old_count == 0 and new_count == 1:
    print("MainHook Recents exit boundary already patched")
else:
    raise SystemExit(
        f"unexpected MainHook state: legacy={old_count} replacement={new_count}"
    )
