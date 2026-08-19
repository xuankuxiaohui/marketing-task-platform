package com.mkt.admin.arch.fixture.layers.illegal;

import com.mkt.admin.AdminApplication;

/** Deliberate reverse dependency: kernel-shaped type → app (RL-01). */
public final class KernelDependingOnApp {

    public Class<?> leak() {
        return AdminApplication.class;
    }
}
