package com.mkt.activity.application;

import com.mkt.activity.command.ActivityPublishCommand;
import com.mkt.activity.command.ActivityScheduleCommand;
import com.mkt.activity.domain.ActivityDates;
import com.mkt.activity.domain.ActivityStatuses;
import com.mkt.activity.entity.ActActivityEntity;
import com.mkt.activity.response.ActivityPublishResponse;
import com.mkt.activity.support.ActivityErrorCodes;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Publish, schedule, and offline entries extracted from {@link ActivityAdminAppService} (R29-2).
 * Move-only: publish / schedule / offline semantics unchanged.
 */
@Component
public class ActivityAdminLifecycle {

    private final ActivityActivityStore activities;
    private final ActivityAdminPersist persist;
    private final ActivityAdminDraft drafts;
    private final Clock clock;

    @Autowired
    public ActivityAdminLifecycle(
            ActivityActivityStore activities,
            ActivityAdminPersist persist,
            ActivityAdminDraft drafts,
            Clock clock) {
        this.activities = activities;
        this.persist = persist;
        this.drafts = drafts;
        this.clock = clock;
    }

    ActivityPublishResponse publish(long id, ActivityPublishCommand command) {
        ActivityPublishCommand cmd = command == null ? new ActivityPublishCommand(null, null) : command;
        ActActivityEntity entity = activities.getByIdForUpdate(id);
        if (entity == null || entity.deletedFlag()) {
            throw new BusinessException(ActivityErrorCodes.NOT_FOUND);
        }
        if (entity.pendingFlag() && !cmd.confirmTrue()) {
            return ActivityPublishResponse.preview(
                    entity.getId(), entity.getCode(), entity.versionOrZero(), entity.getStatus());
        }
        String status = entity.getStatus();
        if (ActivityStatuses.DRAFT.equals(status)
                || ActivityStatuses.SCHEDULED.equals(status)
                || (ActivityStatuses.PUBLISHED.equals(status) && entity.pendingFlag())) {
            persist.freezeToPublished(entity);
        } else {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "当前状态不可发布");
        }
        ActActivityEntity fresh = persist.requireLive(id);
        return ActivityPublishResponse.done(
                fresh.getId(), fresh.getCode(), fresh.versionOrZero(), fresh.getStatus());
    }

    ActivityPublishResponse schedule(long id, ActivityScheduleCommand command) {
        ActActivityEntity entity = persist.requireLive(id);
        LocalDateTime now = ActivityDates.toUtc(clock.instant());
        if (command.publishAt() != null) {
            if (!ActivityStatuses.DRAFT.equals(entity.getStatus())) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "仅草稿可设定时发布");
            }
            LocalDateTime publishAt = ActivityDates.toUtc(command.publishAt());
            if (!publishAt.isAfter(now)) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "定时发布时间必须晚于当前时间");
            }
            drafts.validateDraft(entity);
            entity.setSchedulePublishAt(publishAt);
            entity.setStatus(ActivityStatuses.SCHEDULED);
            entity.setPendingRevision(0);
        }
        if (command.offlineAt() != null) {
            if (!ActivityStatuses.PUBLISHED.equals(entity.getStatus())
                    && !ActivityStatuses.SCHEDULED.equals(entity.getStatus())) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "仅已排期或已发布可设定时下线");
            }
            LocalDateTime offlineAt = ActivityDates.toUtc(command.offlineAt());
            if (!offlineAt.isAfter(now)) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "定时下线时间必须晚于当前时间");
            }
            entity.setScheduleOfflineAt(offlineAt);
        }
        if (command.publishAt() == null && command.offlineAt() == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "须指定定时发布或下线时间");
        }
        entity.setUpdatedAt(now);
        activities.update(entity);
        return ActivityPublishResponse.done(entity.getId(), entity.getCode(), entity.versionOrZero(), entity.getStatus());
    }

    ActivityPublishResponse offline(long id) {
        ActActivityEntity entity = persist.requireLive(id);
        if (!ActivityStatuses.PUBLISHED.equals(entity.getStatus())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "仅已发布活动可下线");
        }
        entity.setStatus(ActivityStatuses.OFFLINE);
        entity.setScheduleOfflineAt(null);
        entity.setUpdatedAt(ActivityDates.toUtc(clock.instant()));
        activities.update(entity);
        return ActivityPublishResponse.done(entity.getId(), entity.getCode(), entity.versionOrZero(), entity.getStatus());
    }
}
