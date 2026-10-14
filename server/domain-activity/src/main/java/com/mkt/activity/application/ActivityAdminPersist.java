package com.mkt.activity.application;

import com.mkt.activity.command.ActivitySaveCommand;
import com.mkt.activity.convert.ActivityDraftContent;
import com.mkt.activity.convert.ActivityFieldCodec;
import com.mkt.activity.domain.ActivityDates;
import com.mkt.activity.domain.ActivityStatuses;
import com.mkt.activity.entity.ActActivityEntity;
import com.mkt.activity.response.ActivitySaveResponse;
import com.mkt.activity.support.ActivityErrorCodes;
import com.mkt.activity.support.ActivityOperator;
import com.mkt.contract.RewardPort;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.json.JsonUtil;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * Persistence helpers extracted from {@link ActivityAdminAppService} (R3-4).
 * Move-only: insertNew / updateExisting / freezeToPublished / requireLive semantics unchanged.
 */
@Component
public class ActivityAdminPersist {

    private final ActivityActivityStore activities;
    private final ActivityAdminDraft drafts;
    private final ActivityAdminNormalize normalize;
    private final RewardPort rewards;
    private final Clock clock;

    @Autowired
    public ActivityAdminPersist(
            ActivityActivityStore activities,
            ActivityAdminDraft drafts,
            ActivityAdminNormalize normalize,
            RewardPort rewards,
            Clock clock) {
        this.activities = activities;
        this.drafts = drafts;
        this.normalize = normalize;
        this.rewards = rewards;
        this.clock = clock;
    }

    ActivitySaveResponse insertNew(ActivitySaveCommand command, ActivityDraftContent draft, LocalDateTime now) {
        if (activities.getByCode(command.code()) != null) {
            throw new BusinessException(ActivityErrorCodes.CODE_DUPLICATE);
        }
        ActActivityEntity entity = new ActActivityEntity();
        entity.setCode(command.code());
        drafts.applyDraft(entity, draft, true);
        entity.setStatus(ActivityStatuses.DRAFT);
        entity.setVersion(0);
        entity.setPendingRevision(0);
        entity.setDeleted(0);
        entity.setQuotaCount(0);
        entity.setCreatedBy(ActivityOperator.requireUserId());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setDraftContent(JsonUtil.toJson(draft));
        try {
            activities.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ActivityErrorCodes.CODE_DUPLICATE, ex);
        }
        return new ActivitySaveResponse(entity.getId(), entity.getCode(), 0, entity.getStatus());
    }

    ActivitySaveResponse updateExisting(
            ActivitySaveCommand command, ActivityDraftContent draft, LocalDateTime now) {
        ActActivityEntity existing = requireLive(command.id());
        if (!existing.getCode().equals(command.code())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "code 不可修改");
        }
        existing.setDraftContent(JsonUtil.toJson(draft));
        if (ActivityStatuses.OFFLINE.equals(existing.getStatus())) {
            existing.setStatus(ActivityStatuses.DRAFT);
            existing.setPendingRevision(0);
            drafts.applyDraft(existing, draft, true);
        } else if (ActivityStatuses.publishedFamily(existing.getStatus())) {
            existing.setPendingRevision(1);
        } else {
            drafts.applyDraft(existing, draft, true);
        }
        existing.setUpdatedAt(now);
        activities.update(existing);
        return new ActivitySaveResponse(
                existing.getId(), existing.getCode(), existing.versionOrZero(), existing.getStatus());
    }

    void freezeToPublished(ActActivityEntity entity) {
        ActivityDraftContent draft = ActivityFieldCodec.draft(entity.getDraftContent());
        if (draft == null) {
            throw new BusinessException(ActivityErrorCodes.HTML_EMPTY);
        }
        String html = normalize.sanitizeHtml(draft.richText());
        if (draft.participationPrizeId() != null && !rewards.prizeEnabled(draft.participationPrizeId())) {
            throw new BusinessException(ActivityErrorCodes.PRIZE_INVALID);
        }
        drafts.applyDraft(entity, ActivityAdminDraft.withHtml(draft, html), true);
        entity.setStatus(ActivityStatuses.PUBLISHED);
        entity.setVersion(entity.versionOrZero() + 1);
        entity.setPendingRevision(0);
        entity.setSchedulePublishAt(null);
        entity.setUpdatedAt(ActivityDates.toUtc(clock.instant()));
        activities.update(entity);
    }

    ActActivityEntity requireLive(long id) {
        ActActivityEntity entity = activities.getById(id);
        if (entity == null || entity.deletedFlag()) {
            throw new BusinessException(ActivityErrorCodes.NOT_FOUND);
        }
        return entity;
    }
}
