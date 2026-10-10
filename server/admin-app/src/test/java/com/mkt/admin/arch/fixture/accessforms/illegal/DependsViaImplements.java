package com.mkt.admin.arch.fixture.accessforms.illegal;

import com.mkt.admin.arch.fixture.accessforms.target.ForbiddenApi;

/** Deliberate violation via implements (inheritance without ctor access). */
public final class DependsViaImplements implements ForbiddenApi {
}
