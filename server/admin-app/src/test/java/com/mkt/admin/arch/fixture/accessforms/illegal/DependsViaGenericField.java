package com.mkt.admin.arch.fixture.accessforms.illegal;

import com.mkt.admin.arch.fixture.accessforms.forbidden.ForbiddenType;
import java.util.List;

/** Deliberate violation via generic field type argument. */
public final class DependsViaGenericField {

    @SuppressWarnings("unused")
    private final List<ForbiddenType> leaks;

    public DependsViaGenericField(List<ForbiddenType> leaks) {
        this.leaks = leaks;
    }
}
