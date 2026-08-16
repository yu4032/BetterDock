# APP → HOME animation handoff implementation plan

1. Add RED state tests proving `GestureToHome` cannot move an APP scene to HOME before an explicit animation-end signal, and that RECENTS/APP interruption cancels the hold.
2. Add a RED source contract requiring an APP HOME animation hook wired alongside the existing Recents lifecycle hook.
3. Extend `CaptureSceneState` with a bounded APP→HOME pending hold and an explicit animation-end target; keep Recents/All Apps precedence and preserve UNKNOWN fail-closed behavior elsewhere.
4. Add `AppHomeAnimationHook` for HyperOS `GestureModeApp$8.onAnimationEnd`, paired only after a pending `CLOSE_TO_HOME` path, then route completion through `HomeOwnershipRuntime` to the current glass view.
5. Run GitHub Actions `testDebugUnitTest` and `assembleDebug`; verify the exact branch commit and artifact.
6. Do not merge to main until device testing confirms APP→HOME no longer snaps at the final handoff and Recents remains fixed.
