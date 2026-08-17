# HyperOS 307 Themed MiShadow Suppression Design

## Goal

Prevent the oversized native `MiShadow` path (`radius=143`) from being applied to the currently bound `HotSeatsListContentBlurBackground2` when LiquidDock Prismal is active, while preserving the default `HotSeatsListContentMiuiXBlurBackground` shadow and every unrelated `MiShadowUtils` consumer.

## Evidence and scope

Device logs and Launcher DEX inspection show two independent effects after icon-theme replacement: `BlurBackground2.addBlur()` owns the native background blur, while `HotSeats.showViewShadow()` calls `MiShadowUtils.applyViewShadow(...)`, which reaches hidden `View.setMiShadow(... radius=143 ...)`. The latter produces the wide `ShortcutMenuLayer` shadow-blur image. This fix targets only that second path.

## Architecture

Install one process-wide hook on `com.miui.home.launcher.common.MiShadowUtils.applyViewShadow(...)` from `Miuix307MaterialPipeline`. Before calling the original method, inspect its first argument. Skip the original only when `MiuixGlassHook.shouldSuppressCompatMiShadow(View)` returns true.

`shouldSuppressCompatMiShadow(View)` must require both:

1. exact runtime class name `com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2`;
2. object identity equal to the currently bound `MiuixGlassHook.backgroundRef`, with a live Prismal host still attached to the same parent.

Default MiuiX material, detached/stale themed backgrounds, Recents/TaskView, shortcut menu, and all other views remain untouched.

## Logging

Log `compat BlurBackground2 MiShadow suppressed` once per bound themed background instance, not once per animation frame. Reset the once-per-instance latch when the bound background changes.

## Non-goals

- Do not disable or clear `BlurBackground2` native background blur.
- Do not change LiquidDock stroke rendering.
- Do not change default MiuiX material shadow behavior.
- Do not hook `View.setMiShadow` globally.
- Do not change drag/capture behavior in this patch.

## Tests

Add regression coverage requiring:

- `Miuix307MaterialPipeline` installs an `MiShadowUtils.applyViewShadow` hook;
- suppression delegates to `MiuixGlassHook.shouldSuppressCompatMiShadow`;
- the helper checks exact `BlurBackground2` class and exact current bound instance;
- no code path suppresses default `HotSeatsListContentMiuiXBlurBackground` or arbitrary views;
- existing themed blur ownership and drag regressions continue to pass.
