from pathlib import Path
import re

p = Path('src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java')
s = p.read_text()

for line in [
    'import android.graphics.Color;\n',
    'import android.graphics.HardwareRenderer;\n',
    'import android.graphics.PorterDuff;\n',
    'import android.graphics.RecordingCanvas;\n',
    'import android.graphics.RenderNode;\n',
    '    private static final int SETTLE_REPLAY_FRAMES = 2;\n',
    '    private final AtomicBoolean launcherFrameAvailable = new AtomicBoolean(false);\n',
    '    private final float[] launcherTextureMatrix = new float[16];\n',
    '    private volatile SurfaceTexture launcherSurfaceTexture;\n',
    '    private volatile Surface launcherProducerSurface;\n',
    '    private volatile HardwareRenderer launcherHardwareRenderer;\n',
    '    private volatile RenderNode launcherRenderNode;\n',
    '    private volatile boolean launcherReplayPosted;\n',
    '    private volatile boolean launcherReplayPending;\n',
    '    private volatile int settleReplayFrames;\n',
    '    private volatile LauncherGlassGpuAtlas.Layout submittedLauncherLayout;\n',
    '    private volatile LauncherGlassGpuAtlas.Layout consumedLauncherLayout;\n',
    '    private volatile boolean hasLauncherFrame;\n',
    '    private int launcherOverlayProgram;\n',
    '    private int launcherOesTexture;\n',
]:
    s = s.replace(line, '')

s = s.replace(
    '        // Detach/reparent changes the Launcher scene even when the remaining node geometry is\n'
    '        // unchanged. Always capture the post-lifecycle scene instead of exposing PassBlur base.\n'
    '        requestLifecycleRefresh();\n',
    '        requestLifecycleRefresh();\n')

s = re.sub(
    r'    void requestLifecycleRefresh\(\) \{\n.*?\n    \}\n\n    void attachOutput',
    '    void requestLifecycleRefresh() {\n        if (shuttingDown) return;\n        requestFrame(false);\n    }\n\n    void attachOutput',
    s,
    flags=re.S,
)

s = s.replace(
    '        ViewTreeObserver.OnPreDrawListener listener = () -> {\n'
    '            syncSceneOnUiThread();\n'
    '            requestSettleReplayOnPreDraw();\n'
    '            return true;\n'
    '        };\n',
    '        ViewTreeObserver.OnPreDrawListener listener = () -> {\n'
    '            syncSceneOnUiThread();\n'
    '            return true;\n'
    '        };\n')

s = re.sub(
    r'    private void requestSettleReplayOnPreDraw\(\) \{.*?\n    private void syncSceneOnUiThread\(\) \{',
    '    private void syncSceneOnUiThread() {',
    s,
    flags=re.S,
)

s = s.replace(
    '        if (changed) {\n'
    '            armSettleReplay();\n'
    '            requestLauncherGpuReplay();\n'
    '            requestFrame(true);\n'
    '        }\n',
    '        if (changed) {\n            requestFrame(false);\n        }\n')

s = re.sub(
    r'            SurfaceTexture launcherInput = launcherSurfaceTexture;\n            if \(launcherInput != null && launcherFrameAvailable.getAndSet\(false\)\) \{.*?\n            \}\n',
    '', s, flags=re.S)

s = re.sub(
    r'        if \(launcherOverlayProgram == 0\) \{\n            launcherOverlayProgram = createProgram\(\n                    Miuix307PassBlurShaders.QUAD_VERTEX,\n                    Miuix307PassBlurShaders.OES_ALPHA_OVER_FRAGMENT\);\n        \}\n',
    '', s)

s = re.sub(
    r'        if \(launcherOesTexture == 0 \|\| launcherSurfaceTexture == null\n                \|\| launcherProducerSurface == null\) \{\n            createLauncherSceneProducer\(\);\n        \}\n',
    '', s)

s = re.sub(
    r'    private void createLauncherSceneProducer\(\) \{.*?\n    private void bindProducerWhenReady\(int attempt\) \{',
    '    private void bindProducerWhenReady(int attempt) {',
    s,
    flags=re.S,
)

s = s.replace(
    '        LauncherGlassGpuAtlas.Layout layout = hasLauncherFrame\n                ? consumedLauncherLayout : atlasLayout;\n',
    '        LauncherGlassGpuAtlas.Layout layout = atlasLayout;\n')

s = re.sub(
    r'        if \(hasLauncherFrame && consumedLauncherLayout != null\n                && consumedLauncherLayout.sameAs\(layout\)\) \{\n            renderLauncherOverlay\(layout.width, layout.height\);\n        \}\n',
    '', s)

s = re.sub(
    r'    private void renderLauncherOverlay\(int width, int height\) \{.*?\n    private void present\(',
    '    private void present(',
    s,
    flags=re.S,
)

s = re.sub(
    r'        HardwareRenderer sceneRenderer = launcherHardwareRenderer;\n        launcherHardwareRenderer = null;\n        launcherRenderNode = null;\n        if \(sceneRenderer != null\) \{\n            try \{ sceneRenderer.destroy\(\); \} catch \(Throwable ignored\) \{\}\n        \}\n',
    '', s)

s = s.replace('        if (launcherOverlayProgram != 0) GLES20.glDeleteProgram(launcherOverlayProgram);\n', '')
s = s.replace('        normalizeProgram = launcherOverlayProgram = compositeProgram = 0;\n', '        normalizeProgram = compositeProgram = 0;\n')
s = s.replace('        if (launcherOesTexture != 0) GLES20.glDeleteTextures(1, new int[]{launcherOesTexture}, 0);\n', '')
s = s.replace('        launcherOesTexture = 0;\n', '')

s = re.sub(
    r'        Surface launcherProducer = launcherProducerSurface;\n        launcherProducerSurface = null;\n        if \(launcherProducer != null\) launcherProducer.release\(\);\n',
    '', s)

s = re.sub(
    r'        SurfaceTexture launcherInput = launcherSurfaceTexture;\n        launcherSurfaceTexture = null;\n        if \(launcherInput != null\) \{\n            try \{ launcherInput.setOnFrameAvailableListener\(null\); \} catch \(Throwable ignored\) \{\}\n            launcherInput.release\(\);\n        \}\n',
    '', s)

s = s.replace(
    'MainHook.log(TAG + " " + debugLabel() + " created");',
    'MainHook.log(TAG + " " + debugLabel() + " created source=PassBlur-wallpaper-only");')

p.write_text(s)

# Remove contracts belonging to superseded scene-replay/native-probe experiments,
# regardless of their package directory in the current merge base.
obsolete = {
    'LauncherGlassInRootBackdropContractTest.java',
    'LauncherGlassGpuOnlyContractTest.java',
    'LauncherGlassLifecycleRecoveryContractTest.java',
    'FolderWallpaperOnlySourceContractTest.java',
}
for f in Path('src/test').rglob('*.java'):
    if f.name in obsolete:
        print(f'removing obsolete contract: {f}')
        f.unlink()
