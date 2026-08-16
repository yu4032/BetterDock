# APP → HOME animation handoff design

Status: approved for implementation by the user's request to fix the confirmed issue.

## Problem

`GestureToHome` announces the destination before HyperOS finishes the visual APP → HOME transition. The actual Launcher path runs a `CLOSE_TO_HOME` spring/Surface animation with `needFinishOnAnimEnd=true`. Switching LiquidDock to HOME/wallpaper at the destination event therefore ends full-display live capture before the last composed frames.

## Design

- Preserve SystemUI as the sole ordinary HOME/APP ownership authority.
- Treat `GestureToHome` from an active APP scene as a pending visual handoff, not as animation completion.
- Keep the effective capture scene APP while that handoff is pending, even if the SystemUI baseline reaches HOME early.
- Observe the exact Launcher `CLOSE_TO_HOME` animation listener and release the pending handoff on its `onAnimationEnd` callback.
- An APP or RECENTS gesture cancels a pending HOME handoff immediately.
- Recents keeps its existing independent `RecentsContainer.setIsExitRecentsAnimating(true/false)` lifecycle.
- A bounded pending-handoff deadline is only a failure-recovery watchdog if the vendor callback is absent; it is not the normal correctness boundary.

## Non-goals

No changes to HOME/APP authority, Floating Dock focus handling, wallpaper cache semantics, capture mode, freeform exclusion, or workstation ownership.
