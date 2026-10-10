package com.mkt.admin.arch.fixture.accessforms.illegal;

import com.mkt.admin.arch.fixture.accessforms.forbidden.ForbiddenApi;

/** Deliberate violation via implements (inheritance without ctor access). */
public final class DependsViaImplements implements ForbiddenApi {
}
