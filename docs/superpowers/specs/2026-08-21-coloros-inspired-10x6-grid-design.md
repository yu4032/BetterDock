# ColorOS-Inspired 10x6 Home Grid Experimental Design

## Goal

Add an experimental 10x6 landscape / 6x10 portrait home-grid mode to LiquidDock while preserving MIUI ownership of workspace persistence, occupancy, vacancy search, drag/drop, and widget hosting. Use the ColorOS launcher analysis as a behavioral reference for rotation stability rather than copying its implementation.

## Evidence and design premise

The ColorOS tablet launcher uses separate wide and long device profiles and maintains two orientation-specific positions for each desktop item (`cellX/cellY` and `anotherCellX/anotherCellY`). Before a rotation it validates the cached alternate coordinates against target bounds and `GridOccupancy`; if invalid it performs deterministic occupancy-aware rearrangement and then synchronizes the alternate-position cache. This is materially stronger than a pure coordinate transpose.

LiquidDock's current 8x4 implementation already owns only grid geometry and MIUI rotation metadata. The archived 10x6 experiment showed that extending MIUI's transform rule alone is insufficient for 4x2 widgets: geometrically valid landscape positions can map outside the portrait grid, and restricting 4x2 anchors avoids the crash but sacrifices placement freedom.

## Experimental architecture

### 1. Profile-based grid counts

Introduce a reusable home-grid profile model with three modes: stock, 8x4, and experimental 10x6. In 10x6 mode the active count is 10x6 in landscape and 6x10 in portrait. Existing 8x4 behavior must remain unchanged.

### 2. Orientation-position shadow state

For 10x6 only, maintain a weak in-memory shadow position record keyed by stable launcher item identity. Each record stores landscape and portrait `(screenId, cellX, cellY, spanX, spanY)` separately.

The shadow state is advisory, not an alternate launcher database. LiquidDock never writes its own occupancy matrix and never replaces MIUI's database owner. It is populated from currently bound `ItemInfo` objects and refreshed after successful moves/layout commits.

### 3. Rotation planning

On an orientation change:

1. Prefer an already-known target-orientation position for an item if it remains within bounds and does not overlap previously accepted target items.
2. Otherwise derive a candidate by normalized transpose between 10x6 and 6x10.
3. Validate the full rectangle, not just the anchor.
4. If the candidate conflicts, search the nearest target cell deterministically with `GridOccupancy` semantics. Preserve screen and relative order where possible.
5. If the current page cannot fit an item, do not silently overlap or place it out of bounds. Leave MIUI's original placement path in control and log the unresolved item for device diagnostics.

A 4x2 widget therefore keeps free landscape placement whenever a valid portrait counterpart can be found; it is not restricted to two hard-coded anchors.

### 4. Span policy

Do not blindly rotate widget dimensions. MIUI item `spanX/spanY` remain the launcher's authoritative widget allocation unless target-grid bounds require the existing native rotation path to expose a swapped span. The planner accepts explicit source and target spans and validates whichever span MIUI presents for the target orientation.

This avoids assuming that every Android widget is semantically rotatable from 4x2 to 2x4.

### 5. Hook boundary

Keep `HomeGridHook` responsible for geometry and refresh. Add pure-Java policy classes for profile selection and rotation planning. Hook only narrow lifecycle points needed to observe item positions and apply a validated target position before MIUI commits/binds it. Do not hook or replace `GridOccupancy` internals, database writes, or vacancy search globally.

### 6. Safety and isolation

The feature is experimental and lives only on `experiment/coloros-10x6-layout`. Stock and 8x4 behavior are regression baselines. Workstation All Apps and Dock/Prismal code are out of scope.

## Test requirements

- 10x6 landscape and 6x10 portrait count policy.
- Every planned rectangle is in bounds.
- No two planned rectangles overlap.
- Repeated landscape -> portrait -> landscape returns items to remembered orientation-specific positions when those positions remain available.
- 4x2 widgets placed at multiple landscape anchors receive valid portrait positions without a hard-coded anchor whitelist.
- Conflict fallback is deterministic.
- Icons and widgets share one occupancy model, so neither can overlap the other.
- Existing 8x4 tests remain green.
- Standard unit tests and `assembleDebug` must pass before an APK is considered device-testable.
