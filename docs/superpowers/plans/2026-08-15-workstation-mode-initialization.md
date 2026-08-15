# Workstation Mode Initialization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve workstation mode synchronously during Launcher startup when `LauncherModeController.isLaptopMode()` returns `null`, while preserving the verified helper capture/rendering behavior.

**Architecture:** Keep `LauncherModeController.isLaptopMode()` as the primary source. If its initial result is `null`, immediately read `DeviceConfig.isMingouLaptopPcModeEnabled()` as a read-only fallback before `installWorkstationModeGuard()` returns; retain `onLaptopModeChanged` as authoritative and keep the existing 2000 ms re-check only as resilience fallback.

**Tech Stack:** Java 17, Android, libxposed API 101.

## Global Constraints

- Modify production code only in `src/main/java/com/hellovoid/liquiddock/MainHook.java`.
- Do not modify Dock background visibility, LiquidGlass rendering, APP→HOME capture, Recents capture, or workstation geometry.
- Do not introduce `LauncherSceneController`, `ForegroundAuthorityGate`, or any new capture-state architecture.
- Preserve `workstationModeHookConfirmed` and the guarded 2000 ms re-check.
- Preserve the legacy DeviceConfig compatibility path when the current API itself is unavailable.
- Do not create or trigger any CI workflow or extra build for this change.

---

### Task 1: Resolve initial workstation state synchronously

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/MainHook.java`

**Interfaces:**
- Consumes: `LauncherModeController.isLaptopMode()`, `DeviceConfig.isMingouLaptopPcModeEnabled()`, `LaptopStateManager.onLaptopModeChanged(boolean)`.
- Produces: `workstationMode` is initialized before Dock setup using a Boolean current-API result or an immediate read-only DeviceConfig fallback when the current API result is `null`.

- [ ] **Step 1: Inspect current `installWorkstationModeGuard()` and confirm the bug condition**

Confirm the current code converts an initial `null` result to `false` and defers DeviceConfig fallback until the 2000 ms runnable.

- [ ] **Step 2: Implement the minimal startup fallback**

Replace the initial assignment with:

```java
Object laptopResult = HookUtil.invokeStatic(
        "com.miui.home.launcher.allapps.LauncherModeController", "isLaptopMode");
if (laptopResult instanceof Boolean) {
    workstationMode = (Boolean) laptopResult;
} else {
    Object dcResult = HookUtil.invokeStatic(
            "com.miui.home.launcher.DeviceConfig", "isMingouLaptopPcModeEnabled");
    workstationMode = dcResult instanceof Boolean && (Boolean) dcResult;
}
```

Do not alter the authoritative hook callback, delayed re-check, background visibility, or capture logic.

- [ ] **Step 3: Static verification**

Fetch the resulting `MainHook.java` and confirm:

```text
initial isLaptopMode() Boolean -> direct use
initial isLaptopMode() null -> immediate DeviceConfig read
onLaptopModeChanged -> workstationModeHookConfirmed = true
2000 ms retry -> still guarded by workstationModeHookConfirmed
```

- [ ] **Step 4: Repository diff verification**

Compare the implementation commit against its parent and confirm the only production file changed is `MainHook.java`; no new capture-state/runtime class or workflow is introduced.

- [ ] **Step 5: Commit without CI**

Commit with `[skip ci]` in the message so the change does not trigger an extra GitHub Actions build.
