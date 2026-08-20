# ColorOS-Inspired 10x6 Grid Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Build an experimental 10x6 landscape / 6x10 portrait grid with stable orientation-specific placement inspired by ColorOS.

**Architecture:** Reuse the archived profile/count overlay but replace hard-coded 4x2 placement restrictions with a pure-Java target-orientation planner. Keep MIUI as the authoritative persistence and occupancy owner; the experimental planner supplies only validated, deterministic candidate positions.

**Tech Stack:** Java, libxposed API 101, Android Launcher private APIs, JUnit.

**Spec:** `docs/superpowers/specs/2026-08-21-coloros-inspired-10x6-grid-design.md`

## Global Constraints

- Work only on `experiment/coloros-10x6-layout`.
- Do not modify Dock/Prismal/workstation rendering behavior.
- Do not replace MIUI database or GridOccupancy ownership.
- No fixed 4x2 anchor whitelist.
- Every target rectangle must be in bounds and non-overlapping.

---

### Task 1: Restore profile/count primitives

**Files:**
- Create `src/main/java/com/hellovoid/liquiddock/HomeGridProfile.java`
- Create `src/main/java/com/hellovoid/liquiddock/HomeGridCountPolicy.java`
- Test `src/test/java/com/hellovoid/liquiddock/HomeGridProfileTest.java`
- Test `src/test/java/com/hellovoid/liquiddock/HomeGridCountPolicyTest.java`

- [ ] Add RED tests for 10x6 landscape, 6x10 portrait, and named GridConfig rewriting.
- [ ] Verify tests fail because profile classes are absent.
- [ ] Add the minimal profile/count implementation adapted from the archived experiment.
- [ ] Verify profile tests pass.

### Task 2: Add orientation-aware placement planner

**Files:**
- Create `src/main/java/com/hellovoid/liquiddock/HomeGridRotationPlanner.java`
- Test `src/test/java/com/hellovoid/liquiddock/HomeGridRotationPlannerTest.java`

**Interface:**
`HomeGridRotationPlanner.plan(int sourceColumns, int sourceRows, int targetColumns, int targetRows, List<Item> items, Map<Long, Position> rememberedTarget)` returns one deterministic target `Position` per item or marks an item unresolved.

- [ ] Add RED tests proving arbitrary 4x2 landscape anchors can rotate without overlap/out-of-bounds.
- [ ] Add RED round-trip test proving remembered landscape positions are restored after portrait rotation.
- [ ] Add RED conflict test proving icons/widgets share one occupancy model and fallback is deterministic.
- [ ] Implement normalized transpose candidate selection, remembered-target preference, rectangle validation, and nearest-cell fallback.
- [ ] Verify planner tests pass.

### Task 3: Restore narrow 10x6 runtime overlay

**Files:**
- Create `src/main/java/com/hellovoid/liquiddock/HomeGridProfileOverlayHook.java`
- Modify `LiquidDockConfig.java`
- Modify `ModuleMain.java`
- Modify `MainHook.java`
- Modify config schema/settings only as required to select `10x6`.

- [ ] Add contract tests first for profile wiring and private-API preflight.
- [ ] Restore only count/profile overlay behavior from the archive.
- [ ] Do not restore `WorkspaceDropPolicy` fixed-anchor logic.
- [ ] Verify existing 8x4 behavior stays unchanged.

### Task 4: Experimental rotation bridge

**Files:**
- Create `HomeGridRotationBridge.java`
- Modify `HomeGridHook.java` only at narrow rotation/bind lifecycle points.

- [ ] Add contract tests for observation/apply boundaries.
- [ ] Capture stable item identity and per-orientation positions from bound ItemInfo objects.
- [ ] On rotation, invoke the pure planner and apply only validated target coordinates before MIUI binds/commits the target layout.
- [ ] Fail closed to MIUI native behavior for unresolved items.

### Task 5: Verification

- [ ] Run full unit test suite.
- [ ] Run `assembleDebug`.
- [ ] Create a draft PR only to trigger standard API101 CI.
- [ ] Device-test repeated 10x6 <-> 6x10 rotation with icons and 4x2 widgets at multiple anchors before any merge discussion.
