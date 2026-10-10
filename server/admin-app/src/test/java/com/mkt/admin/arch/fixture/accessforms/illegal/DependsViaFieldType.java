package com.mkt.admin.arch.fixture.accessforms.illegal;

import com.mkt.admin.arch.fixture.accessforms.target.ForbiddenType;

/** Deliberate violation via declared field type (no method call). */
public final class DependsViaFieldType {

    @SuppressWarnings("unused")
    private final ForbiddenType leak;

    public DependsViaFieldType(ForbiddenType leak) {
        this.leak = leak;
    }
}
