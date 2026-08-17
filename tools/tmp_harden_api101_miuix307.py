from pathlib import Path
import subprocess
import textwrap

ROOT = Path('.')
PKG = ROOT / 'src/main/java/com/hellovoid/liquiddock'
TEST = ROOT / 'src/test/java/com/hellovoid/liquiddock'


def run(*args, check=True):
    print('+', ' '.join(args))
    return subprocess.run(args, check=check, text=True)


def replace_once(path: Path, old: str, new: str, label: str):
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly one match, found {count}')
    path.write_text(text.replace(old, new, 1))


def write_isolation_contract():
    (TEST / 'Miuix307IsolationContractTest.java').write_text(textwrap.dedent('''\
        package com.hellovoid.liquiddock;

        import static org.junit.Assert.*;
        import java.nio.file.Files;
        import java.nio.file.Path;
        import org.junit.Test;

        public class Miuix307IsolationContractTest {
            private static String src(String name) throws Exception {
                return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock", name));
            }

            @Test public void ordinaryDragApiKeepsLegacySemantics() throws Exception {
                String glass = src("DockLiquidGlassView.java");
                String ordinarySignature = "void setDockDragging(boolean dragging, String dragSurfaceLayerName)";
                int start = glass.indexOf(ordinarySignature);
                assertTrue(start >= 0);
                int overload = glass.indexOf("void setDockDragging(boolean dragging, String dragSurfaceLayerName,", start);
                assertTrue(overload > start);
                String ordinary = glass.substring(start, overload);
                assertFalse("ordinary path must not enter 307 Surface-aware overload",
                        ordinary.contains("setDockDragging(dragging, dragSurfaceLayerName, null)"));
                assertTrue(ordinary.contains("dockDragging = dragging"));
                assertTrue(ordinary.contains("dragLayerName = dragging ? dragSurfaceLayerName : null"));
                assertTrue(ordinary.contains("requestStateCapture(\"drag-start\")"));
            }

            @Test public void ordinaryContentBlurCleanupDoesNotClear307PassBlur() throws Exception {
                String bridge = src("MiBlurBridge.java");
                int start = bridge.indexOf("static void clearContentBlur(View view)");
                assertTrue(start >= 0);
                String method = bridge.substring(start);
                assertFalse("legacy cleanup must not mutate vendor pass-window blur",
                        method.contains("clearPassWindowBlur(view)"));
                assertTrue(method.contains("if (!LEGACY_AVAILABLE || view == null) return"));
            }
        }
    '''))


def expect_red():
    result = subprocess.run([
        './gradlew', 'testDebugUnitTest',
        '--tests', 'com.hellovoid.liquiddock.Miuix307IsolationContractTest',
        '--stacktrace'
    ], text=True)
    if result.returncode == 0:
        raise SystemExit('isolation contract unexpectedly passed before hardening')
    print('307 isolation RED confirmed')


def harden_drag_api():
    path = PKG / 'DockLiquidGlassView.java'
    old = '''    void setDockDragging(boolean dragging, String dragSurfaceLayerName) {\n        setDockDragging(dragging, dragSurfaceLayerName, null);\n    }\n\n'''
    new = '''    void setDockDragging(boolean dragging, String dragSurfaceLayerName) {\n        // Ordinary v1.3.0/API101 path: preserve the established live-drag behavior exactly.\n        // The 307 adapter deliberately calls the Surface-aware overload below instead.\n        dockDragging = dragging;\n        dragLayerName = dragging ? dragSurfaceLayerName : null;\n        if (dragging) {\n            resetCaptureCircuit("drag-start");\n            beginObservationBurst();\n            observationValid = false;\n            requestStateCapture("drag-start");\n        }\n    }\n\n'''
    replace_once(path, old, new, 'ordinary drag isolation')


def harden_blur_bridge():
    path = PKG / 'MiBlurBridge.java'
    replace_once(path,
        '    static volatile boolean liquidGlassActive;\n\n',
        '',
        'remove unused 307 bridge state')
    old = '''    /** Symmetric cleanup: clear both legacy self blur and MiuiX pass-window blur. */\n    static void clearContentBlur(View view) {\n        if (view == null) return;\n        if (LEGACY_AVAILABLE) {\n            try {\n                SET_MI_SELF_BLUR.invoke(view, 0, null);\n            } catch (Throwable ignored) {}\n            try {\n                SET_MI_SELF_BLUR_ENHANCE_FLAG.invoke(view, 0, SELF_BLUR_ENHANCE_FLAG);\n            } catch (Throwable ignored) {}\n            try {\n                SET_PASS_TEXTURE_SCALE.invoke(view, 1f);\n            } catch (Throwable ignored) {}\n        }\n        clearPassWindowBlur(view);\n    }\n'''
    new = '''    /** Legacy self/content cleanup. 307 pass-window cleanup is explicit at its adapter boundary. */\n    static void clearContentBlur(View view) {\n        if (!LEGACY_AVAILABLE || view == null) return;\n        try {\n            SET_MI_SELF_BLUR.invoke(view, 0, null);\n        } catch (Throwable ignored) {}\n        try {\n            SET_MI_SELF_BLUR_ENHANCE_FLAG.invoke(view, 0, SELF_BLUR_ENHANCE_FLAG);\n        } catch (Throwable ignored) {}\n        try {\n            SET_PASS_TEXTURE_SCALE.invoke(view, 1f);\n        } catch (Throwable ignored) {}\n    }\n'''
    replace_once(path, old, new, 'legacy blur cleanup isolation')


def verify_scope():
    changed = set(subprocess.check_output(['git', 'diff', '--name-only'], text=True).splitlines())
    expected = {
        'src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java',
        'src/main/java/com/hellovoid/liquiddock/MiBlurBridge.java',
        'src/test/java/com/hellovoid/liquiddock/Miuix307IsolationContractTest.java',
    }
    if changed != expected:
        raise SystemExit(f'unexpected hardening scope: {sorted(changed)}')


def main():
    write_isolation_contract()
    expect_red()
    harden_drag_api()
    harden_blur_bridge()
    verify_scope()

    run('./gradlew', 'testDebugUnitTest',
        '--tests', 'com.hellovoid.liquiddock.Miuix307IsolationContractTest',
        '--tests', 'com.hellovoid.liquiddock.Miuix307FeatureGateContractTest',
        '--tests', 'com.hellovoid.liquiddock.Miuix307MaterialOwnershipContractTest',
        '--tests', 'com.hellovoid.liquiddock.Miuix307CaptureCompatibilityContractTest',
        '--stacktrace')
    run('./gradlew', 'testDebugUnitTest', '--stacktrace')
    run('./gradlew', 'assembleDebug', '--stacktrace')

    run('git', 'add',
        'src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java',
        'src/main/java/com/hellovoid/liquiddock/MiBlurBridge.java',
        'src/test/java/com/hellovoid/liquiddock/Miuix307IsolationContractTest.java')
    run('git', 'commit', '-m', '[api101-307-hardened] refactor: isolate 307 compatibility behavior')
    run('git', 'push', 'origin', 'HEAD:integrate/api101-miuix307-impl')


if __name__ == '__main__':
    main()
