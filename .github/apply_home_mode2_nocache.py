from pathlib import Path
import sys

SOURCE = Path("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java")
TEST = Path("src/test/java/com/hellovoid/liquiddock/HomeMode2NoCacheDiagnosticContractTest.java")

TEST_SOURCE = r'''package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class HomeMode2NoCacheDiagnosticContractTest {
    private static String source() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java"));
    }

    @Test public void diagnosticBypassesWallpaperCacheServe() throws Exception {
        String s = source();
        assertTrue(s.contains("HOME_MODE2_NOCACHE_DIAGNOSTIC"));
        assertTrue(s.contains("if (!HOME_MODE2_NOCACHE_DIAGNOSTIC"));
    }

    @Test public void diagnosticDoesNotSwitchHomeToFullDisplay() throws Exception {
        String policy = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/CaptureSourcePolicy.java"));
        assertTrue(policy.contains("if (scene == CaptureScene.HOME) return Source.WALLPAPER;"));
        assertFalse(policy.contains("if (scene == CaptureScene.HOME) return Source.FULL_DISPLAY;"));
    }
}
'''


def replace_once(text: str, old: str, new: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one match, got {count}: {old[:120]!r}")
    return text.replace(old, new, 1)


def prepare_test() -> None:
    TEST.parent.mkdir(parents=True, exist_ok=True)
    TEST.write_text(TEST_SOURCE)
    print(f"wrote RED diagnostic contract: {TEST}")


def apply_patch() -> None:
    text = SOURCE.read_text()
    text = replace_once(
        text,
        "    private static final int CAPTURE_TIMEOUT_BREAKER_LIMIT = 4;\n",
        "    private static final int CAPTURE_TIMEOUT_BREAKER_LIMIT = 4;\n"
        "    // Diagnostic only: force HOME wallpaper requests to reach SurfaceFlinger.\n"
        "    private static final boolean HOME_MODE2_NOCACHE_DIAGNOSTIC = true;\n",
    )
    text = replace_once(
        text,
        "                    if (wallpaperMode\n"
        "                            && !(workstationMode && workstationCaptureBurst.isActive())\n"
        "                            && tryServeWallpaperFromCache(\n"
        "                            req, requestScene, requestSceneRevision, attempt)) {\n"
        "                        return;\n"
        "                    }\n",
        "                    if (!HOME_MODE2_NOCACHE_DIAGNOSTIC\n"
        "                            && wallpaperMode\n"
        "                            && !(workstationMode && workstationCaptureBurst.isActive())\n"
        "                            && tryServeWallpaperFromCache(\n"
        "                            req, requestScene, requestSceneRevision, attempt)) {\n"
        "                        return;\n"
        "                    }\n",
    )
    SOURCE.write_text(text)
    print("applied B-only HOME mode2 no-cache diagnostic")


def main() -> None:
    if len(sys.argv) != 2 or sys.argv[1] not in {"prepare-test", "apply"}:
        raise SystemExit("usage: apply_home_mode2_nocache.py {prepare-test|apply}")
    if sys.argv[1] == "prepare-test":
        prepare_test()
    else:
        apply_patch()


if __name__ == "__main__":
    main()
