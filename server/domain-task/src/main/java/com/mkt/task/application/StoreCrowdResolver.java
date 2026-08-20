package com.mkt.task.application;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.task.domain.CrowdStatuses;
import com.mkt.task.entity.TaskCrowdEntity;
import com.mkt.task.expression.CrowdResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * inCrowd lookup (R11.9): missing/disabled pack is false + warn. Membership is DB-backed;
 * cache load is wired when KeyValueStore SET ops are used by later tasks.
 */
public final class StoreCrowdResolver implements CrowdResolver {

    private static final Logger log = LoggerFactory.getLogger(StoreCrowdResolver.class);

    private final TaskCrowdStore store;
    private final UserAttributePort users;
    private final Long userId;

    public StoreCrowdResolver(TaskCrowdStore store, UserAttributePort users, Long userId) {
        this.store = store;
        this.users = users;
        this.userId = userId;
    }

    @Override
    public boolean contains(String crowdCode) {
        TaskCrowdEntity crowd = store.getByCode(crowdCode);
        if (crowd == null || !CrowdStatuses.ENABLED.equals(crowd.getStatus())) {
            log.warn("inCrowd pack missing or disabled: {}", crowdCode);
            return false;
        }
        if (userId == null) {
            return false;
        }
        if (users != null) {
            UserAttributes attrs = users.attributes(userId);
            if (attrs.accountStatus() == AccountStatus.NOT_FOUND) {
                return false;
            }
        }
        return store.countMember(crowd.getId(), userId) > 0;
    }
}
