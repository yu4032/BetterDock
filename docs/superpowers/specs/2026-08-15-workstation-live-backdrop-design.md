# Workstation Live Backdrop Design

## Goal

When workstation mode is active, All Apps and Recents must refresh the liquid-glass backdrop at both entry and exit boundaries, then keep sampling only while the sampled background is changing. Sampling stops after the background stabilizes. Normal-mode capture behavior must remain unchanged.

## Capture source policy

Workstation All Apps and confirmed Recents prefer the Launcher-owned local layer rooted at the corresponding capture root. This avoids including the independent workstation Dock/icon surface. If that local layer cannot be captured, fall back to full-display capture while explicitly excluding the Dock window SurfaceControl. Workstation APP outside these launcher-owned scenes remains wallpaper-backed as before.

## Adaptive workstation burst

A workstation-only burst starts at each All Apps/Recents entry or exit boundary. The first installed frame establishes a visual signature. Subsequent frames are compared using the existing VisualProbe signature. Any changed signature resets the stable counter and keeps the burst alive. Two consecutive unchanged comparisons after at least three samples end the burst. This prevents a one-frame pre-animation pause from terminating sampling too early while still stopping quickly after the scene settles.

## Scene lifecycle

All Apps entry temporarily unsuspends workstation glass, makes the glass visible, forces an immediate capture, and starts a burst. All Apps exit forces a fresh capture and starts an exit burst before workstation glass is suspended after stabilization.

Recents entry continues to use the exact workstation Recents button boundary, but its capture input must exclude the Dock. Recents exit forces a refresh at the exit boundary and keeps the burst alive through the closing animation; once the background is stable and Recents is hidden, workstation glass is suspended again.

## Isolation

Every new adaptive-burst and local-layer preference branch is gated by workstationMode. Existing HOME/APP/normal All Apps/normal Recents behavior, dynamic APP capture, wallpaper caching, and capture cadence configuration remain unchanged.

## Verification

Add pure-Java tests for workstation source selection and burst convergence, plus source contracts asserting workstation full-display fallback excludes the Dock and All Apps/Recents boundaries start/refresh the workstation burst. Run testDebugUnitTest and assembleDebug in CI before producing an APK.
