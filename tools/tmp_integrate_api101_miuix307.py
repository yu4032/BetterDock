from pathlib import Path
import subprocess
import textwrap

ROOT = Path('.')
PKG = ROOT / 'src/main/java/com/hellovoid/liquiddock'
TEST = ROOT / 'src/test/java/com/hellovoid/liquiddock'
EXPERIMENT = 'origin/fix/miuix307-drag-freeform-followup'


def run(*args, check=True):
    print('+', ' '.join(args))
    return subprocess.run(args, check=check, text=True)


def replace_once(path: Path, old: str, new: str):
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{path}: expected one match, found {count}: {old[:80]!r}')
    path.write_text(text.replace(old, new, 1))


def write_contract_tests():
    TEST.mkdir(parents=True, exist_ok=True)
    (TEST / 'Miuix307MaterialOwnershipContractTest.java').write_text(textwrap.dedent('''\
        package com.hellovoid.liquiddock;

        import static org.junit.Assert.*;
        import java.nio.file.Files;
        import java.nio.file.Path;
        import org.junit.Test;

        public class Miuix307MaterialOwnershipContractTest {
            private static String src(String name) throws Exception {
                return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock", name));
            }

            @Test public void nativeMaterialOwnsShellWhilePrismalOwnsBody() throws Exception {
                Path hookPath = Path.of("src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java");
                assertTrue(Files.exists(hookPath));
                String hook = Files.readString(hookPath);
                assertTrue(hook.contains("hasReadyNativeGeometry"));
                assertTrue(hook.contains("dockBg.isAttachedToWindow()"));
                assertTrue(hook.contains("dockBg.getWidth() <= 0 || dockBg.getHeight() <= 0"));
                assertTrue(hook.contains("radius > 0.5f"));
                assertTrue(hook.contains("if (!hasReadyNativeGeometry(dockBg)) return false"));
                assertTrue(hook.contains("materialHost.addView(host"));
                assertTrue(hook.contains("glass.setPreserveGeometrySourceVisuals(true)"));
                assertTrue(hook.contains("suppressVendorMaterialBody"));
                assertTrue(hook.contains("host.setGeometry(nativeRadius, false"));
                assertTrue(hook.contains("configureReplacingForeground"));
                assertFalse(hook.contains("dockBg.setAlpha(0f)"));
            }

            @Test public void nativeOpticsAndDecorativeStrokeAreSeparated() throws Exception {
                String hook = src("MiuixGlassHook.java");
                String host = src("DockLiquidGlassHostView.java");
                assertTrue(hook.contains("readNativeOpticsRadius"));
                assertTrue(hook.contains("mCornerRadius"));
                assertTrue(host.contains("reloadOpticsPreservingGeometry"));
                assertTrue(src("DockStrokeRenderer.java").contains("configureReplacingForeground"));
            }
        }
    '''))

    (TEST / 'Miuix307CaptureCompatibilityContractTest.java').write_text(textwrap.dedent('''\
        package com.hellovoid.liquiddock;

        import static org.junit.Assert.*;
        import java.nio.file.Files;
        import java.nio.file.Path;
        import org.junit.Test;

        public class Miuix307CaptureCompatibilityContractTest {
            private static String src(String name) throws Exception {
                return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock", name));
            }

            @Test public void homePrearmUsesNative307Boundaries() throws Exception {
                String pipeline = src("Miuix307MaterialPipeline.java");
                assertTrue(pipeline.contains("com.miui.home.launcher.dock.v3.GestureToHome"));
                assertTrue(pipeline.contains("com.miui.home.recents.util.StateNotifyUtils"));
                assertTrue(pipeline.contains("MiuixGlassHook.onHomeTransitionStart()"));
                assertFalse(pipeline.contains("hasWindowFocus()"));
            }

            @Test public void dragAdapterUsesSurfaceWhenAvailableAndFreezesOtherwise() throws Exception {
                String drag = src("Miuix307DragCaptureHook.java");
                String glass = src("DockLiquidGlassView.java");
                assertTrue(drag.contains("startDragInDockForSystem"));
                assertTrue(drag.contains("setSystemDockDragActive(true)"));
                assertTrue(drag.contains("mDragViews"));
                assertTrue(drag.contains("views.getClass().isArray()"));
                assertTrue(drag.contains("SurfaceControl"));
                assertTrue(glass.contains("setSystemDockDragActive"));
                assertTrue(glass.contains("setDockDragging"));
            }
        }
    '''))


def expect_deep_red():
    result = subprocess.run([
        './gradlew', 'testDebugUnitTest',
        '--tests', 'com.hellovoid.liquiddock.Miuix307MaterialOwnershipContractTest',
        '--tests', 'com.hellovoid.liquiddock.Miuix307CaptureCompatibilityContractTest',
        '--stacktrace'
    ], text=True)
    if result.returncode == 0:
        raise SystemExit('deep 307 contracts unexpectedly passed before production implementation')
    print('Deep 307 RED confirmed')


def copy_validated_components():
    paths = [
        'src/main/java/com/hellovoid/liquiddock/DockLiquidGlassHostView.java',
        'src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java',
        'src/main/java/com/hellovoid/liquiddock/DockStrokeRenderer.java',
        'src/main/java/com/hellovoid/liquiddock/MiBlurBridge.java',
        'src/main/java/com/hellovoid/liquiddock/Miuix307DragCaptureHook.java',
        'src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java',
        'src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java',
    ]
    run('git', 'checkout', EXPERIMENT, '--', *paths)


def patch_feature_gate():
    schema = PKG / 'config/ConfigSchema.java'
    replace_once(schema,
        '        public static final ConfigKey<String> BLUR_MODE = string(\n'
        '                "liquid_blur_mode", "shader", "shader", "shader",\n'
        '                ConfigKey.ExportMode.ALWAYS);\n',
        '        public static final ConfigKey<String> BLUR_MODE = string(\n'
        '                "liquid_blur_mode", "shader", "shader", "shader",\n'
        '                ConfigKey.ExportMode.ALWAYS);\n'
        '        public static final ConfigKey<Boolean> MIUIX_307_PIPELINE = bool(\n'
        '                "liquid_miuix_307_pipeline", false, false, false, ConfigKey.ExportMode.ALWAYS);\n')

    config = PKG / 'LiquidDockConfig.java'
    replace_once(config,
        '        final boolean enabled, dimensionsDp, dynamicAppCapture, fullscreenCapture;\n',
        '        final boolean enabled, dimensionsDp, dynamicAppCapture, fullscreenCapture, miuix307Pipeline;\n')
    replace_once(config,
        '            dimensionsDp = c.b(ConfigSchema.Glass.DIMENSIONS_DP.name(),\n'
        '                    ConfigSchema.Glass.DIMENSIONS_DP.runtimeFallback());\n',
        '            dimensionsDp = c.b(ConfigSchema.Glass.DIMENSIONS_DP.name(),\n'
        '                    ConfigSchema.Glass.DIMENSIONS_DP.runtimeFallback());\n'
        '            miuix307Pipeline = c.b(ConfigSchema.Glass.MIUIX_307_PIPELINE.name(),\n'
        '                    ConfigSchema.Glass.MIUIX_307_PIPELINE.runtimeFallback());\n')

    compose = ROOT / 'src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt'
    replace_once(compose,
        '        BooleanSetting(prefs, ConfigSchema.Glass.ENABLED, stringResource(R.string.liquid_enable),\n'
        '            stringResource(R.string.liquid_enable_summary), masterEnabled) { liquidGlass = it }\n'
        '        StringDropdown(\n',
        '        BooleanSetting(prefs, ConfigSchema.Glass.ENABLED, stringResource(R.string.liquid_enable),\n'
        '            stringResource(R.string.liquid_enable_summary), masterEnabled) { liquidGlass = it }\n'
        '        BooleanSetting(prefs, ConfigSchema.Glass.MIUIX_307_PIPELINE,\n'
        '            "HyperOS 3.0.307+ 高级材质兼容",\n'
        '            "仅在 HyperOS 3.0.307+ 开启系统高级材质时手动启用；默认关闭，重启桌面生效",\n'
        '            masterEnabled && liquidGlass)\n'
        '        StringDropdown(\n')

    main = PKG / 'MainHook.java'
    replace_once(main,
        '        boolean dockCustomization = config.dock.enabled;\n'
        '        boolean liquidGlass = config.glass.enabled;\n'
        '        if (!dockCustomization && !liquidGlass) {\n',
        '        boolean dockCustomization = config.dock.enabled;\n'
        '        boolean liquidGlass = config.glass.enabled;\n'
        '        if (liquidGlass && config.glass.miuix307Pipeline) {\n'
        '            if (Miuix307Compatibility.install(classLoader, config)) {\n'
        '                log("[DC] HyperOS 3.0.307+ compatibility active; legacy liquid capture bypassed");\n'
        '                return;\n'
        '            }\n'
        '            log("[DC] HyperOS 3.0.307+ compatibility unavailable; falling back to ordinary pipeline");\n'
        '        }\n'
        '        if (!dockCustomization && !liquidGlass) {\n')

    (PKG / 'Miuix307Compatibility.java').write_text(textwrap.dedent('''\
        package com.hellovoid.liquiddock;

        /**
         * Single feature boundary for HyperOS 3.0.307+ compatibility.
         * MainHook decides whether the user-enabled feature is active; implementation details
         * stay inside the dedicated material/capture adapters.
         */
        final class Miuix307Compatibility {
            private Miuix307Compatibility() {}

            static boolean install(ClassLoader classLoader, LiquidDockConfig config) {
                return Miuix307MaterialPipeline.install(classLoader, config);
            }
        }
    '''))


def verify_no_unscoped_ports():
    forbidden = [
        'src/main/java/com/hellovoid/liquiddock/FreeformCaptureLeashHook.java',
        'src/main/java/com/hellovoid/liquiddock/CaptureExclusionNames.java',
        'src/main/java/com/hellovoid/liquiddock/RecentsHapticHook.java',
    ]
    out = subprocess.check_output(['git', 'diff', '--name-only', 'HEAD'], text=True).splitlines()
    for path in forbidden:
        if path in out:
            raise SystemExit(f'unscoped global port detected: {path}')


def main():
    run('git', 'fetch', 'origin', 'fix/miuix307-drag-freeform-followup:refs/remotes/origin/fix/miuix307-drag-freeform-followup')
    write_contract_tests()
    expect_deep_red()
    copy_validated_components()
    patch_feature_gate()
    verify_no_unscoped_ports()

    run('./gradlew', 'testDebugUnitTest',
        '--tests', 'com.hellovoid.liquiddock.Miuix307FeatureGateContractTest',
        '--tests', 'com.hellovoid.liquiddock.Miuix307MaterialOwnershipContractTest',
        '--tests', 'com.hellovoid.liquiddock.Miuix307CaptureCompatibilityContractTest',
        '--stacktrace')
    run('./gradlew', 'testDebugUnitTest', '--stacktrace')
    run('./gradlew', 'assembleDebug', '--stacktrace')

    run('git', 'add',
        'src/main/java', 'src/main/kotlin', 'src/test/java')
    run('git', 'commit', '-m', '[api101-307-applied] feat: integrate HyperOS 3.0.307 compatibility')
    run('git', 'push', 'origin', 'HEAD:integrate/api101-miuix307-impl')


if __name__ == '__main__':
    main()
