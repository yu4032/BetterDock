from pathlib import Path

root = Path('.')
glass_path = root / 'src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java'
pipeline_path = root / 'src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java'

glass = glass_path.read_text()
pipeline = pipeline_path.read_text()


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly one match, got {count}')
    return text.replace(old, new, 1)

# ---- MiuixGlassHook: Prismal owns all actual blur; vendor backgrounds are geometry only. ----
glass = replace_once(glass,
''' * The vendor background remains intact and owns the realtime backdrop blur/gradient. This
 * class overlays LiquidDock's existing Prismal glass stack above it. The generic Launcher
''',
''' * The vendor background remains only as a geometry source. Its compositor/pass-window blur is
 * disabled so LiquidDock's existing Prismal glass stack owns the actual blur and optical pass.
 * The generic Launcher
''', 'class ownership comment')

glass = replace_once(glass,
'''    private static ViewTreeObserver nativeBackgroundObserver;
    private static ViewTreeObserver.OnPreDrawListener nativeBackgroundPreserver;
    private static int nativeBlurRadiusPx = -1;
    private static boolean nativeBlurRadiusFailureLogged;
    // BlurBackground2 can issue the same hard-coded utility blur repeatedly during layout.
    // Keep one concise diagnostic per themed background instance.
    private static View compatBackgroundBlurLoggedFor;
''',
'''    private static ViewTreeObserver vendorBlurObserver;
    private static ViewTreeObserver.OnPreDrawListener vendorBlurSuppressor;
    private static View vendorGpuBlurLoggedFor;
    // BlurBackground2 can issue the same hard-coded utility blur repeatedly during layout.
    // Keep one concise diagnostic per themed background instance.
    private static View compatBackgroundBlurLoggedFor;
''', 'fields')

glass = replace_once(glass,
'''    /**
     * BlurBackground2.addBlur() hard-codes a 100-unit background blur before delegating to
     * BlurUtilities.setBackgroundBlur(View,int,float[],int[][]). The full default->theme device
     * trace shows this is the themed-only visual difference: default MiuiX uses the configured
     * radius while both implementations intentionally keep the same HotSeats MiShadow. Clamp
     * only positive themed utility radii; preserve vendor disable semantics and blend arrays.
     * This deliberately does not require a live Prismal binding because the vendor can call the
     * utility while constructing the replacement background, before hierarchy rebind completes.
     */
    static int clampCompatBackgroundBlurRadius(
            View dockBg, int requestedRadius, LiquidDockConfig config) {
        if (dockBg == null || config == null || requestedRadius <= 0) return requestedRadius;
        if (!COMPAT_BACKGROUND_CLASS.equals(dockBg.getClass().getName())) return requestedRadius;
        int targetRadius = Math.round(
                config.glass.blur * dockBg.getResources().getDisplayMetrics().density);
        if (requestedRadius != targetRadius && compatBackgroundBlurLoggedFor != dockBg) {
            compatBackgroundBlurLoggedFor = dockBg;
            MainHook.log(TAG + " compat BlurBackground2 background blur clamped "
                    + requestedRadius + " -> " + targetRadius);
        }
        return targetRadius;
    }
''',
'''    /**
     * BlurBackground2.addBlur() routes a positive vendor blur radius through this utility before
     * it reaches hidden View background-blur APIs. theme(3) shows that even radius=5 becomes a
     * SurfaceFlinger region blur on the whole Floating Dock, post-processing Prismal. For the
     * exact themed HotSeats background, suppress every positive vendor radius to zero. Disable
     * calls and every other BlurUtilities consumer pass through unchanged.
     */
    static int suppressCompatBackgroundBlurRadius(View dockBg, int requestedRadius) {
        if (dockBg == null || requestedRadius <= 0) return requestedRadius;
        if (!COMPAT_BACKGROUND_CLASS.equals(dockBg.getClass().getName())) return requestedRadius;
        if (compatBackgroundBlurLoggedFor != dockBg) {
            compatBackgroundBlurLoggedFor = dockBg;
            MainHook.log(TAG + " compat BlurBackground2 GPU background blur suppressed "
                    + requestedRadius + " -> 0");
        }
        return 0;
    }
''', 'compat radius helper')

glass = replace_once(glass,
'''        // Detached/old host: never stack a second glass layer on a recreated Dock hierarchy.
        removeNativeBackgroundPreserver();
''',
'''        // Detached/old host: never stack a second glass layer on a recreated Dock hierarchy.
        removeVendorGpuBlurSuppressor();
''', 'remove old preserver')

glass = replace_once(glass,
'''        backgroundRef = null;
        compatBackgroundBlurLoggedFor = null;
        nativeBlurRadiusPx = -1;
        nativeBlurRadiusFailureLogged = false;

        float density = dockBg.getResources().getDisplayMetrics().density;
        int blurPx = Math.round(config.glass.blur * density);
        nativeBlurRadiusPx = nativeVisualOwner ? blurPx : -1;
        if (nativeMaterial) {
            boolean passOk = MiBlurBridge.applyPassWindowBlur(dockBg, blurPx);
            MainHook.log(TAG + " passWindowBlur radius=" + blurPx + " ok=" + passOk);
            if (!passOk) {
                // The true MiuiX material normally owns compositor pass-window blur.
                boolean contentOk = MiBlurBridge.applyContentBlur(
                        dockBg, blurPx, config.glass.captureScale);
                MainHook.log(TAG + " fallback to content blur ok=" + contentOk);
            }
        } else if (nativeVisualOwner) {
            // BlurBackground2 owns its vendor backdrop/outline stack. Its separate utility
            // background-blur radius is clamped at BlurUtilities; keep the normal HotSeats
            // MiShadow because the working default MiuiX path uses the same shadow parameters.
            MainHook.log(TAG + " compat BlurBackground2 keeps vendor blur/outline radius="
                    + blurPx);
        }
''',
'''        backgroundRef = null;
        vendorGpuBlurLoggedFor = null;
        compatBackgroundBlurLoggedFor = null;

        // Both 307 backgrounds can attach pass-window blur to the whole Floating Dock Surface.
        // Disable that compositor stage before Prismal is installed; the vendor View remains only
        // as geometry until DockLiquidGlassView hides it after the first valid capture.
        if (nativeVisualOwner) suppressVendorGpuBlur(dockBg);
''', 'install vendor blur block')

glass = replace_once(glass,
'''        glass.setCaptureScale(config.glass.captureScale);
        glass.setCapturePowerLimitFps(config.glass.captureFps);
        if (nativeVisualOwner) enforcePrismalOpticalOnly(glass);
        MainHook.log(TAG + " capture tuning fps=" + config.glass.captureFps
''',
'''        glass.setCaptureScale(config.glass.captureScale);
        glass.setCapturePowerLimitFps(config.glass.captureFps);
        // LiquidGlassFactory already applied config.blur/config.blurMode. Do not override them:
        // the GPU capture is the input and Prismal itself owns blur/refraction/highlight.
        MainHook.log(TAG + " capture tuning fps=" + config.glass.captureFps
''', 'remove optical-only override')

glass = replace_once(glass,
'''        // Preserve the MiuiX drawable/pass-window blur. DockLiquidGlassView normally hides its
        // geometrySource after the first captured frame; on 307 that source is the actual native
        // material background and must stay visible underneath the Prismal layer.
        backgroundRef = dockBg;
        glassRef = glass;
        hostRef = host;
        if (nativeVisualOwner) enforceNativeBlurRadius(dockBg);
        installNativeBackgroundPreserver(dockBg, glass, nativeVisualOwner);
        HomeOwnershipRuntime.bind(glass, glass.getContext());

        DockStrokeRenderer.configure(dockBg, config.dock, radius);
        MainHook.log(TAG + " Prismal optical layer installed above "
                + (nativeMaterial ? "MiuiX native material" : "BlurBackground2 vendor visual")
                + " background with live ownership");
''',
'''        // Keep the vendor View only as DockLiquidGlassView's geometry source. Once a capture is
        // installed, the normal glass lifecycle hides that source; do not restore its alpha/latch.
        backgroundRef = dockBg;
        glassRef = glass;
        hostRef = host;
        if (nativeVisualOwner) suppressVendorGpuBlur(dockBg);
        installVendorGpuBlurSuppressor(dockBg);
        HomeOwnershipRuntime.bind(glass, glass.getContext());

        DockStrokeRenderer.configure(dockBg, config.dock, radius);
        MainHook.log(TAG + " Prismal owns blur; native 307 background is geometry only class="
                + dockBg.getClass().getSimpleName());
''', 'post-bind ownership')

glass = replace_once(glass,
'''        host.setVisibility(dockBg.getVisibility());
        host.invalidate();
''',
'''        if (isNativeVisualOwner(dockBg)) suppressVendorGpuBlur(dockBg);
        host.setVisibility(dockBg.getVisibility());
        host.invalidate();
''', 'syncSize suppression')

glass = replace_once(glass,
'''        if (isNativeVisualOwner(dockBg)) {
            float density = dockBg.getResources().getDisplayMetrics().density;
            nativeBlurRadiusPx = Math.round(config.glass.blur * density);
            enforceNativeBlurRadius(dockBg);
        } else {
            nativeBlurRadiusPx = -1;
        }

        float radius = readRadius(dockBg);
''',
'''        if (isNativeVisualOwner(dockBg)) suppressVendorGpuBlur(dockBg);

        float radius = readRadius(dockBg);
''', 'syncGeometry suppression')

glass = replace_once(glass,
'''    /** Both 307 implementations own the native backdrop/outline/shadow visual stack. */
    private static boolean isNativeVisualOwner(View dockBg) {
''',
'''    /** Both supported 307 implementations are vendor geometry sources for the injected host. */
    private static boolean isNativeVisualOwner(View dockBg) {
''', 'visual owner comment')

start = glass.index('    /**\n     * MiuiX already owns the actual backdrop blur.')
end = glass.index('    private static int readDimension', start)
replacement = '''    /**
     * Disable every vendor compositor/pass-window blur stage on the bound 307 background.
     * SurfaceFlinger applies that effect to the Floating Dock Surface after child composition,
     * so leaving even the correct radius active would blur/cover Prismal's rendered output.
     */
    static void suppressVendorGpuBlur(View dockBg) {
        if (dockBg == null || !isNativeVisualOwner(dockBg)) return;
        // Radius-zero first gives a narrow fail-safe even if one hidden disable entry point fails.
        MiBlurBridge.setPassWindowBlurRadius(dockBg, 0);
        MiBlurBridge.clearPassWindowBlur(dockBg);
        if (vendorGpuBlurLoggedFor != dockBg) {
            vendorGpuBlurLoggedFor = dockBg;
            MainHook.log(TAG + " vendor GPU background blur disabled; Prismal owns blur class="
                    + dockBg.getClass().getSimpleName());
        }
    }

    /**
     * HyperOS can reapply its material state during animation without replacing the background.
     * Reassert only GPU-blur suppression before draw. Crucially, this listener never restores
     * dockBg alpha and never touches DockLiquidGlassView.nativeBackgroundHiddenByGlass: the
     * ordinary glass lifecycle must be free to hide the native geometry source after capture.
     */
    private static void installVendorGpuBlurSuppressor(View dockBg) {
        removeVendorGpuBlurSuppressor();
        View root = dockBg.getRootView();
        ViewTreeObserver observer = root != null ? root.getViewTreeObserver() : null;
        if (observer == null || !observer.isAlive()) return;

        ViewTreeObserver.OnPreDrawListener listener = () -> {
            if (backgroundRef == dockBg) suppressVendorGpuBlur(dockBg);
            return true;
        };
        observer.addOnPreDrawListener(listener);
        vendorBlurObserver = observer;
        vendorBlurSuppressor = listener;

        dockBg.post(() -> {
            if (backgroundRef == dockBg) suppressVendorGpuBlur(dockBg);
        });
    }

    private static void removeVendorGpuBlurSuppressor() {
        ViewTreeObserver observer = vendorBlurObserver;
        ViewTreeObserver.OnPreDrawListener listener = vendorBlurSuppressor;
        vendorBlurObserver = null;
        vendorBlurSuppressor = null;
        if (observer == null || listener == null) return;
        try {
            if (observer.isAlive()) observer.removeOnPreDrawListener(listener);
        } catch (Throwable ignored) {}
    }

'''
glass = glass[:start] + replacement + glass[end:]

# ---- Pipeline: themed BlurUtilities positive blur is suppressed to zero, not clamped to config. ----
pipeline = replace_once(pipeline,
''' * HyperOS can switch the live HotSeats background implementation when an icon theme is applied.
 * Keep the vendor background installed as the backdrop-blur/gradient owner and place LiquidDock's
 * existing Prismal glass stack directly above whichever supported implementation is active.
''',
''' * HyperOS can switch the live HotSeats background implementation when an icon theme is applied.
 * Keep either vendor background only as a geometry source while LiquidDock's Prismal glass owns
 * the real blur/optical pass; vendor compositor blur is explicitly suppressed.
''', 'pipeline class comment')

pipeline = replace_once(pipeline,
'''            installHomeGesturePrearm(classLoader);
            installCompatBackgroundBlurClamp(classLoader, config);
''',
'''            installHomeGesturePrearm(classLoader);
            installCompatBackgroundBlurSuppression(classLoader);
''', 'install suppression')

pipeline = replace_once(pipeline,
'''    /**
     * BlurBackground2.addBlur() delegates its hard-coded positive radius through this exact
     * utility boundary before reflection reaches hidden View.setBackgroundBlur. Clamp only the
     * themed HotSeats background argument; default MiuiX and every other BlurUtilities consumer
     * pass through unchanged, as do radius<=0 disable calls and both vendor blend arrays.
     */
    private static void installCompatBackgroundBlurClamp(
            ClassLoader classLoader, LiquidDockConfig config) {
        try {
            HookUtil.hookMethod(classLoader,
                    "com.miui.home.launcher.common.BlurUtilities", "setBackgroundBlur",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (args.length >= 2 && args[0] instanceof View
                                && args[1] instanceof Integer) {
                            args[1] = MiuixGlassHook.clampCompatBackgroundBlurRadius(
                                    (View) args[0], (Integer) args[1], config);
                        }
                        return chain.proceed(args);
                    }, View.class, int.class, float[].class, int[][].class);
            MainHook.log("[DC] MiuiX 307 compat background blur clamp installed");
        } catch (Throwable error) {
            MainHook.log("[DC] MiuiX 307 compat background blur clamp unavailable: " + error);
        }
    }
''',
'''    /**
     * BlurBackground2.addBlur() delegates positive vendor blur through this exact utility before
     * reflection reaches hidden View APIs. On 307 that becomes a post-composition region blur on
     * the Floating Dock Surface, so the themed HotSeats radius must be zero while Prismal is the
     * visual owner. Other BlurUtilities consumers, disable calls and vendor arrays pass through.
     */
    private static void installCompatBackgroundBlurSuppression(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader,
                    "com.miui.home.launcher.common.BlurUtilities", "setBackgroundBlur",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (args.length >= 2 && args[0] instanceof View
                                && args[1] instanceof Integer) {
                            args[1] = MiuixGlassHook.suppressCompatBackgroundBlurRadius(
                                    (View) args[0], (Integer) args[1]);
                        }
                        return chain.proceed(args);
                    }, View.class, int.class, float[].class, int[][].class);
            MainHook.log("[DC] MiuiX 307 compat background blur suppression installed");
        } catch (Throwable error) {
            MainHook.log("[DC] MiuiX 307 compat background blur suppression unavailable: "
                    + error);
        }
    }
''', 'pipeline helper')

pipeline = replace_once(pipeline,
'''        // Decompiled BlurBackground2.addBlur() is invoked by both attach and radius updates.
        // Its hard-coded utility radius is already clamped before the original reaches View;
        // run geometry sync afterwards to keep the pass-window radius and Prismal shape aligned.
''',
'''        // Decompiled BlurBackground2.addBlur() is invoked by both attach and radius updates.
        // Its positive utility radius is suppressed before it reaches View; geometry sync then
        // reasserts vendor GPU-blur disable while keeping the Prismal shape aligned.
''', 'themed hook comment')

# Guard against accidentally retaining the superseded ownership architecture.
for forbidden in (
    'enforcePrismalOpticalOnly',
    'installNativeBackgroundPreserver',
    'nativeBackgroundHiddenByGlass',
    'nativeBlurRadiusPx',
    'applyPassWindowBlur(dockBg, blurPx)',
    'clampCompatBackgroundBlurRadius',
    'installCompatBackgroundBlurClamp',
):
    if forbidden in glass or forbidden in pipeline:
        raise SystemExit(f'forbidden legacy ownership token remains: {forbidden}')

for required in (
    'suppressVendorGpuBlur',
    'setPassWindowBlurRadius(dockBg, 0)',
    'clearPassWindowBlur(dockBg)',
    'Prismal owns blur',
    'suppressCompatBackgroundBlurRadius',
    'GPU background blur suppressed',
):
    if required not in glass and required not in pipeline:
        raise SystemExit(f'missing required ownership token: {required}')

glass_path.write_text(glass)
pipeline_path.write_text(pipeline)
print('patched Prismal blur ownership')
