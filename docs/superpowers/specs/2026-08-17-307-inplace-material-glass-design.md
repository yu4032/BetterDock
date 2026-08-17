# 307 In-Place Material Glass Design

## Goal
Render LiquidDock inside the existing HyperOS 3.0.307 HotSeats material View instead of injecting a sibling visual owner that hides/replaces the vendor background.

## Architecture
`HotSeatsListContentMiuiXBlurBackground` and the themed fallback `HotSeatsListContentBlurBackground2` are `FrameLayout`-based material containers. Keep that vendor View alive as the authoritative Dock shell: it retains size/radius lifecycle, outline, MiShadow, foreground, and LiquidDock stroke/shadow. Insert the existing `DockLiquidGlassHostView` as a MATCH_PARENT child of that material container. Android then composes vendor background/internal material first, Liquid Glass next, and the vendor View foreground last.

The child host keeps the existing `DockLiquidGlassView` capture/shader pipeline intact, avoiding a new renderer or a global `View.draw()` hook. `DockLiquidGlassView` gains a 307-only preserve-source mode so installing a valid capture never sets the parent material View alpha to zero. Vendor compositor/pass-window/background blur remains forcibly disabled, because device evidence showed it post-processes the entire Floating Dock Surface.

## Overlay ownership
The in-place host renders Liquid Glass body and advanced optical highlight but does not install its own configurable stroke. `DockStrokeRenderer` remains installed on the vendor material View foreground so stroke and stroke-shadow render above the glass. The vendor View remains alpha 1.

## Dock customization compatibility
The 307 pipeline currently returns before MainHook's legacy customization block. Reapply the relevant customization at the 307 hooks instead of maintaining a second sibling glass architecture:
- width offset in `setBackgroundWidth`;
- height offset in `setBackgroundHeight`;
- blur-corner offset in `setBackgroundRadius` (stroke radius remains derived by `DockStrokeRenderer` using `cornerOffset - blurCornerOffset`);
- item spacing hooks;
- bottom offset hook.

No vendor GPU blur radius customization is restored; Liquid Glass owns optical blur.

## Theme switching and hierarchy recovery
A bound host is now a child of the exact current material background. Hierarchy recovery resolves the host inside that background first and continues to rebind when HyperOS replaces the material instance during theme changes.

## Verification
Per the user's agile instruction, do not run unit tests for this experiment. Verify exact production diff scope, run `assembleDebug`, and deliver an APK. Device success criteria: Liquid Glass is visible; vendor post-composition region blur remains zero/absent; native MiShadow/outline and LiquidDock stroke/shadow survive; Dock geometry customization responds again; theme switching still rebinds.