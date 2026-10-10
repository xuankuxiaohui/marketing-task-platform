package com.mkt.admin.arch.fixture.accessforms.illegal;

import com.mkt.admin.arch.fixture.accessforms.target.ForbiddenMarker;

/** Deliberate violation via annotation type from forbidden package. */
@ForbiddenMarker
public final class DependsViaAnnotation {
}
