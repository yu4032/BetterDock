from pathlib import Path

SOURCE = Path("src/main/java/com/hellovoid/liquiddock/MainHook.java")

OLD = '        hookOverviewStateEvent(cl, "ExitOverviewStateEvent", false);\n'
NEW = (
    '        // ExitOverviewStateEvent is emitted when the exit transition starts; the visual\n'
    '        // lifetime ends only when RecentsContainer.setIsExitRecentsAnimating(false).\n'
)

text = SOURCE.read_text()
count = text.count(OLD)
if count != 1:
    raise SystemExit(f"expected exactly one legacy ExitOverview hook, got {count}")
SOURCE.write_text(text.replace(OLD, NEW, 1))
print("patched MainHook Recents exit boundary")
