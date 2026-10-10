package com.mkt.admin.arch.fixture.accessforms.illegal;

import com.mkt.admin.arch.fixture.accessforms.target.ForbiddenType;

/** Deliberate violation via method call. */
public final class DependsViaMethodCall {

    public void use(ForbiddenType target) {
        target.touch();
    }
}
