# Orientation Layout Memory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add deterministic per-orientation desktop layout memory and a transaction-safe fallback planner for LiquidDock custom grids.

**Architecture:** Keep MIUI native occupancy/rotation internals untouched. Add pure Java placement/snapshot/planner primitives, then add a narrow Android sidecar store and runtime hook that captures/restores complete orientation snapshots around existing custom-grid rotation behavior.

**Tech Stack:** Java 17, Android SharedPreferences, libxposed API 101 hooks, JUnit 4, Gradle.

**Spec:** `docs/superpowers/specs/2026-08-22-orientation-layout-memory.md`

## Global Constraints

- Branch starts from `main`.
- Custom-grid disabled must remain behaviorally identical to stock MIUI.
- Do not directly write MIUI transposed occupancy matrices.
- Do not change the MIUI launcher database schema.
- Never commit a partial generated layout.
- Existing target-orientation memory has precedence over automatic repacking.

---

### Task 1: Pure placement model and snapshot validation

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/HomeGridOrientation.java`
- Create: `src/main/java/com/hellovoid/liquiddock/HomeGridItemPosition.java`
- Create: `src/main/java/com/hellovoid/liquiddock/HomeGridLayoutSnapshot.java`
- Test: `src/test/java/com/hellovoid/liquiddock/HomeGridLayoutSnapshotTest.java`

**Interfaces:**
- `HomeGridOrientation` exposes `LANDSCAPE`, `PORTRAIT`, and `other()`.
- `HomeGridItemPosition` is immutable and exposes id/screen/cell/span fields plus bounds helpers.
- `HomeGridLayoutSnapshot.create(...)` returns a snapshot only for a complete, in-bounds, non-overlapping placement set.

- [ ] **Step 1:** Write tests for orientation inversion, rectangle bounds, overlap rejection, duplicate-id rejection, and valid mixed 4x2/1x1 layouts.
- [ ] **Step 2:** Push only tests and verify CI fails because the production types do not exist.
- [ ] **Step 3:** Implement the three pure Java types minimally.
- [ ] **Step 4:** Verify the focused tests and full unit-test suite pass.
- [ ] **Step 5:** Commit the green implementation.

### Task 2: Deterministic first-layout planner

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/HomeGridPlacementPlanner.java`
- Test: `src/test/java/com/hellovoid/liquiddock/HomeGridPlacementPlannerTest.java`

**Interfaces:**
- `HomeGridPlacementPlanner.plan(HomeGridProfile profile, HomeGridOrientation target, Collection<HomeGridItemPosition> source, HomeGridLayoutSnapshot remembered)` returns `PlanResult`.
- `PlanResult.success()` is true only when it contains a complete valid snapshot.

- [ ] **Step 1:** Add failing tests proving remembered positions are preserved, large items are placed before 1x1 items, nearest-normalized-center fallback is deterministic, and impossible pages fail atomically.
- [ ] **Step 2:** Verify the new tests fail for the missing planner.
- [ ] **Step 3:** Implement stable priority ordering and nearest-cell search on a temporary occupancy grid.
- [ ] **Step 4:** Verify planner tests and all prior tests pass.
- [ ] **Step 5:** Commit the planner.

### Task 3: Sidecar orientation memory

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/HomeGridOrientationMemory.java`
- Create: `src/main/java/com/hellovoid/liquiddock/HomeGridOrientationMemoryStore.java`
- Create: `src/main/java/com/hellovoid/liquiddock/HomeGridSharedPreferencesMemoryStore.java`
- Test: `src/test/java/com/hellovoid/liquiddock/HomeGridOrientationMemoryTest.java`

**Interfaces:**
- Store keys are scoped by profile and orientation.
- A write replaces the entire snapshot for that scope atomically.
- Decode failure returns no snapshot rather than partial placements.

- [ ] **Step 1:** Add failing round-trip, profile-isolation, orientation-isolation, and corrupt-payload tests.
- [ ] **Step 2:** Verify RED.
- [ ] **Step 3:** Implement the in-memory-facing interface and SharedPreferences JSON/string codec without external dependencies.
- [ ] **Step 4:** Verify GREEN and full unit suite.
- [ ] **Step 5:** Commit persistence support.

### Task 4: Runtime snapshot capture/restore policy

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/HomeGridOrientationRuntime.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/ModuleMain.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/HomeGridProfileOverlayHook.java`
- Test: `src/test/java/com/hellovoid/liquiddock/HomeGridOrientationRuntimeContractTest.java`

**Interfaces:**
- Runtime is installed only when custom-grid mode is enabled.
- Rotation source snapshot is captured before native transformation mutates positions.
- A complete target snapshot can be restored through MIUI item-model setters/update path; native occupancy arrays remain owned by MIUI.
- If restore cannot be applied completely, abort restore and allow native transform.

- [ ] **Step 1:** Add source-contract tests that require gated installation, memory-first target restore, and explicit native-transform fallback.
- [ ] **Step 2:** Verify RED.
- [ ] **Step 3:** Implement a narrow reflection adapter around Workspace/ItemInfo model APIs already available in the launcher, with fail-open behavior.
- [ ] **Step 4:** Verify full unit suite and compile.
- [ ] **Step 5:** Commit runtime integration.

### Task 5: Other-orientation preflight after user edits

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/WorkspaceDropRuleHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/HomeGridOrientationRuntime.java`
- Test: `src/test/java/com/hellovoid/liquiddock/HomeGridOtherOrientationPreflightTest.java`

**Interfaces:**
- Current-direction user drop remains committed.
- Opposite snapshot is preserved if still compatible.
- Otherwise planner regenerates it; impossible regeneration invalidates only the opposite snapshot.

- [ ] **Step 1:** Add failing policy/contract tests.
- [ ] **Step 2:** Verify RED.
- [ ] **Step 3:** Wire a post-commit notification from existing drop/resize paths into runtime preflight.
- [ ] **Step 4:** Verify GREEN and round-trip regressions for 8x4 and 10x6.
- [ ] **Step 5:** Commit preflight integration.

### Task 6: Final verification

**Files:**
- Test: existing `src/test/java/com/hellovoid/liquiddock/*`

- [ ] **Step 1:** Run `./gradlew testDebugUnitTest --stacktrace` in CI.
- [ ] **Step 2:** Run `./gradlew assembleDebug --stacktrace` in CI.
- [ ] **Step 3:** Confirm no existing 8x4/10x6 rotation-direction tests regress.
- [ ] **Step 4:** Inspect final branch diff against `main` for unintended hook/database changes.
