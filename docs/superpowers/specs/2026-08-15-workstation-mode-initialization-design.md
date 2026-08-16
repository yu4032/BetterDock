# Workstation Mode Initialization Fix Design

## Goal

Preserve the verified `38d43ba` capture/rendering behavior while fixing workstation-mode initialization so Launcher does not spend its first ~2 seconds incorrectly classified as normal mode.

## Root cause

`LauncherModeController.isLaptopMode()` can return `null` during early Launcher binding. The current null-safe implementation converts that `null` to `false`, marks the current API path as detected, and only corrects the state during a delayed re-check. That allows `setupViews()` and other Dock initialization to run once under the wrong normal-mode state. When the delayed re-check later flips `workstationMode` to `true`, existing workstation transition code hides the normal Dock background/glass layers, which can leave the visible workstation Dock without a background.

The old helper behavior avoided this particular timing window because failure of the early current-API read immediately fell through to `DeviceConfig`, so the initial mode was resolved synchronously before Dock setup.

## Design

`installWorkstationModeGuard()` keeps the current API as the primary source but resolves an early `null` synchronously:

1. Call `LauncherModeController.isLaptopMode()`.
2. If the result is a `Boolean`, use it directly.
3. If the result is `null`, immediately read `DeviceConfig.isMingouLaptopPcModeEnabled()` as a read-only fallback and use that Boolean result when available.
4. Install `LaptopStateManager.onLaptopModeChanged(boolean)` as before. The callback sets `workstationModeHookConfirmed = true` before calling `setWorkstationMode(entering)` so it remains authoritative.
5. Retain the 2000 ms delayed re-check only as a resilience fallback. It exits immediately when the hook has already confirmed state. Otherwise it re-queries `isLaptopMode()` and uses the same read-only DeviceConfig fallback only when the current API still returns `null`.
6. Do not change Dock background visibility, glass rendering, APP→HOME capture behavior, workstation geometry, Recents capture, or any capture-state architecture.

## Compatibility behavior

If the `LauncherModeController` API itself cannot be installed or called because the class/method is unavailable, preserve the existing legacy DeviceConfig compatibility path. This design only changes handling of an early `null` result from an otherwise available current API.

## Files

Production change is limited to:

- `src/main/java/com/hellovoid/liquiddock/MainHook.java`

No new runtime class, renderer, capture policy, or workflow is introduced.

## Verification

Static regression checks should confirm:

- the initial `isLaptopMode()` result is never directly unboxed;
- a `null` initial result invokes the read-only `isMingouLaptopPcModeEnabled()` fallback before `installWorkstationModeGuard()` returns;
- `workstationModeHookConfirmed = true` remains in `onLaptopModeChanged`;
- the delayed 2000 ms re-check remains guarded by `workstationModeHookConfirmed`;
- no production files other than `MainHook.java` change;
- no `LauncherSceneController`, `ForegroundAuthorityGate`, or APP→HOME handoff refactor is reintroduced;
- no CI workflow is added or triggered as part of this change.

Per user instruction, this change is not accompanied by extra CI builds. A later local/device build can be used for runtime verification.