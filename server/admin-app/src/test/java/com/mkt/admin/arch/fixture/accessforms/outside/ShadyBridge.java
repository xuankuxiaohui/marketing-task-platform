package com.mkt.admin.arch.fixture.accessforms.outside;

import com.mkt.admin.arch.fixture.accessforms.target.ForbiddenType;

/**
 * Non-layer package depending on an App-shaped target. Used to demonstrate
 * consideringOnlyDependenciesInLayers ignoring Origin→outside→Target paths.
 */
public final class ShadyBridge {

    @SuppressWarnings("unused")
    private final ForbiddenType app;

    public ShadyBridge(ForbiddenType app) {
        this.app = app;
    }

    public void touch() {
        app.touch();
    }
}
