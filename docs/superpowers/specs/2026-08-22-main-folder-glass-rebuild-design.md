# Main-based Folder Liquid Glass Rebuild Design

Date: 2026-08-22
Base: `main` at `a9054bda471218f8a3037fd7ddf8972287d7120a`
Target branch: `feat/main-folder-glass-rebuild`

## Goal

Rebuild folder liquid glass from the current `main` branch instead of continuing the existing shared-launcher-glass experimental branch. Preserve `main`'s Dock and workstation behavior exactly, while reintroducing only the requested Launcher-side functionality and UI cleanup.

## Scope

This rebuild contains three user-visible changes:

1. Folder liquid glass using wallpaper-only GPU sampling.
2. Highlight component switches that apply only to Launcher glass surfaces: folders now, and widgets later when widget liquid glass is implemented.
3. Settings cleanup:
   - move Dock stroke corner-radius offset from the Dock page to the Stroke page;
   - remove implementation-oriented or unrelated wording from the Liquid Glass page.

Widget liquid glass itself is explicitly out of scope for this rebuild. The architecture should allow it to reuse the same Launcher-surface highlight controls later without introducing a separate Prismal optical model.

## Baseline and isolation rule

The branch starts directly from `main` commit `a9054bda471218f8a3037fd7ddf8972287d7120a`.

The existing `feat/shared-launcher-glass-session` branch is reference material only. No feature commit from that branch is cherry-picked wholesale.

The following `main` behavior is a protected boundary and must not be altered by this rebuild:

- ordinary Dock background ownership;
- workstation/Laptop Dock background ownership;
- Dock size, blur, bottom-offset, and resize behavior;
- workstation transition behavior;
- Dock Prismal optical appearance and defaults;
- Dock material suppression/attachment rules.

In particular, the rebuild must not import the experimental workstation ownership transform or any Launcher/Dock shared material-ownership mechanism from the old branch.

## Folder liquid glass architecture

### Rendering model

Folders use the existing Prismal optical model already used by Dock. There is no separate folder-specific or widget-specific optical model.

The distinction is at the renderer/surface configuration level, not the optical model level:

- Dock continues using its current renderer configuration unchanged.
- Folder surfaces instantiate/use Prismal with Launcher-surface component controls.
- Future widget liquid glass will use the same Prismal model and may reuse the same Launcher-surface component-control path.

### Background source

Folder glass samples only the launcher wallpaper/background source through the GPU path.

It must not:

- capture the folder itself;
- capture folder icons;
- recursively sample its own rendered output;
- use screen recording;
- use SurfaceFlinger bitmap readback as the folder background source.

The implementation should reuse only the proven wallpaper-only behavior from the experimental branch, rewritten cleanly against `main` rather than importing the old shared-glass ownership stack.

### Lifecycle requirements

The folder renderer must correctly handle:

- initial launcher startup;
- folder creation/attachment;
- folder detach/re-attach;
- wallpaper/background refresh;
- launcher restart;
- material refresh without continuous self-capture.

The previously observed startup case where the folder initially shows the stock/default background until the user manually drags something must be covered by a regression test or deterministic startup-refresh contract.

## Highlight component controls

### Scope

Highlight component controls are Launcher-surface controls.

Current consumer:

- folder liquid glass.

Future consumer:

- widget liquid glass, once implemented.

Non-consumer:

- Dock.

Changing these controls must never change the Dock's component state, shader composition, or default appearance.

### Components

Preserve the existing component granularity where supported by the current Prismal shader path, including:

- Sky Haze
- Specular
- Lit Rim
- Opposite Rim
- Corner Rim
- Face Sheen
- Plain Highlight
- Caustics
- Press Glow
- Compact Safe Highlight

The exact shader implementation may be simplified if some components are already combined in the current `main` Prismal code, but no control should silently affect Dock.

### Configuration model

Do not retain the old dual global profile structure with `LAUNCHER_*` and `DOCK_*` preference groups.

Instead:

- keep only Launcher-surface component preferences needed for folders/future widgets;
- pass or bind these controls at the Launcher renderer/surface instance boundary;
- leave Dock on its current implicit/default component behavior.

The key contract is renderer isolation: a Launcher preference change cannot propagate into an already-existing Dock renderer merely because both reuse the same Prismal optical model.

## Settings UI

### Dock page

Remove the external stroke corner-radius offset (`ConfigSchema.Dock.CORNER_OFFSET`) from the Dock page.

Keep the internal blur/background corner offset (`ConfigSchema.Dock.BLUR_CORNER_OFFSET`) on the Dock page because it belongs to the blur/background geometry rather than stroke geometry.

### Stroke page

Add the external stroke corner-radius offset to the Stroke page's geometry section.

Its storage key and runtime semantics remain unchanged; only settings-page ownership changes.

### Liquid Glass page

The page should describe user-facing function and optical properties, not internal implementation details.

Remove or rewrite text such as:

- `Launcher Prismal`;
- `PassBlur -> OES -> Prismal`;
- `zero-copy`;
- FBO/backend implementation descriptions;
- unnecessary `Prismal ·` prefixes in option titles where the user-facing property is clear without them.

Preferred wording describes properties directly, for example:

- Blur
- Glass thickness
- Refractive index
- Refraction strength
- Chromatic dispersion
- Tint
- Highlight width/intensity
- Rim light
- Caustics
- Background sampling range

Implementation-specific debug settings may remain only if they are clearly labeled as debug controls and still relevant.

English and Simplified Chinese resources must remain synchronized; new visible strings should use resources rather than new hard-coded Chinese UI text.

## Explicit exclusions

This rebuild does not include:

- widget liquid glass rendering;
- a widget-only Prismal model;
- a widget-only settings page;
- Dock component switches;
- changes to Dock Prismal optical defaults;
- workstation background fixes from the experimental branch;
- screen recording or CPU bitmap capture for folder glass;
- unrelated refactoring of grid, divider, workstation, Dock geometry, or animation code.

## Migration strategy

Use semantic reconstruction from `main`, not broad cherry-picks.

Implementation should proceed in independently verifiable commits:

1. Baseline verification on untouched `main`-derived branch.
2. Folder wallpaper-only liquid-glass core.
3. Folder lifecycle/startup/material-refresh fixes.
4. Launcher-surface highlight component controls with Dock isolation tests.
5. Settings ownership and wording cleanup.
6. Full regression verification and APK build.

Each commit should be buildable and testable so a device regression can be bisected to the first functional step that introduced it.

## Test contracts

At minimum, add or preserve automated contracts for:

- folder background source is wallpaper-only and does not recursively capture folder/icon output;
- startup path installs/refreshes folder material without a user drag interaction;
- lifecycle detach/rebind does not submit work to a dead rendering thread;
- Launcher-surface component preferences are not read by the Dock renderer path;
- Dock defaults are unchanged when Launcher component preferences are toggled;
- `CORNER_OFFSET` is exposed on Stroke rather than Dock settings;
- Liquid Glass page does not expose the removed implementation-oriented descriptions;
- existing `main` workstation and Dock tests continue to pass.

## Device acceptance checks

Automated CI is necessary but not sufficient for visual acceptance. Final device validation should include:

1. Boot/restart launcher with folder liquid glass enabled and confirm the folder background is correct immediately.
2. Open/close folders repeatedly and confirm no white/self-captured/recursive background appears.
3. Change folder highlight components, restart launcher if required, and confirm only folder appearance changes.
4. Enable Dock size, blur, and liquid-glass master controls, then enter/leave workstation mode repeatedly and confirm workstation background remains present.
5. Confirm Dock appearance is unchanged by folder highlight component settings.
6. Confirm the stroke corner-radius setting is present under Stroke and absent from the Dock page.
7. Confirm the Liquid Glass page uses only user-facing functional/property descriptions.
