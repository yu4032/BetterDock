# Compose Grid Profile UI Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose the production 8×4/10×6 grid-profile selector in the actual Compose settings UI and remove the obsolete XML Preference UI so there is one settings surface.

**Architecture:** `ComposeSettingsActivity` remains the only launcher activity. `SettingsActivity` remains only as a shared host for preference migration, import/export, and launcher restart; all legacy Fragment/Preference UI responsibilities are removed. The Compose `GridPage` uses the existing `StringDropdown` and `GridProfileConfig` key, so the same persisted value already consumed by `ModuleMain` is edited by the visible GUI.

**Tech Stack:** Android, Kotlin Compose, Miuix KMP, Java shared settings host, JUnit contract tests.

**Spec:** User-approved requirement in the current conversation: fix the Compose GUI and delete the old configuration UI to prevent future confusion.

## Global Constraints

- Keep 8×4 / 4×8 as the default profile.
- Offer 10×6 / 6×10 as the second profile.
- Do not change runtime grid hooks or occupancy behavior.
- Preserve config import/export and launcher restart.
- Delete obsolete XML Preference UI resources and Fragment code.
- Do not retain implementation-plan or diagnostic artifacts in the final merge.

---

### Task 1: Lock the real Compose UI contract

**Files:**
- Modify: `src/test/java/com/hellovoid/liquiddock/GridProfileSelectionContractTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/LegacySettingsUiRemovalContractTest.java`

**Interfaces:**
- Consumes: `GridProfileConfig.PROFILE_KEY`, `GridProfileConfig.DEFAULT_PROFILE`, `StringDropdown(...)`.
- Produces: failing source contracts proving the visible Compose UI is missing profile selection and legacy UI still exists.

- [ ] **Step 1: Write the failing tests**

Require `ComposeSettingsActivity.kt` to import/use `GridProfileConfig`, render `StringDropdown` for `PROFILE_KEY`, and use both profile arrays. Require `SettingsActivity.java` to contain no `PreferenceFragmentCompat`, `SettingsFragment`, `useLegacyPreferenceUi`, `setContentView`, or `R.xml.preferences`, and require `preferences.xml`/`activity_settings.xml` to be absent.

- [ ] **Step 2: Run `./gradlew testDebugUnitTest --stacktrace`**

Expected: only the new/updated UI contracts fail for the known missing Compose selector and existing legacy UI.

### Task 2: Add the production Compose selector

**Files:**
- Modify: `src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt`
- Modify: `src/main/res/values/strings.xml`
- Keep: `src/main/res/values/arrays.xml`

**Interfaces:**
- Consumes: `GridProfileConfig.PROFILE_KEY`, `GridProfileConfig.DEFAULT_PROFILE`, `StringDropdown(...)`, `home_grid_profile_entries`, `home_grid_profile_values`.
- Produces: one visible profile row in `GridPage`, enabled only when the custom grid is enabled.

- [ ] **Step 1: Import `GridProfileConfig` and `stringArrayResource`**

- [ ] **Step 2: Build profile options by zipping localized display entries with persisted values**

- [ ] **Step 3: Render `StringDropdown` immediately below the custom-grid switch**

Use the existing preference-write path so `LiquidDockApp` mirrors the same `grid_profile` value to API101 Remote Preferences.

- [ ] **Step 4: Update visible copy**

Rename the custom-grid switch text/summary and home-card summary so neither falsely describes the feature as 8×4-only.

### Task 3: Remove the obsolete Preference UI

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/SettingsActivity.java`
- Modify: `src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt`
- Delete: `src/main/res/xml/preferences.xml`
- Delete: `src/main/res/layout/activity_settings.xml`

**Interfaces:**
- Preserves: `launchExport()`, `launchImport()`, `restartLauncher()`, preference migration, import/export codec behavior.
- Removes: Fragment/Preference rendering, legacy toolbar/layout, UI-mode branch.

- [ ] **Step 1: Make `SettingsActivity.onCreate` lifecycle-only**

Run preference migration and status-bar appearance setup, but do not inflate a layout or install a Fragment.

- [ ] **Step 2: Delete `SettingsFragment` and Preference-specific imports**

- [ ] **Step 3: Remove `ComposeSettingsActivity.useLegacyPreferenceUi()` override**

- [ ] **Step 4: Delete the two legacy UI XML files**

### Task 4: Verify and merge cleanly

**Files:**
- Delete before merge: `docs/superpowers/plans/2026-08-21-compose-grid-profile-ui.md`

**Interfaces:**
- Produces: clean production diff with a single settings UI.

- [ ] **Step 1: Run full unit tests**

Command: `./gradlew testDebugUnitTest --stacktrace`
Expected: success.

- [ ] **Step 2: Run debug assembly**

Command: `./gradlew assembleDebug --stacktrace`
Expected: success and APK artifact.

- [ ] **Step 3: Inspect final diff**

Confirm no references remain to `PreferenceFragmentCompat`, `SettingsFragment`, `R.xml.preferences`, `activity_settings`, or `useLegacyPreferenceUi`.

- [ ] **Step 4: Delete this plan document, run final CI again, then squash-merge only after the clean HEAD passes.**
