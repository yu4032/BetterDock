# HOME/APP Ownership Convergence — CI Evidence

**Date:** 2026-08-16  
**Branch:** `fix/home-app-ownership-convergence`  
**Implementation checkpoint before CI enablement:** `d241753eab191d847182e70c0fa475e67c40f388`  
**CI-validated HEAD:** `86a4ee0d63cfaa70d7b21a18e46d76eb73de798a`  
**GitHub Actions run:** `31948422333`  
**Job:** `95168017682`

## Why this run exists

The existing `api101-build.yml` workflow did not include the isolated ownership-convergence branch in its push filter. Commit `86a4ee0d63cfaa70d7b21a18e46d76eb73de798a` adds only `fix/home-app-ownership-convergence` to that CI branch list so the exact branch can preserve full Gradle evidence. No production Java/Kotlin implementation changed between `d241753eab191d847182e70c0fa475e67c40f388` and the CI-validated HEAD.

## Unit-test gate

Command:

```text
./gradlew testDebugUnitTest --stacktrace
```

Result:

```text
> Task :testDebugUnitTest
BUILD SUCCESSFUL in 1m 54s
24 actionable tasks: 24 executed
```

**Status: passed.**

## Debug build gate

Command:

```text
./gradlew assembleDebug --stacktrace
```

Result:

```text
> Task :assembleDebug
BUILD SUCCESSFUL in 1m 2s
35 actionable tasks: 16 executed, 19 up-to-date
```

**Status: passed.**

## APK artifact

GitHub Actions uploaded exactly one file under artifact name:

```text
LiquidDock-api101-debug
```

Artifact metadata:

- Artifact ID: `9263984261`
- Archive size: `3659820` bytes
- Archive SHA-256: `761ef280808083de4a30df3c94899854e0cfe2d67c22b397f18738f7e2f7035e`
- Retention expiry: `2026-08-23T12:59:41Z`

The upload step completed successfully.

## Non-blocking warnings observed

The build emitted an existing Kotlin warning in `ComposeSettingsActivity.kt` about a redundant conversion call and generic deprecated-API compiler notes. GitHub Actions also warned that several v4 actions are being forced from deprecated Node 20 to Node 24, and specifically recommends migrating `actions/setup-java@v4` to v5. None of these warnings failed either Gradle gate.

## Gates closed by this record

This run closes the two Gradle evidence gaps listed in the pre-wrap validation record:

1. fresh `testDebugUnitTest` output;
2. fresh `assembleDebug` output for the branch implementation checkpoint.

## Gates still requiring target-device evidence

This CI run cannot replace visual/device validation. The remaining acceptance gates are:

1. normal Recents/Overview enter and exit;
2. normal All Apps enter and exit;
3. APP + freeform visual confirmation that the freeform task remains excluded from the LiquidDock backdrop.

The Android Local Builder connector was unavailable during this CI session, so no new APK installation or device interaction is claimed here.
