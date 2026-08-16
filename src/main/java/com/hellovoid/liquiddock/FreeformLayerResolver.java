package com.hellovoid.liquiddock;

import android.content.Context;

import java.util.Collection;
import java.util.Collections;

/**
 * Temporary compatibility shell for DockLiquidGlassView's old preflight API.
 *
 * This class no longer observes tasks, windowing modes, visibility, display IDs, packages,
 * UIDs, or SurfaceFlinger layer names. SystemUI's FreeformTaskListener snapshot is the only
 * freeform task authority; FreeformCaptureLeashHook applies that snapshot at mode-1 submit.
 * The shell exists only until DockLiquidGlassView drops the legacy preflight calls entirely.
 */
final class FreeformLayerResolver {
    FreeformLayerResolver(Context context, SurfaceLayerNameResolver ignoredLegacyResolver) {
        Context app = context.getApplicationContext();
        Context safeContext = app != null ? app : context;
        FreeformLeashRuntime.install(new FreeformTaskLeashResolver(safeContext));
    }

    void invalidate() {
        // No Launcher-side freeform cache remains.
    }

    boolean hasVisibleFreeformTasks() {
        // Presence is no longer guessed in Launcher. HOME source policy ignores this legacy
        // value, while APP safety is enforced by the final SystemUI snapshot capture gate.
        return false;
    }

    Collection<String> resolveVisibleLayerNames() {
        // Freeform exclusions are SurfaceControl task leashes, never guessed layer names.
        return Collections.emptyList();
    }
}
