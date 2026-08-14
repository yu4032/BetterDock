# Phase 1 Configuration Convergence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make LiquidDock configuration schema-driven and side-effect-free while preserving every existing preference key, JSON field, default/preset behavior, migration, and runtime behavior, except for the approved fix that `grid_widget_adaptation` must round-trip through export/import.

**Architecture:** Introduce a small pure-Java `config` package containing typed key metadata and a map-based codec, then adapt the existing Android settings activity to it. Preserve context-specific legacy fallbacks explicitly rather than collapsing them into one numeric default: current UI/storage defaults, runtime fallback defaults, and export defaults are allowed to differ when the existing code already differs. Move migration and preset ownership out of `SettingsActivity`/Compose without changing their algorithms or values. `LiquidDockConfig` remains the immutable runtime snapshot and becomes side-effect-free; the existing widget enable side effect is temporarily moved to the composition/install path until Phase 2 removes the global flag entirely.

**Tech Stack:** Java 17, Kotlin 2.4.0, Android SharedPreferences, Compose Miuix, org.json at Android boundary only, JUnit 4, Gradle/AGP 9.3.0, libxposed API 101.

## Global Constraints

- Work only on `refactor/modular-architecture`, based on `api101-migration` commit `e2c21a59de3ba6a8aae5277422e601abb1438292`.
- Existing SharedPreferences keys are compatibility API and must not be renamed.
- Existing JSON field names and `_format=liquiddock-settings`, `_version=2` remain unchanged.
- Existing JSON remains importable; existing export behavior remains unchanged except `grid_widget_adaptation` is added to export/import.
- Existing current UI defaults, legacy migration fallbacks, runtime fallbacks, presets, dp/tenths storage, and clamping semantics remain unchanged.
- Do not reinterpret historical raw-pixel defaults as current dp defaults. Preserve each context explicitly in schema metadata or compatibility code.
- `LiquidDockConfig.load()` must not mutate widget/grid/glass state when this phase is complete.
- Do not change widget geometry, rotation, occupancy, workstation behavior, Dock rendering, or Liquid Glass algorithms in Phase 1.
- Run `./gradlew testDebugUnitTest` and `./gradlew assembleDebug` at every task checkpoint. Do not proceed on failure.
- Each task is independently revertible and ends with a focused commit.

---

## File Structure Locked For This Phase

New files:

- `src/main/java/com/hellovoid/liquiddock/config/ConfigKey.java` — immutable metadata for one preference key.
- `src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java` — authoritative key registry and current storage/UI/import/export metadata.
- `src/main/java/com/hellovoid/liquiddock/config/ConfigCodec.java` — pure map-to-map export/import transformation; no Android or `org.json` dependency.
- `src/main/java/com/hellovoid/liquiddock/config/ConfigMigration.java` — owner of the existing app-side SharedPreferences migration sequence.
- `src/main/java/com/hellovoid/liquiddock/config/PresetManager.java` — owner of current default preset and legacy iPad preset writes; values unchanged.
- `src/test/java/com/hellovoid/liquiddock/config/ConfigSchemaTest.java` — uniqueness/default/range/schema regression tests.
- `src/test/java/com/hellovoid/liquiddock/config/ConfigCodecTest.java` — map-level export/import, dp-tenths, legacy alias, and widget switch regression tests.
- `src/test/java/com/hellovoid/liquiddock/config/ConfigPresetTest.java` — pure preset-value regression snapshot where Android resource probing is not involved.

Modified files:

- `src/main/java/com/hellovoid/liquiddock/SettingsActivity.java` — delegate migration and codec work to the new config package; keep ActivityResult/UI/file I/O and launcher restart here.
- `src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt` — reference schema key/default/range metadata and delegate default preset application.
- `src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java` — use schema key names/current metadata where safe and remove cross-module mutation.
- `src/main/java/com/hellovoid/liquiddock/MainHook.java` — temporary explicit widget adaptation gate after config load, preserving current behavior until Phase 2.
- `ARCHITECTURE.md` — document schema/config ownership after Phase 1 is verified.

Do not move `LiquidDockConfig.java` to a new package in this phase; package movement would create unnecessary call-site churn before module extraction.

---

### Task 1: Establish the Typed Schema Contract

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/config/ConfigKey.java`
- Create: `src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java`
- Create: `src/test/java/com/hellovoid/liquiddock/config/ConfigSchemaTest.java`

**Interfaces:**
- Produces: `ConfigKey<T>` with `name()`, `type()`, `uiDefault()`, `runtimeFallback()`, `exportDefault()`, `minInt()`, `maxInt()`, `storageMode()`, `exportMode()`.
- Produces: `ConfigSchema.all()` and named groups `ConfigSchema.Core`, `Grid`, `Dock`, `Divider`, `Glass`, `Workstation`, `Debug`.
- Later tasks consume key names/default/range metadata but do not mutate schema objects.

- [ ] **Step 1: Write the failing schema tests**

Create `ConfigSchemaTest.java` with tests that require the new classes and verify at minimum:

```java
@Test
public void allKeyNamesAreUnique() {
    Set<String> seen = new HashSet<>();
    for (ConfigKey<?> key : ConfigSchema.all()) {
        assertTrue("duplicate key: " + key.name(), seen.add(key.name()));
    }
}

@Test
public void widgetAdaptationKeepsCurrentDefault() {
    assertEquals("grid_widget_adaptation", ConfigSchema.Grid.WIDGET_ADAPTATION.name());
    assertEquals(Boolean.FALSE, ConfigSchema.Grid.WIDGET_ADAPTATION.uiDefault());
    assertEquals(Boolean.FALSE, ConfigSchema.Grid.WIDGET_ADAPTATION.runtimeFallback());
}

@Test
public void integerDefaultsAreInsideDeclaredImportBounds() {
    for (ConfigKey<?> key : ConfigSchema.all()) {
        if (key.type() != ConfigKey.Type.INT || key.minInt() == null) continue;
        int value = (Integer) key.uiDefault();
        assertTrue(key.name(), value >= key.minInt());
        assertTrue(key.name(), value <= key.maxInt());
    }
}
```

Also encode known intentional context differences so later cleanup cannot accidentally collapse them. Examples:

```java
@Test
public void legacyAndCurrentDefaultsRemainDistinctWhereRequired() {
    assertEquals(Integer.valueOf(-1), ConfigSchema.Glass.CAPTURE_BLEED_TOP.runtimeFallback());
    assertEquals(Integer.valueOf(17), ConfigSchema.Glass.CAPTURE_BLEED_TOP.uiDefault());
    assertEquals(Integer.valueOf(48), ConfigSchema.Glass.CAPTURE_BLEED_TOP.exportDefault());

    assertEquals(Integer.valueOf(4), ConfigSchema.Dock.SQUIRCLE_STROKE_WIDTH.runtimeFallback());
    assertEquals(Integer.valueOf(1), ConfigSchema.Dock.SQUIRCLE_STROKE_WIDTH.uiDefault());
    assertEquals(Integer.valueOf(4), ConfigSchema.Dock.SQUIRCLE_STROKE_WIDTH.exportDefault());
}
```

- [ ] **Step 2: Run the new test and verify RED**

Run:

```bash
./gradlew testDebugUnitTest --tests 'com.hellovoid.liquiddock.config.ConfigSchemaTest'
```

Expected: compilation failure because `ConfigKey`/`ConfigSchema` do not exist.

- [ ] **Step 3: Implement the minimal immutable metadata model**

Implement `ConfigKey<T>` with explicit enums:

```java
public final class ConfigKey<T> {
    public enum Type { BOOLEAN, INT, STRING }
    public enum StorageMode { DIRECT, DP_TENTHS }
    public enum ExportMode { ALWAYS, IF_PRESENT, NEVER }

    private final String name;
    private final Type type;
    private final T uiDefault;
    private final T runtimeFallback;
    private final T exportDefault;
    private final Integer minInt;
    private final Integer maxInt;
    private final StorageMode storageMode;
    private final ExportMode exportMode;
    // constructor + read-only accessors only
}
```

`runtimeFallback` means the fallback used by the injected runtime if a key is absent. `uiDefault` means the current post-migration setting/reset default. `exportDefault` means the value historically emitted when the preference is absent. These are deliberately separate because current code already has different values in several places.

Populate `ConfigSchema` from the current code, not from guessed values. Include every runtime/user setting currently referenced by `LiquidDockConfig`, `SettingsActivity` export/import, and Compose controls. Mark non-exported current settings such as debug-only options with `ExportMode.NEVER` unless current export already emits them. Mark legacy divider fields as `IF_PRESENT`. Mark decimal-dp keys as `DP_TENTHS`.

Do not force dynamic compatibility defaults such as `grid_landscape_row_gap` (`offsets ? 0 : (dp ? 1 : 3)`) or divider explicit/legacy defaults into a scalar. Their schema entry stores the current UI/import metadata, while the existing conditional runtime fallback remains in `LiquidDockConfig` until a later compatibility-policy extraction.

- [ ] **Step 4: Run schema tests**

Run:

```bash
./gradlew testDebugUnitTest --tests 'com.hellovoid.liquiddock.config.ConfigSchemaTest'
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/config/ConfigKey.java \
        src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java \
        src/test/java/com/hellovoid/liquiddock/config/ConfigSchemaTest.java
git commit -m "refactor: define typed config schema"
```

---

### Task 2: Build a Pure Map-Based Config Codec

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/config/ConfigCodec.java`
- Create: `src/test/java/com/hellovoid/liquiddock/config/ConfigCodecTest.java`

**Interfaces:**
- Consumes: `ConfigSchema.all()` and `ConfigKey` metadata.
- Produces: `LinkedHashMap<String, Object> exportValues(Map<String, ?> preferences)`.
- Produces: `LinkedHashMap<String, Object> importValues(Map<String, ?> jsonValues)` where returned entries are the exact SharedPreferences writes, including `<key>_tenths` for decimal-dp keys.
- `ConfigCodec` must have no Android imports and no `org.json` imports.

- [ ] **Step 1: Write codec regression tests before implementation**

Create tests covering exact approved behavior:

```java
@Test
public void widgetAdaptationRoundTrips() {
    Map<String, Object> prefs = new HashMap<>();
    prefs.put("grid_widget_adaptation", true);
    Map<String, Object> exported = ConfigCodec.exportValues(prefs);
    assertEquals(Boolean.TRUE, exported.get("grid_widget_adaptation"));

    Map<String, Object> imported = ConfigCodec.importValues(exported);
    assertEquals(Boolean.TRUE, imported.get("grid_widget_adaptation"));
}
```

Add dp-tenths precedence:

```java
@Test
public void decimalDpExportPrefersTenths() {
    Map<String, Object> prefs = new HashMap<>();
    prefs.put("indicator_landscape_y", -9);
    prefs.put("indicator_landscape_y_tenths", -88);
    assertEquals(-8.8d,
        ((Number) ConfigCodec.exportValues(prefs).get("indicator_landscape_y")).doubleValue(),
        0.0001d);
}
```

Add import clamping using existing bounds, e.g. `liquid_capture_power_limit_fps` clamps to 5..60 and `dock_shadow_alpha` to 0..200.

Add legacy aliases exactly matching current behavior:

- `grid_landscape_margin_horizontal` -> left and right;
- `grid_portrait_margin_horizontal` -> left and right;
- old `grid_margin_left/right/top/bottom` conversion when modern landscape margins are absent;
- workstation legacy All Apps offsets remain importable.

Add optional divider preservation: absent optional legacy divider keys must not be synthesized by export or import.

- [ ] **Step 2: Run codec tests and verify RED**

```bash
./gradlew testDebugUnitTest --tests 'com.hellovoid.liquiddock.config.ConfigCodecTest'
```

Expected: compilation failure because `ConfigCodec` does not exist.

- [ ] **Step 3: Implement export transformation**

Implement schema-driven export with ordered output. Preserve the old behavior that `dock_dimensions_dp` and `liquid_dimensions_dp` are emitted as `true` by the app-side exporter even if a missing/raw legacy preference would have another fallback. Preserve current `exportDefault` values rather than replacing them with UI defaults.

For `DP_TENTHS` keys:

```java
if (preferences.containsKey(key.name() + "_tenths")) {
    Number tenths = (Number) preferences.get(key.name() + "_tenths");
    out.put(key.name(), tenths.intValue() / 10.0d);
} else {
    out.put(key.name(), directOrExportDefault(...));
}
```

For `IF_PRESENT`, emit only when the source contains the key.

- [ ] **Step 4: Implement import transformation**

For direct ints, clamp using the existing import range. For booleans, only produce a write when the JSON map actually contains the field, except current mandatory migration flags that `SettingsActivity` intentionally writes (`grid_margins_dp`/`grid_margins_offset`). For decimal-dp values produce both:

```java
out.put(key, (int) Math.round(value));
out.put(key + "_tenths", (int) Math.round(value * 10.0d));
```

Keep the legacy alias conversions in named private methods so they are explicit compatibility rules, not scattered loops.

- [ ] **Step 5: Run focused and full verification**

```bash
./gradlew testDebugUnitTest --tests 'com.hellovoid.liquiddock.config.ConfigCodecTest'
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Expected: all PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/config/ConfigCodec.java \
        src/test/java/com/hellovoid/liquiddock/config/ConfigCodecTest.java
git commit -m "refactor: centralize config import export codec"
```

---

### Task 3: Wire SettingsActivity Export/Import to ConfigCodec

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/SettingsActivity.java`
- Test: `src/test/java/com/hellovoid/liquiddock/config/ConfigCodecTest.java`

**Interfaces:**
- Consumes: `ConfigCodec.exportValues(Map<String, ?>)` and `ConfigCodec.importValues(Map<String, ?>)`.
- Produces: thin Android/JSON adapters in `SettingsActivity`; no key/default/range lists remain in `collectParameters()`/`applyImportedParameters()`.

- [ ] **Step 1: Add a parity fixture test before changing the Activity**

In `ConfigCodecTest`, build a representative preference map containing values from every group (grid, dock, divider, workstation, glass, stroke, shadow), including tenths values and missing optional divider fields. Assert the codec output contains the same values the current manual exporter would emit plus `grid_widget_adaptation`.

Also import a representative legacy map and assert exact writes for converted aliases and clamped values.

- [ ] **Step 2: Run tests and keep them GREEN before wiring**

```bash
./gradlew testDebugUnitTest --tests 'com.hellovoid.liquiddock.config.ConfigCodecTest'
```

Expected: PASS. This is the behavior barrier for the wiring-only change.

- [ ] **Step 3: Replace manual export body with a thin adapter**

`collectParameters(SharedPreferences sp)` becomes conceptually:

```java
private static JSONObject collectParameters(SharedPreferences sp) throws Exception {
    JSONObject json = new JSONObject();
    for (Map.Entry<String, Object> entry : ConfigCodec.exportValues(sp.getAll()).entrySet()) {
        json.put(entry.getKey(), entry.getValue());
    }
    return json;
}
```

Keep `_format` and `_version` insertion in `exportCurrentParameters()` exactly where it is.

- [ ] **Step 4: Replace manual import body with a thin adapter**

Convert `JSONObject` to a plain `Map<String,Object>` in one helper, call `ConfigCodec.importValues`, then apply each returned typed value to `SharedPreferences.Editor` with a small `putPreferenceValue` helper handling Boolean/Integer/Long/Float/String. Do not clear unspecified preferences.

Delete the obsolete `putInt`, `putDp`, manual key arrays, and old collect/apply key tables only after the adapter compiles.

- [ ] **Step 5: Verify build/tests**

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/SettingsActivity.java \
        src/test/java/com/hellovoid/liquiddock/config/ConfigCodecTest.java
git commit -m "refactor: route settings import export through codec"
```

---

### Task 4: Make LiquidDockConfig Loading Side-Effect-Free

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MainHook.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/WidgetGridSizingTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/ConfigLoadPolicyTest.java`

**Interfaces:**
- Consumes: `ConfigSchema` key names/default metadata where a scalar fallback is behaviorally identical.
- Produces: `LiquidDockConfig.load()` that only reads/parses/constructs.
- Temporary compatibility: `MainHook` explicitly applies `WidgetGridSizing.setWidgetAdaptationEnabled(grid.enabled && grid.widgetAdaptation)` immediately after one top-level config load. Phase 2 removes this global setter entirely.

- [ ] **Step 1: Add a failing policy test that forbids config-owned widget mutation**

Because direct API101 loading is Android/service-bound, test the constructor path by adding a package-visible factory in `LiquidDockConfig` only if needed:

```java
static LiquidDockConfig from(ConfigReader reader) {
    return new LiquidDockConfig(reader);
}
```

Do not add a fake production global. The test should set `WidgetGridSizing` false, construct a config snapshot whose grid/widget flags are true using a testable reader/map path, and assert the global flag did not change. If `ConfigReader` needs a map-backed constructor for tests, add package-visible `ConfigReader(Map<String, ?> prefs)` that copies the map and performs no API101 access.

- [ ] **Step 2: Run focused test and verify RED**

```bash
./gradlew testDebugUnitTest --tests 'com.hellovoid.liquiddock.ConfigLoadPolicyTest'
```

Expected: FAIL because the current `LiquidDockConfig` constructor calls `WidgetGridSizing.setWidgetAdaptationEnabled(...)`.

- [ ] **Step 3: Remove the constructor side effect and move it to installation**

Delete from `LiquidDockConfig`:

```java
WidgetGridSizing.setWidgetAdaptationEnabled(
    WidgetGridSizing.shouldAdaptWidgets(grid.enabled, grid.widgetAdaptation));
```

Immediately after `MainHook` performs its top-level `LiquidDockConfig.load()`, explicitly preserve existing behavior:

```java
WidgetGridSizing.setWidgetAdaptationEnabled(
    WidgetGridSizing.shouldAdaptWidgets(config.grid.enabled, config.grid.widgetAdaptation));
```

Do not add this call to other config reload sites. This intentionally makes side-effect ownership visible in the composition path until Phase 2 removes it.

- [ ] **Step 4: Replace safe duplicated key/default literals in LiquidDockConfig with schema metadata**

Use schema names and scalar fallbacks only where behavior is exactly identical, for example:

```java
enabled = c.b(ConfigSchema.Core.ENABLED.name(),
              ConfigSchema.Core.ENABLED.runtimeFallback());
widgetAdaptation = c.b(ConfigSchema.Grid.WIDGET_ADAPTATION.name(),
                       ConfigSchema.Grid.WIDGET_ADAPTATION.runtimeFallback());
```

Keep conditional legacy compatibility expressions in place, e.g. row-gap fallback, divider explicit/legacy defaults, and workstation legacy All Apps fallback. Do not replace them with a current UI default.

- [ ] **Step 5: Verify tests/build**

```bash
./gradlew testDebugUnitTest --tests 'com.hellovoid.liquiddock.ConfigLoadPolicyTest'
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/ConfigReader.java \
        src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java \
        src/main/java/com/hellovoid/liquiddock/MainHook.java \
        src/test/java/com/hellovoid/liquiddock/ConfigLoadPolicyTest.java \
        src/test/java/com/hellovoid/liquiddock/WidgetGridSizingTest.java
git commit -m "refactor: make runtime config loading side effect free"
```

---

### Task 5: Extract Existing Preference Migrations Without Rewriting Them

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/config/ConfigMigration.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/SettingsActivity.java`
- Create: `src/test/java/com/hellovoid/liquiddock/config/ConfigMigrationTest.java`

**Interfaces:**
- Produces: `public static void migrate(Context context, SharedPreferences preferences)`.
- SettingsActivity consumes only `ConfigMigration.migrate(this, prefs)`.
- Migration order remains exactly: merged horizontal -> legacy grid keys -> grid to dp -> grid to offsets -> corners to dp -> liquid dimensions to dp -> dock dimensions to dp -> axis distances.

- [ ] **Step 1: Write pure regression tests for conversion helpers**

Extract only deterministic arithmetic/key mapping helpers needed to test legacy transformations without Android resources. Tests must cover:

- legacy 160/160/80/80 grid maps to the same landscape/portrait field placement;
- grid-to-offset baselines remain 57/28/1;
- dp conversion uses the provided density and existing clamp bounds;
- axis-distance fallback averages left/right exactly as current code;
- `putDpPreference` stores both rounded direct int and tenths.

The Android-facing method may still accept `Context`/`SharedPreferences`, but the transformation math used by it must be package-visible/pure enough for JUnit.

- [ ] **Step 2: Run migration test and verify RED**

```bash
./gradlew testDebugUnitTest --tests 'com.hellovoid.liquiddock.config.ConfigMigrationTest'
```

Expected: compilation failure because `ConfigMigration` does not exist.

- [ ] **Step 3: Move migration methods verbatim into ConfigMigration**

Move, do not redesign:

```text
migrateMergedHorizontal
copyMergedValueIfMissing
migrateLegacyGridKeys
migrateGridToDp
migrateGridToOffsets
migrateCornersToDp
migrateLiquidDimensionsToDp
migrateDockDimensionsToDp
migrateAxisDistances
migrateAxisValue
readDpPreference
putDpPreference
```

Retain `commit()` vs `apply()` choices exactly as current code. Retain historical constants exactly. The top-level `migrate()` must invoke them in the current order.

- [ ] **Step 4: Replace SettingsActivity migration implementation with delegation**

`migratePreferences()` should become:

```java
private void migratePreferences() {
    ConfigMigration.migrate(
        this,
        PreferenceManager.getDefaultSharedPreferences(this));
}
```

Delete moved private migration methods from `SettingsActivity`.

- [ ] **Step 5: Verify**

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/config/ConfigMigration.java \
        src/main/java/com/hellovoid/liquiddock/SettingsActivity.java \
        src/test/java/com/hellovoid/liquiddock/config/ConfigMigrationTest.java
git commit -m "refactor: isolate preference migrations"
```

---

### Task 6: Extract Preset Ownership Without Changing Values

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/config/PresetManager.java`
- Modify: `src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt`
- Modify: `src/main/java/com/hellovoid/liquiddock/SettingsActivity.java`
- Create: `src/test/java/com/hellovoid/liquiddock/config/ConfigPresetTest.java`

**Interfaces:**
- Produces: `PresetManager.applyDefault(SharedPreferences.Editor)` for the Compose default preset values only, with no Toast/restart side effects.
- Produces: `PresetManager.applyIpad(Context, SharedPreferences)` for the existing legacy iPad resource-aware preset calculation.
- UI remains responsible for Toast and launcher restart.

- [ ] **Step 1: Snapshot the current default preset values in a failing test**

Create an expected map containing the current values from `applyDefaultPreset`, including decimal/tenths values and the approved widget default:

```java
assertEquals(Boolean.TRUE, values.get("liquiddock_enabled"));
assertEquals(Boolean.FALSE, values.get("home_grid_8x4"));
assertEquals(Boolean.FALSE, values.get("grid_widget_adaptation"));
assertEquals(Integer.valueOf(170), values.get("liquid_ior"));
assertEquals(Integer.valueOf(30), values.get("liquid_capture_power_limit_fps"));
assertEquals(Integer.valueOf(100), values.get("liquid_capture_scale"));
assertEquals(Integer.valueOf(-88), values.get("indicator_landscape_y_tenths"));
assertEquals(Integer.valueOf(118), values.get("indicator_portrait_y_tenths"));
```

Expose a pure `PresetManager.defaultValues()` map for this test; `applyDefault` writes exactly that map.

- [ ] **Step 2: Run and verify RED**

```bash
./gradlew testDebugUnitTest --tests 'com.hellovoid.liquiddock.config.ConfigPresetTest'
```

Expected: compilation failure because `PresetManager` does not exist.

- [ ] **Step 3: Implement default preset map exactly**

Transcribe the current Compose preset values without normalizing them to schema defaults. Presets are intentional value sets, not defaults. Preserve every direct value and every `_tenths` value generated by the current `dp()` helper.

- [ ] **Step 4: Delegate Compose preset application**

Replace the large preference write block with:

```kotlin
val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
PresetManager.applyDefault(prefs.edit())
Toast.makeText(activity, "默认预设已应用", Toast.LENGTH_LONG).show()
activity.restartLauncher()
```

Preserve current commit semantics inside `PresetManager.applyDefault`.

- [ ] **Step 5: Move the legacy iPad preset implementation from SettingsFragment**

Move the existing resource probing/calculation code with no value or fallback changes. `SettingsFragment.applyIpadPreset()` becomes a thin call to `PresetManager.applyIpad(requireContext(), prefs)` plus its existing UI notification/restart behavior.

- [ ] **Step 6: Verify**

```bash
./gradlew testDebugUnitTest --tests 'com.hellovoid.liquiddock.config.ConfigPresetTest'
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/config/PresetManager.java \
        src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt \
        src/main/java/com/hellovoid/liquiddock/SettingsActivity.java \
        src/test/java/com/hellovoid/liquiddock/config/ConfigPresetTest.java
git commit -m "refactor: centralize preset ownership"
```

---

### Task 7: Bind Compose Settings Metadata to ConfigSchema

**Files:**
- Modify: `src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt`
- Modify: `src/test/java/com/hellovoid/liquiddock/config/ConfigSchemaTest.java`

**Interfaces:**
- Consumes: schema names, current UI defaults, and current UI ranges.
- Keeps Compose ownership of title/summary/page/dependency/section/unit presentation.

- [ ] **Step 1: Add schema assertions for all Compose numeric specs**

Extend `ConfigSchemaTest` to assert the current Compose range/default values for every `gridSpecs`, `dockSpecs`, `dividerSpecs`, `workstationSpecs`, `liquidSpecs`, `strokeSpecs`, and `shadowSpecs` entry. This creates a mechanical barrier against accidental range/default changes while the Kotlin lists are rewired.

- [ ] **Step 2: Run schema tests before rewiring**

```bash
./gradlew testDebugUnitTest --tests 'com.hellovoid.liquiddock.config.ConfigSchemaTest'
```

Expected: PASS.

- [ ] **Step 3: Change IntSpec construction to take a ConfigKey<Integer>**

Use a shape equivalent to:

```kotlin
private data class IntSpec(
    val config: ConfigKey<Int>,
    val title: String,
    val unit: String = "dp",
    val dependency: String? = null,
    val section: IntSection = IntSection.General,
    val summary: String = optionSummary(config.name()),
) {
    val key: String get() = config.name()
    val default: Int get() = config.uiDefault()
    val min: Int get() = requireNotNull(config.minInt())
    val max: Int get() = requireNotNull(config.maxInt())
}
```

If Java generic interop makes `ConfigKey<Int>` awkward, expose typed accessors in `ConfigSchema` or use `ConfigKey<Integer>` from Kotlin; do not duplicate numeric values back into Kotlin.

Keep UI-only unit/dependency/section/title metadata in Kotlin. Unit strings are presentation; `StorageMode.DP_TENTHS` controls persistence behavior.

- [ ] **Step 4: Rewire BooleanSetting calls for key/default metadata where straightforward**

At minimum rewire master enable, `home_grid_8x4`, `grid_widget_adaptation`, dock customization, workstation dock customization, liquid glass, and the major feature switches. Do not create a generic schema-driven UI renderer.

- [ ] **Step 5: Verify**

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Expected: PASS with no GUI layout/label changes.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt \
        src/test/java/com/hellovoid/liquiddock/config/ConfigSchemaTest.java
git commit -m "refactor: bind settings metadata to config schema"
```

---

### Task 8: Phase 1 Verification and Documentation Gate

**Files:**
- Modify: `ARCHITECTURE.md`
- Modify: `docs/superpowers/specs/2026-08-14-modular-architecture-design.md` only if implementation discovered a non-behavioral clarification that must be recorded.

**Interfaces:**
- Produces the acceptance evidence required before Phase 2.

- [ ] **Step 1: Run the complete unit suite from the final Phase 1 head**

```bash
./gradlew clean testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 2: Build debug from the same head**

```bash
./gradlew assembleDebug
```

Expected: PASS and a debug APK is produced. Do not install it in this phase unless explicitly requested.

- [ ] **Step 3: Inspect the final diff against the design baseline**

Run:

```bash
git diff --stat e2c21a59de3ba6a8aae5277422e601abb1438292..HEAD
git diff e2c21a59de3ba6a8aae5277422e601abb1438292..HEAD -- \
  src/main/java/com/hellovoid/liquiddock/SettingsActivity.java \
  src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt \
  src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java \
  src/main/java/com/hellovoid/liquiddock/MainHook.java
```

Explicitly confirm:

- no existing preference key was renamed;
- `_format` and `_version` are unchanged;
- all old export fields remain and `grid_widget_adaptation` is added;
- all old import fields/legacy aliases remain accepted;
- preset values are byte-for-byte/value-for-value equivalent to the prior implementation;
- migration order and arithmetic are unchanged;
- `LiquidDockConfig` contains no call that mutates another feature;
- MainHook's temporary widget gate is the only moved side effect;
- no Grid/Widget geometry or Liquid Glass behavior code changed.

- [ ] **Step 4: Update architecture documentation**

Document the resulting flow:

```text
local SharedPreferences -> ConfigMigration -> ConfigSchema/ConfigCodec
                                        \
                                         -> API101 Remote Preferences -> ConfigReader -> LiquidDockConfig snapshot
```

Document that schema metadata distinguishes current UI/storage defaults from legacy/runtime/export fallbacks where compatibility requires it.

- [ ] **Step 5: Commit documentation**

```bash
git add ARCHITECTURE.md docs/superpowers/specs/2026-08-14-modular-architecture-design.md
git commit -m "docs: record config ownership after phase 1"
```

- [ ] **Step 6: Wait for GitHub Actions on the final Phase 1 commit**

Required CI evidence:

```text
testDebugUnitTest  success
assembleDebug      success
```

Do not start Phase 2 until both are green. If CI differs from local results, investigate the CI failure rather than overriding the gate.

---

## Plan Self-Review Notes

- Spec coverage: Phase 1 covers schema, codec, migration ownership, preset ownership, side-effect-free runtime snapshots, GUI metadata reuse, widget export/import defect, and final compatibility verification.
- Intentional deferment: removing `WidgetGridSizing` global state itself belongs to Phase 2; Phase 1 only moves ownership out of config loading while preserving behavior.
- Intentional deferment: generic runtime `ConfigProvider` polling/snapshot distribution is introduced with MainHook/runtime modularization in later phases; Phase 1 establishes the side-effect-free snapshot/schema base it requires.
- Type consistency: all import/export tests use plain `Map<String, Object>`; Android/`JSONObject` remain boundary adapters only.
- Compatibility ambiguity resolved: `uiDefault`, `runtimeFallback`, and `exportDefault` are explicitly separate because the current implementation has legitimate migration-era differences. No task is allowed to normalize them merely for cleanliness.
