package com.mkt.admin.arch.fixture.accessforms.illegal;

import com.mkt.admin.arch.fixture.accessforms.target.ForbiddenType;

/** Deliberate violation via declared return type (no body access). */
public final class DependsViaReturnType {

    public ForbiddenType leak() {
        return null;
    }
}
