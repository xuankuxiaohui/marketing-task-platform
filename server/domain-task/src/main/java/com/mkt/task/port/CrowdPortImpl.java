package com.mkt.task.port;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.CrowdPort;
import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.task.application.TaskCrowdStore;
import com.mkt.task.domain.CrowdStatuses;
import com.mkt.task.entity.TaskCrowdEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** DEC-003 CrowdPort backed by task_crowd tables (combo via interface defaults). */
public class CrowdPortImpl implements CrowdPort {

    private static final Logger log = LoggerFactory.getLogger(CrowdPortImpl.class);

    private final TaskCrowdStore store;
    private final UserAttributePort users;

    public CrowdPortImpl(TaskCrowdStore store, UserAttributePort users) {
        this.store = store;
        this.users = users;
    }

    @Override
    public boolean memberOf(long crowdId, long userId) {
        TaskCrowdEntity crowd = store.getById(crowdId);
        if (crowd == null || !CrowdStatuses.ENABLED.equals(crowd.getStatus())) {
            log.warn("crowd pack missing or disabled: {}", crowdId);
            return false;
        }
        return store.countMember(crowdId, userId) > 0;
    }

    @Override
    public boolean memberOfCode(String crowdCode, long userId) {
        if (crowdCode == null || crowdCode.isBlank()) {
            return false;
        }
        TaskCrowdEntity crowd = store.getByCode(crowdCode);
        if (crowd == null || !CrowdStatuses.ENABLED.equals(crowd.getStatus())) {
            log.warn("inCrowd pack missing or disabled: {}", crowdCode);
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
