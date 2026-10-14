package com.mkt.activity.application;

import com.mkt.activity.command.ActivityGrayCommand;
import com.mkt.activity.convert.ActivityDraftContent;
import com.mkt.activity.convert.ActivityFieldCodec;
import com.mkt.activity.domain.ActivityDates;
import com.mkt.activity.domain.ContentHasher;
import com.mkt.activity.entity.ActActivityEntity;
import com.mkt.activity.support.ActivityErrorCodes;
import com.mkt.contract.RewardPort;
import com.mkt.kernel.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Draft helpers extracted from {@link ActivityAdminAppService} (R3-3).
 * Move-only: applyDraft / withHtml / validateDraft semantics unchanged.
 */
@Component
public class ActivityAdminDraft {

    private final ActivityAdminNormalize normalize;
    private final RewardPort rewards;

    @Autowired
    public ActivityAdminDraft(ActivityAdminNormalize normalize, RewardPort rewards) {
        this.normalize = normalize;
        this.rewards = rewards;
    }

    void validateDraft(ActActivityEntity entity) {
        ActivityDraftContent draft = ActivityFieldCodec.draft(entity.getDraftContent());
        if (draft == null) {
            throw new BusinessException(ActivityErrorCodes.HTML_EMPTY);
        }
        normalize.sanitizeHtml(draft.richText());
        if (draft.participationPrizeId() != null && !rewards.prizeEnabled(draft.participationPrizeId())) {
            throw new BusinessException(ActivityErrorCodes.PRIZE_INVALID);
        }
    }

    static ActivityDraftContent withHtml(ActivityDraftContent draft, String html) {
        return new ActivityDraftContent(
                draft.name(),
                draft.startTime(),
                draft.endTime(),
                html,
                draft.gray(),
                draft.submodules(),
                draft.participationPrizeId(),
                draft.allowUserIds(),
                draft.allowCrowdCodes(),
                draft.newUserOnly(),
                draft.newUserDays(),
                draft.userDailyLimit(),
                draft.userTotalLimit(),
                draft.globalDailyLimit(),
                draft.regions(),
                draft.scheduleOfflineAt());
    }

    void applyDraft(ActActivityEntity entity, ActivityDraftContent draft, boolean live) {
        if (!live) {
            return;
        }
        entity.setName(draft.name());
        entity.setStartTime(ActivityDates.toUtc(draft.startTime()));
        entity.setEndTime(ActivityDates.toUtc(draft.endTime()));
        entity.setRichText(draft.richText());
        entity.setContentHash(ContentHasher.sha256(draft.richText()));
        ActivityGrayCommand gray = normalize.normalizeGray(draft.gray());
        entity.setGrayType(gray.type());
        entity.setGrayRatio(gray.ratio());
        entity.setSubmodules(ActivityFieldCodec.json(normalize.normalizeSubmodules(draft.submodules())));
        entity.setParticipationPrizeId(draft.participationPrizeId());
        entity.setAllowUserIds(ActivityFieldCodec.json(draft.allowUserIds()));
        entity.setAllowCrowdCodes(ActivityFieldCodec.json(draft.allowCrowdCodes()));
        entity.setNewUserOnly(Boolean.TRUE.equals(draft.newUserOnly()) ? 1 : 0);
        entity.setNewUserDays(normalize.normalizeNewUserDays(draft.newUserDays()));
        entity.setUserDailyLimit(draft.userDailyLimit());
        entity.setUserTotalLimit(draft.userTotalLimit());
        entity.setGlobalDailyLimit(draft.globalDailyLimit());
        entity.setRegions(ActivityFieldCodec.json(draft.regions()));
    }
}
