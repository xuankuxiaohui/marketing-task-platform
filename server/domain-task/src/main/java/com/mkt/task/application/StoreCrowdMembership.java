package com.mkt.task.application;

import com.mkt.task.domain.CrowdMembership;
import com.mkt.task.domain.CrowdStatuses;
import com.mkt.task.entity.TaskCrowdEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Gray / filter crowd-id lookup. Missing or disabled packs are not hits + warn (design §5.3). */
public final class StoreCrowdMembership implements CrowdMembership {

    private static final Logger log = LoggerFactory.getLogger(StoreCrowdMembership.class);

    private final TaskCrowdStore store;

    public StoreCrowdMembership(TaskCrowdStore store) {
        this.store = store;
    }

    @Override
    public boolean contains(long crowdId, long userId) {
        TaskCrowdEntity crowd = store.getById(crowdId);
        if (crowd == null || !CrowdStatuses.ENABLED.equals(crowd.getStatus())) {
            log.warn("crowd pack missing or disabled: {}", crowdId);
            return false;
        }
        return store.countMember(crowdId, userId) > 0;
    }
}
