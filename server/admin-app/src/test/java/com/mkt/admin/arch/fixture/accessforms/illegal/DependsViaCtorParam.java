package com.mkt.admin.arch.fixture.accessforms.illegal;

import com.mkt.admin.arch.fixture.accessforms.target.ForbiddenType;

/** Deliberate violation via constructor parameter type. */
public final class DependsViaCtorParam {

    public DependsViaCtorParam(ForbiddenType ignored) {
        // parameter type alone must count as a dependency
    }
}
