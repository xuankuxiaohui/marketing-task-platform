package com.mkt.admin.arch.fixture.accessforms.illegal;

import com.mkt.admin.arch.fixture.accessforms.outside.ShadyBridge;

/**
 * Layer-shaped origin that only depends on a non-layer bridge (no direct ForbiddenType mention).
 * Paired with {@link com.mkt.admin.arch.fixture.accessforms.outside.ShadyBridge} for the
 * consideringOnlyDependenciesInLayers blind-spot demo.
 */
public final class DependsViaOutsideBridge {

    private final ShadyBridge bridge;

    public DependsViaOutsideBridge(ShadyBridge bridge) {
        this.bridge = bridge;
    }

    public void run() {
        bridge.touch();
    }
}
