# Orientation Layout Memory Design

## Goal

Add stable per-orientation desktop placement for LiquidDock custom grids so repeated rotations restore user-authored portrait and landscape layouts instead of repeatedly deriving one orientation from the other.

## Scope

- Applies only when LiquidDock custom home-grid mode is enabled.
- First implementation targets `8x4 <-> 4x8` and `10x6 <-> 6x10` profiles.
- Stock MIUI layout behavior remains untouched when custom-grid mode is disabled.
- Existing MIUI `LayoutTransformRuleGridChanged` ownership of native occupancy matrices is preserved.
- No MIUI database schema changes are introduced in this phase.

## Architecture

### 1. Per-orientation snapshots

A pure-Java model stores complete item placements keyed by a stable item id and orientation. A placement contains page/screen identity plus `cellX`, `cellY`, `spanX`, and `spanY`.

A snapshot is valid only when every item is in bounds and no two item rectangles overlap. Partial snapshots are never committed.

### 2. Memory-first rotation policy

On orientation change:

1. Capture the source orientation as a complete snapshot.
2. If a complete, compatible target-orientation snapshot exists, restore it.
3. Otherwise allow MIUI's native transform to build the target layout.
4. Validate the resulting target layout and persist it as the initial target snapshot.

After both orientations have a valid snapshot, rotation becomes restore-only for user-authored positions rather than repeated transform chaining.

### 3. First-layout planner

When a target orientation has no snapshot, a deterministic pure-Java planner may generate a complete target layout. Placement priority is:

1. Existing valid target memory positions.
2. Large items (`spanX * spanY >= 4`, including 4x2 widgets/folders).
3. Other multi-cell items.
4. 1x1 items.

For items without a remembered target position, try the nearest valid cell to the source item's normalized center. Ties are resolved row-major to keep the result deterministic. If every item cannot be placed, planning fails without producing a partial layout.

### 4. Other-orientation preflight

After a drag/drop or resize commits in the current orientation, validate the other-orientation snapshot against the updated item set. If it is no longer valid, try to regenerate the other orientation with the planner. If planning fails, invalidate only that target snapshot; do not reject the current user action in phase one.

### 5. Persistence

Phase one uses a sidecar store owned by LiquidDock. The persistence interface is separate from planner/model code so tests can use an in-memory implementation and runtime can use Android `SharedPreferences`.

## Safety invariants

- Never directly write MIUI's transposed occupancy arrays from LiquidDock.
- Never commit a partial planned page.
- Never silently clamp a multi-cell item into overlap.
- Existing valid target-orientation memory wins over aesthetic repacking.
- User-authored current-orientation placement is never changed by preflight failure.
- Custom-grid disabled means zero behavior change.
