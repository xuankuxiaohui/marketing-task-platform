package com.mkt.activity.application;

import com.mkt.activity.domain.ActivityDates;
import com.mkt.activity.domain.ActivityStatuses;
import com.mkt.activity.entity.ActActivityEntity;
import com.mkt.activity.response.OkResponse;
import com.mkt.activity.support.ActivityErrorCodes;
import com.mkt.kernel.BusinessException;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Delete entry extracted from {@link ActivityAdminAppService} (R29-4).
 * Move-only: delete semantics unchanged.
 */
@Component
public class ActivityAdminDelete {

    private final ActivityActivityStore activities;
    private final ActivityAdminPersist persist;
    private final Clock clock;

    @Autowired
    public ActivityAdminDelete(
            ActivityActivityStore activities, ActivityAdminPersist persist, Clock clock) {
        this.activities = activities;
        this.persist = persist;
        this.clock = clock;
    }

    OkResponse delete(long id) {
        ActActivityEntity entity = persist.requireLive(id);
        if (!ActivityStatuses.deletable(entity.getStatus())) {
            throw new BusinessException(ActivityErrorCodes.PUBLISHED_NOT_DELETABLE);
        }
        entity.setDeleted(1);
        entity.setUpdatedAt(ActivityDates.toUtc(clock.instant()));
        activities.update(entity);
        return OkResponse.yes();
    }
}
