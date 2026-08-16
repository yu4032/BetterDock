package com.hellovoid.liquiddock;

import java.util.Collection;
import java.util.Collections;

/**
 * Retired compatibility adapter.
 *
 * HyperOS no longer exposes the historical SurfaceFlinger layer-debug path used by the
 * previous implementation. The remaining Dock call site only populates a legacy cached
 * diagnostic name and does not participate in capture source or exclusion decisions.
 * Keep these signatures until that large call site is removed in a normal local refactor,
 * but never query SurfaceFlinger or guess task/window layers here.
 */
final class SurfaceLayerNameResolver {
    String resolveTopmostByOwnerUid(int ownerUid) {
        return null;
    }

    Collection<String> resolveAllByOwnerUids(Collection<Integer> ownerUids) {
        return Collections.emptyList();
    }
}
