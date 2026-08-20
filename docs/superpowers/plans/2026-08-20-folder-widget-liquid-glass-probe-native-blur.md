# Folder / Widget Liquid Glass Probe + Native Blur Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement steps 1 and 2 of `docs/FOLDER_WIDGET_LIQUID_GLASS_SPIKE.md`: read-only runtime probes for large folders and both widget hosts, plus a large-folder `BackgroundBlurDrawable` prototype.

**Architecture:** Keep probe and prototype in separate classes. The probe may only observe/log class, parent, screen bounds, radius and attach/detach. The prototype uses Launcher `ViewRootImpl.createBackgroundBlurDrawable()` and never creates a PassBlur producer, so it cannot compete with the current Dock root-scoped producer.

**Tech Stack:** Java 17, Android View APIs, reflection, libxposed API 101, JUnit source-contract tests.

**Spec:** `docs/FOLDER_WIDGET_LIQUID_GLASS_SPIKE.md`

## Global Constraints

- Work only on `explore/folder-widget-liquid-glass`.
- Do not merge to `main`.
- Probe code must not mutate visual state.
- Prototype is enabled only while Liquid Glass is enabled.
- Do not add a second `SetPassBlurSurface`/PassBlur producer.
- Cover `LauncherAppWidgetHostView` and `MaMlHostView` in probes.
- Large-folder prototype must restore the stock image while editing/opening a folder and on prototype failure.

---

### Task 1: Read-only folder/widget probe

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/FolderWidgetGlassProbe.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MainHook.java`
- Test: `src/test/java/com/hellovoid/liquiddock/FolderWidgetGlassProbeContractTest.java`

**Interfaces:**
- Produces: `FolderWidgetGlassProbe.install(ClassLoader)`.

- [ ] Write a failing source-contract test requiring supported folder/widget class names, attach/detach logging, screen bounds/radius logging, and no visual mutation calls.
- [ ] Verify RED through CI.
- [ ] Implement minimal probe hooks and call the installer after the master-switch gate.
- [ ] Verify GREEN.

### Task 2: Large-folder native blur prototype

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/LargeFolderNativeBlurPrototype.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MainHook.java`
- Test: `src/test/java/com/hellovoid/liquiddock/LargeFolderNativeBlurPrototypeContractTest.java`

**Interfaces:**
- Produces: `LargeFolderNativeBlurPrototype.install(ClassLoader)`.

- [ ] Write a failing source-contract test requiring `mIconImageView`, an index-0 background layer, `createBackgroundBlurDrawable`, blur/corner radius setup, and edit/open/close lifecycle hooks.
- [ ] Verify RED through CI.
- [ ] Implement the minimal prototype, preserving the stock image as fallback.
- [ ] Verify all tests and `assembleDebug` pass.
