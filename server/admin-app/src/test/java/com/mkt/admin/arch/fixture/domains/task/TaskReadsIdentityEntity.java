package com.mkt.admin.arch.fixture.domains.task;

import com.mkt.admin.arch.fixture.domains.identity.entity.UserEntity;

/** Deliberate RL-03 violation: task-shaped type imports identity entity. */
public final class TaskReadsIdentityEntity {

    public Class<?> leak() {
        return UserEntity.class;
    }
}
