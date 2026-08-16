# Freeform Task Leash Exclusion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the obsolete UID -> SurfaceFlinger debug layer-name freeform exclusion path with direct exclusion of HyperOS WMShell freeform task `SurfaceControl` leashes, while making every SystemUI failure degrade only to the existing wallpaper fallback.

**Architecture:** SystemUI passively observes the existing `FreeformTaskListener`, exposes a tiny asynchronous Binder provider, and registers that provider with a module-app broker service. Launcher discovers the provider through the broker, batch-resolves every visible freeform task ID to a parcel-copied `SurfaceControl`, and passes those wrappers directly to the already-existing `CaptureArgs.Builder.setExcludeLayers(SurfaceControl[])` path. WMShell state is read only on the existing task-organizer executor; no second organizer or task state machine is created.

**Tech Stack:** Java 17, Android API 37, libxposed API 101, raw Binder/Parcel, Android `SurfaceControl`, JUnit 4.

## Global Constraints

- Branch: `fix/freeform-task-leash-exclusion`, based on clean `api101-migration@10455c6bb10d90e01c9f1299d0af0a6754ac54ab`.
- Never merge code from `dev/freeform-capture-diagnostic`.
- Never use `ISurfaceComposer`, `getLayerDebugInfo()`, UID-to-layer-name lookup, or freeform layer-name guessing in the production path.
- Never register another `TaskOrganizer`, mutate WMShell task state, submit `SurfaceControl.Transaction`, or release the SystemUI-owned source leash.
- SystemUI exceptions must never escape LiquidDock hooks, Binder handlers, shell-executor runnables, or callbacks.
- `SurfaceControl` is written with parcel flags `0`, never `PARCELABLE_WRITE_RETURN_VALUE`.
- All visible freeform tasks on the capture display must resolve; partial resolution is unsafe and must use the existing WALLPAPER fallback.
- Launcher waits at most 25 ms per leash batch request. Timeout is normal unavailability, not an infrastructure failure.
- Three infrastructure failures disable the failing bridge side for the current process only.
- All commits use `[skip ci]`; do not force-push.

---

### Task 1: Pure bridge policy and breaker

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/FreeformBridgePolicy.java`
- Test: `src/test/java/com/hellovoid/liquiddock/FreeformBridgePolicyTest.java`

**Interfaces:**
- Produces: `FreeformBridgePolicy.packageListContains(String[] packages, String expected)`, `FreeformBridgePolicy.deduplicateTaskIds(int[] taskIds)`, and nested `CircuitBreaker` with `recordInfrastructureFailure()`, `isDisabled()`, `resetForTest()`.

- [ ] **Step 1: Write failing unit tests** for package-membership authorization, task-ID deduplication preserving order, normal unavailability not counting toward the breaker, and exactly three infrastructure failures disabling it.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests com.hellovoid.liquiddock.FreeformBridgePolicyTest` and verify RED because the class is absent.
- [ ] **Step 3: Implement minimal Android-free policy code.** `packageListContains` must tolerate null arrays/items; `deduplicateTaskIds` must use insertion order; `CircuitBreaker` increments only when explicitly told an infrastructure failure occurred and disables at count 3.
- [ ] **Step 4: Re-run the focused unit test and verify GREEN.**
- [ ] **Step 5: Commit** `test/feat: add freeform bridge safety policy [skip ci]`.

### Task 2: Broker rendezvous service and process scope

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/FreeformLeashProtocol.java`
- Create: `src/main/java/com/hellovoid/liquiddock/FreeformLeashBrokerService.java`
- Modify: `src/main/AndroidManifest.xml`
- Modify: `src/main/resources/META-INF/xposed/scope.list`
- Modify: `src/main/java/com/hellovoid/liquiddock/ModuleMain.java`
- Test: `src/test/java/com/hellovoid/liquiddock/FreeformBrokerContractTest.java`

**Interfaces:**
- `FreeformLeashProtocol`: fixed descriptors and transaction IDs for broker register/get, provider batch request, and callback batch result. It also defines status integers and `MAX_TASKS = 32`, `REQUEST_TIMEOUT_MS = 25`.
- `FreeformLeashBrokerService`: exported bound service whose Binder stores only one provider `IBinder` plus its `DeathRecipient`.
- `ModuleMain`: dispatches Launcher initialization exactly as before for `com.miui.home`; SystemUI receives only `SystemUiFreeformLeashProvider.install(classLoader)` inside a top-level `try/catch(Throwable)`.

- [ ] **Step 1: Write failing contract tests** asserting both Xposed scopes are present, the manifest declares the explicit broker service, broker registration requires SystemUI package membership, broker retrieval requires Launcher package membership, provider death clears broker state, and SystemUI dispatch cannot call `MainHook`/workstation hooks.
- [ ] **Step 2: Run focused contract tests and verify RED.**
- [ ] **Step 3: Implement raw Binder broker.** `onTransact` must validate interface token and `Binder.getCallingUid()`, resolve caller packages through the service `PackageManager`, accept `REGISTER_PROVIDER` only from `com.android.systemui`, accept `GET_PROVIDER` only from `com.miui.home`, unlink the old death recipient before replacement, and return null when unavailable. All unexpected errors are caught and logged; no `SurfaceControl` is ever read or written by the broker.
- [ ] **Step 4: Extend scope and package dispatch.** Add `com.android.systemui` to `scope.list`; add the broker service with `android:exported="true"`; keep all existing Launcher initialization byte-for-byte semantically unchanged.
- [ ] **Step 5: Re-run focused tests and verify GREEN.**
- [ ] **Step 6: Commit** `feat: add fail-safe freeform leash broker [skip ci]`.

### Task 3: Passive SystemUI leash provider

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/SystemUiFreeformLeashProvider.java`
- Create: `src/main/java/com/hellovoid/liquiddock/FreeformLeashBrokerClient.java`
- Test: `src/test/java/com/hellovoid/liquiddock/SystemUiFreeformLeashProviderContractTest.java`

**Interfaces:**
- `SystemUiFreeformLeashProvider.install(ClassLoader)`: finds `com.android.wm.shell.freeform.FreeformTaskListener`, resolves `mContext`, `mShellTaskOrganizer`, `mTasks`, nested state `mLeash`, hooks every declared constructor via `HookUtil.hook(Constructor, ...)`, always calls `chain.proceed(...)` first, then observes the successfully constructed instance.
- `FreeformLeashBrokerClient`: explicit-component `bindService()` helper with process-local broker Binder, death handling, capped reconnect delay sequence 250/500/1000/2000/5000 ms, and no permanent retry loop when not demanded.
- Provider Binder: accepts one-way batch request `(requestId, int[] taskIds, callbackBinder)`, authorizes Launcher UID, copies primitive IDs, and posts lookup to the executor resolved from the existing `mShellTaskOrganizer.getExecutor()`.

- [ ] **Step 1: Write failing source/contract tests** asserting constructor after-observation, no `TaskOrganizer` registration, no `SurfaceControl.Transaction`, no source-leash `release()`, no `SystemUIApplication` hook, use of `WeakReference`, shell-executor dispatch before reading `mTasks`, parcel flags `0`, and catch-all guards around hook/Binder/executor/callback boundaries.
- [ ] **Step 2: Run focused tests and verify RED.**
- [ ] **Step 3: Implement broker client connection lifecycle.** Binding failures and binder death only clear capability and schedule capped demand-driven reconnect; they never throw into the host process.
- [ ] **Step 4: Implement provider installation and capability probe.** Resolve reflection fields once; if any required structure is absent, disable the SystemUI bridge for the current process. After each original constructor succeeds, store a `WeakReference` to the listener and application context from `mContext`, then demand broker registration.
- [ ] **Step 5: Implement one-way batch lookup.** On Binder thread: authorize, bound task count to `MAX_TASKS`, copy task IDs, enqueue to shell executor, return immediately. On shell executor: read current listener -> `mTasks.get(taskId)` -> state `mLeash`, never modify state, build callback Parcel, call `writeTypedObject(surface, 0)`, and send callback with `IBinder.FLAG_ONEWAY`. Missing task/listener is normal unavailability; structural/reflection/protocol failures count toward the 3-failure breaker.
- [ ] **Step 6: Re-run focused tests and verify GREEN.**
- [ ] **Step 7: Commit** `feat: expose existing SystemUI freeform task leashes safely [skip ci]`.

### Task 4: Launcher batch resolver and remote-wrapper ownership

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/FreeformTaskLeashResolver.java`
- Test: `src/test/java/com/hellovoid/liquiddock/FreeformTaskLeashResolverTest.java`
- Test: `src/test/java/com/hellovoid/liquiddock/FreeformTaskLeashResolverContractTest.java`

**Interfaces:**
- Constructor: `FreeformTaskLeashResolver(Context context)`.
- `Resolution resolveVisibleLeashes(int displayId)`: scans up to 32 `RunningTaskInfo`, keeps visible `WINDOWING_MODE_FREEFORM` tasks on the requested display, deduplicates by task ID, requests all IDs in one provider transaction, waits at most 25 ms, and returns either safe full coverage or unavailable.
- Nested `Resolution implements AutoCloseable`: `boolean hasVisibleFreeformTasks()`, `boolean isSafe()`, `SurfaceControl[] borrowedRemoteLeashes()`, `close()` releases only parcel-created Launcher wrappers exactly once.

- [ ] **Step 1: Write failing tests** for 0-task safe/no-query behavior, display filtering, full coverage safe, partial coverage unsafe, timeout unsafe without breaker increment, response task-ID mismatch unsafe, provider death invalidating cache, and close-once ownership state.
- [ ] **Step 2: Run focused tests and verify RED.**
- [ ] **Step 3: Implement visible-task scan** preserving current `FreeformCapturePolicy.shouldExclude(mode, visible)` semantics, including vendor visibility fallback, but storing task IDs rather than package UIDs.
- [ ] **Step 4: Implement asynchronous request state.** Create per-request callback Binder + `CountDownLatch`; provider request is one-way; wait `REQUEST_TIMEOUT_MS`; late response must release every received wrapper immediately if request already expired.
- [ ] **Step 5: Validate all-or-nothing coverage.** Safe only when the exact deduplicated requested task-ID set has one valid returned `SurfaceControl` each. No surfaces are cached across captures.
- [ ] **Step 6: Implement `Resolution.close()`** using an atomic/guarded closed state and `SurfaceControl.release()` only on remote parcel-created wrappers.
- [ ] **Step 7: Re-run focused tests and verify GREEN.**
- [ ] **Step 8: Commit** `feat: resolve visible freeform task leashes in Launcher [skip ci]`.

### Task 5: Dock capture integration and obsolete SF path removal

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/FreeformCaptureExclusionTest.java`
- Delete when unused: `src/main/java/com/hellovoid/liquiddock/FreeformLayerResolver.java`
- Delete when unused: `src/main/java/com/hellovoid/liquiddock/SurfaceLayerNameResolver.java`
- Test: `src/test/java/com/hellovoid/liquiddock/FreeformLeashCaptureIntegrationContractTest.java`

**Interfaces:**
- Replace fields `SurfaceLayerNameResolver` + `FreeformLayerResolver` with `FreeformTaskLeashResolver`.
- `FullDisplayExclusions implements AutoCloseable` carries `String[] layerNames`, `SurfaceControl[] surfaceControls`, `boolean safe`, and optional owning `Resolution`.
- `resolveFullDisplayExclusions(int displayId)` merges existing Dock surface exclusion with every remote freeform leash; layer names remain only for existing Dock/drag-layer behavior, never for freeform.

- [ ] **Step 1: Rewrite/add failing contract tests** asserting production Dock code no longer references `resolveVisibleLayerNames`, `SurfaceLayerNameResolver`, `ISurfaceComposer`, or `getLayerDebugInfo`; it must pass freeform `SurfaceControl[]` into `captureScreenAsync`; partial/unavailable resolution must retain WALLPAPER fallback.
- [ ] **Step 2: Run focused tests and verify RED.**
- [ ] **Step 3: Integrate direct exclusions for normal FULL_DISPLAY.** Resolve leashes only when full-display exclusions are actually needed; submit capture inside `try/finally`; close remote wrappers immediately after `captureScreenAsync()` returns from request submission.
- [ ] **Step 4: Preserve workstation LOCAL_LAYER behavior without retaining remote wrappers across async callbacks.** Do not pre-resolve remote leashes for a local-layer attempt. If local-layer capture fails or is unavailable and code wants FULL_DISPLAY fallback, resolve a fresh `FullDisplayExclusions` inside that fallback callback/path, submit once, then close in `finally`. If fresh resolution is unsafe, use wallpaper exactly as before.
- [ ] **Step 5: Update log text** from “resolvable SurfaceFlinger layer” to “resolvable freeform task leash”; do not change scene ownership/source policy.
- [ ] **Step 6: Delete the obsolete resolvers** only after checking no production/test caller remains. Keep `CaptureExclusionNames` if still used for Dock/drag names.
- [ ] **Step 7: Re-run all freeform/capture/workstation unit and contract tests and verify GREEN.**
- [ ] **Step 8: Commit** `fix: exclude freeform task leashes from live capture [skip ci]`.

### Task 6: Verification and branch review

**Files:**
- Modify only if verification exposes a defect; no opportunistic refactors.

- [ ] **Step 1: Run full JVM suite:** `./gradlew testDebugUnitTest`.
- [ ] **Step 2: Run source scans:** verify no production reference to `ISurfaceComposer`, `getLayerDebugInfo`, or freeform UID-to-layer-name resolution; verify no `TaskOrganizer.register*` added; verify `scope.list` includes exactly the intended package scopes.
- [ ] **Step 3: Run Android compile/package check if the local environment supports it:** `./gradlew assembleDebug`. This is compile verification only; do not claim device behavior from it.
- [ ] **Step 4: Compare branch against `api101-migration`** and confirm changes are limited to the approved broker/provider/resolver/integration/test/docs scope; explicitly ensure the diagnostic implementation branch was not merged.
- [ ] **Step 5: Review SystemUI safety gates:** all injected boundaries catch `Throwable`, broker/provider failures return unavailable, shell state reads run on shell executor, source leash is never released, and fallback remains WALLPAPER.
- [ ] **Step 6: Commit any verification-only corrections** with `[skip ci]`; otherwise do not create an empty commit.
- [ ] **Step 7: Report exact HEAD, tests/commands actually run, and clearly distinguish static/JVM/Gradle verification from device testing. Device acceptance remains the 10-case matrix in the approved spec.
