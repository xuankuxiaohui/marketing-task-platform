package com.mkt.admin.arch.fixture.accessforms.forbidden;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Stand-in annotation type in a forbidden package. */
@Retention(RetentionPolicy.RUNTIME)
public @interface ForbiddenMarker {
}
