# 307 Native Shell A/B Design

## Goal
Determine whether the original HyperOS 3.0.307 Dock background View is still the visual carrier for Dock stroke, stroke shadow, vendor shadow/outline, and related geometry after Prismal takes over blur.

## Experiment
Keep vendor compositor/pass-window blur disabled at radius 0. Keep the original Dock background View visible after Prismal installs a valid capture, and place the Prismal host immediately below that native View in the same parent. Revert the prior squircle-only experiment so this A/B has no shape-variable confound.

The native View is treated as a visual shell, not as the blur owner. Prismal remains the sole blur/refraction/highlight renderer. Do not restore the full legacy Dock customization hook block yet; this experiment only determines whether preserving the shell restores the missing stroke/shadow/outline behavior.

## Scope
Production changes are limited to `MiuixGlassHook.java` and `DockLiquidGlassView.java`. No unit tests are added or run per user instruction. Verification is source-scope inspection plus `assembleDebug` and a device visual check.

## Success criterion
On device, the prior LiquidDock stroke/shadow/native rounded visual should reappear while `Floating Dock ... regionblurRadius` remains 0/absent. If the shell visuals return, the next step is to reconnect 307 to the Dock customization hooks without restoring vendor GPU blur.