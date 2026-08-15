# Capture Rebuild from 8ee84ed

## Goal

Rebuild LiquidDock capture behavior from commit `8ee84ed61e74b3199f7e4d6fb1dd30cfdc2c3294`, which is device-verified to avoid capturing Floating Dock icons. Preserve its capture timing and failure behavior as the baseline, then reintroduce later improvements only when they do not depend on the `prearm RECENTS -> immediate FULL_DISPLAY` behavior introduced by `963b910`.

## Device evidence

The following commits were tested on the target HyperOS device:

- `2d5c4ef`: normal.
- `357b35f`: normal.
- `04ec679`: normal.
- `8ee84ed`: normal with respect to Dock-icon contamination; known defect is that Recents does not switch to live capture after the haptic/transition boundary.
- `963b910`: Dock-icon contamination is present.

Therefore the fullscreen capture + Floating Dock exclusion mechanism is not intrinsically broken on this device. The first known regression is `963b910`, where Recents prearm was promoted to the live FULL_DISPLAY source immediately.

## Baseline invariants

The first implementation stage must preserve these `8ee84ed` properties:

1. An unconfirmed Recents prearm/haptic event must not issue a mode-1 FULL_DISPLAY request.
2. Haptic and upward-distance signals are hints only; they cannot by themselves make a live capture source authoritative.
3. APP remains the established FULL_DISPLAY path with Floating Dock exclusion.
4. HOME and ALL_APPS remain wallpaper-backed.
5. Capture failures must not enter the later `DockExcludeRecovery` `NAME_ONLY/SUSPENDED` state machine.
6. Do not introduce the later foreground-ownership/race machinery until the new baseline is device-verified.

## Recents repair

`8ee84ed` has one known functional defect: after the Recents haptic/prearm boundary, the backdrop can remain wallpaper-backed instead of becoming live task-card content.

Fix this with two phases rather than mapping every `RECENTS` state to FULL_DISPLAY:

### Phase A: prearm

Signals such as:

- launcher Recents haptic,
- upward-distance threshold,
- speculative gesture target,

may set Recents intent/cadence state, but the capture source remains WALLPAPER.

### Phase B: confirmed Recents

A Recents live source becomes eligible only after a concrete stock/visual confirmation, initially `isRecentsVisible() == true` using the already-established Recents view observation.

At the false -> true boundary:

- mark the source dirty;
- reset the capture-rate deadline for one immediate refresh;
- request a fresh capture;
- resolve the source as FULL_DISPLAY;
- use the same Floating Dock exclusion path already proven by the pre-regression commits.

This must not be implemented by a fixed delay after haptic.

## Source contract

Initial rebuilt behavior:

- `HOME` -> WALLPAPER
- `ALL_APPS` -> WALLPAPER
- `APP` -> FULL_DISPLAY
- `RECENTS` while only prearmed/unconfirmed -> WALLPAPER
- `RECENTS` when actually visible/confirmed -> FULL_DISPLAY

The confirmation belongs to runtime capture-source resolution, not to the gesture constructor itself.

## Later changes: migration policy

Later commits are not cherry-picked wholesale. Each feature is reimplemented or cherry-picked only after checking that it does not restore the bad source timing.

### Safe early candidate

`4fc9875` (`refactor: collapse glass composition to two views`)

Reason: this changes visual composition ownership (Host + Glass, highlight/stroke placement) rather than deciding when SurfaceFlinger capture starts. It may be ported after the capture-only baseline is verified.

### Candidates to evaluate separately after baseline verification

`92f092` (`refactor: extract launcher scene hooks`)

- Structural extraction only in intent.
- May be ported if diff review confirms no behavioral dependency on later source-domain assumptions.

`dc131f7` (`preserve APP live capture across transient launcher focus`)

- The top-task confirmation idea may still be useful for APP/HOME focus noise.
- Do not port its whole state flow initially.
- If needed, reintroduce as a narrow focus guard after capture timing is stable.

`e0a89b` (`keep unconfirmed HOME gestures in live capture`)

- Potentially useful for APP -> HOME gesture races.
- Evaluate only after the baseline passes device tests.
- Must not change Recents prearm source authority.

`cb6b576` (`switch recents home gesture to wallpaper`)

- Re-evaluate after confirmed-Recents live capture exists.
- Any fast Recents -> HOME handoff must require a confirmed HOME boundary and must not make speculative gesture construction authoritative.

### Do not port as-is

`963b910`

- First device-confirmed bad implementation.
- Its `RECENTS -> FULL_DISPLAY` mapping plus immediate haptic capture is explicitly rejected.

`a803f81`, `5f3b50f`, `56bf9ce`, `fc1694f`

- These progressively add interaction locks, source-domain barriers, foreground ownership, and race convergence around the APP/RECENTS shared live-domain model.
- Their individual ideas can be reconsidered later, but their state machines are not baseline material.

`4ff67bd`

- HOME settle/foreground authority logic is too coupled to the later race model for the first rebuild stage.
- The app-layer resolver removal can be reconsidered separately if needed.

`6c48c21`

- Generation-aware EINVAL recovery and capture suspension are rejected for the baseline because device testing showed they amplify failures and can make the backdrop disappear.

## Implementation stages

### Stage 1: capture-only rebuild

Starting branch: `test/capture-rebuild-8ee84ed` at exact `8ee84ed` source.

Make only the confirmed-Recents source change described above. Do not modify visual composition, foreground authority, HOME settle, or Dock exclusion recovery in the same commit.

Success criteria on device:

- APP Dock pull does not capture Dock icons.
- Haptic alone does not cause Dock-icon contamination.
- Once Recents is actually visible, task-card content becomes live behind the Dock.
- Returning/cancelling before confirmed Recents does not issue an unsafe early mode-1 request.
- No background disappearance or capture suspension behavior is introduced.

### Stage 2: visual composition migration

Port the 2-View composition behavior from `4fc9875` without changing capture-source logic.

Success criteria:

- Stage-1 capture behavior remains unchanged.
- ADVANCED highlight/stroke stay outside the self-blurred Glass RenderNode.
- No Dock-icon contamination regression.

### Stage 3: narrow race fixes, one at a time

Only after Stages 1 and 2 are device-stable, evaluate APP/HOME focus and gesture fixes individually. Each migrated behavior gets its own test commit and device A/B result before the next one is introduced.

## Explicit non-goals

The rebuild does not initially:

- replace capture with native `setBackgroundBlur`;
- redesign the entire scene authority model;
- restore LOCAL_LAYER/captureLayers;
- add EINVAL-specific suspension;
- add a new generalized retry circuit;
- cherry-pick all post-`8ee84ed` commits.

Those are separate experiments after a stable baseline is restored.
