# 307 Native Shell A/B Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run a minimal device A/B that preserves the original 307 Dock background as a visual shell above Prismal while keeping all vendor GPU blur disabled.

**Architecture:** Add an opt-out in `DockLiquidGlassView` for hiding its geometry source after capture. Enable that opt-out only in the 307 path. Move the Prismal host below the native background and revert the previous squircle-only experiment so shape is not a second variable. Vendor pass-window/region blur suppression remains unchanged.

**Tech Stack:** Android View hierarchy, Prismal `DockLiquidGlassView`, HyperOS Launcher hooks, GitHub Actions/Gradle.

## Global Constraints

- Work only on `fix/miuix307-drag-freeform-followup`.
- Do not restore vendor compositor/pass-window blur.
- Do not reconnect the full Dock customization hook block in this A/B.
- Do not add or run unit tests; user requested an agile device experiment only.
- Verification is production diff scope + `./gradlew assembleDebug --stacktrace`.

---

### Task 1: Preserve native shell after capture

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java`

**Interfaces:**
- Produces: `DockLiquidGlassView.setPreserveGeometrySourceVisuals(boolean preserve)`.
- Consumes: existing `geometrySource`, `nativeBackgroundHiddenByGlass`, and 307 vendor GPU blur suppressor.

- [ ] **Step 1: Add an opt-out for geometry-source hiding**

Add a default-false `preserveGeometrySourceVisuals` field and package-private setter. When enabled, force `geometrySource` alpha to 1 and clear the hidden-source latch.

- [ ] **Step 2: Gate `installCapture()` source hiding**

Change the first-frame alpha-zero behavior so it runs only when `preserveGeometrySourceVisuals` is false. In preserve mode, keep alpha 1 and the hidden latch false.

- [ ] **Step 3: Enable preserve mode only for 307**

After creating/configuring the 307 `DockLiquidGlassView`, call `glass.setPreserveGeometrySourceVisuals(true)`.

- [ ] **Step 4: Put Prismal below the native shell**

Change the host insertion index from immediately after `dockBg` to the current `dockBg` index, so adding the host pushes the vendor background above it.

- [ ] **Step 5: Revert the squircle-only experiment**

Restore the 307 factory and host geometry booleans from `true` to `false` in the three experimental call sites.

- [ ] **Step 6: Verify source scope**

Confirm the production diff from the previous stable ownership build changes only `DockLiquidGlassView.java` and `MiuixGlassHook.java`, with no change to vendor GPU blur suppression.

- [ ] **Step 7: Build without tests**

Run `./gradlew assembleDebug --stacktrace`. Expected: `BUILD SUCCESSFUL`. Do not invoke any unit-test task.

- [ ] **Step 8: Deliver APK for device A/B**

The device check is: stroke/shadow/native rounded visual returns while Floating Dock region blur stays zero/absent. If successful, a later change can reconnect the full 307 Dock customization hooks.