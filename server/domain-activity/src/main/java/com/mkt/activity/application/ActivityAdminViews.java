package com.mkt.activity.application;

import com.mkt.activity.command.ActivityGrayCommand;
import com.mkt.activity.command.ActivitySubmoduleCommand;
import com.mkt.activity.convert.ActivityDraftContent;
import com.mkt.activity.convert.ActivityFieldCodec;
import com.mkt.activity.domain.ActivityDates;
import com.mkt.activity.entity.ActActivityEntity;
import com.mkt.activity.entity.ActParticipationEntity;
import com.mkt.activity.response.ActivityGrayView;
import com.mkt.activity.response.ActivityView;
import com.mkt.activity.response.ParticipationView;
import com.mkt.activity.response.SubmoduleView;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * View helpers extracted from {@link ActivityAdminAppService} (R3-2).
 * Move-only: toView / toParticipationView semantics unchanged.
 */
@Component
public class ActivityAdminViews {

    ActivityView toView(ActActivityEntity entity) {
        ActivityDraftContent draft = entity.pendingFlag() ? ActivityFieldCodec.draft(entity.getDraftContent()) : null;
        String name = draft != null && draft.name() != null ? draft.name() : entity.getName();
        Instant start = draft != null && draft.startTime() != null
                ? draft.startTime()
                : ActivityDates.toInstant(entity.getStartTime());
        Instant end = draft != null && draft.endTime() != null
                ? draft.endTime()
                : ActivityDates.toInstant(entity.getEndTime());
        String html = draft != null && draft.richText() != null ? draft.richText() : entity.getRichText();
        ActivityGrayCommand grayCmd = draft != null && draft.gray() != null
                ? draft.gray()
                : ActivityFieldCodec.gray(entity.getGrayType(), entity.getGrayRatio());
        List<ActivitySubmoduleCommand> subs = draft != null && draft.submodules() != null
                ? draft.submodules()
                : ActivityFieldCodec.submodules(entity.getSubmodules());
        List<Long> allowUsers = draft != null && draft.allowUserIds() != null
                ? draft.allowUserIds()
                : ActivityFieldCodec.longs(entity.getAllowUserIds());
        List<String> allowCrowds = draft != null && draft.allowCrowdCodes() != null
                ? draft.allowCrowdCodes()
                : ActivityFieldCodec.strings(entity.getAllowCrowdCodes());
        boolean newUserOnly = draft != null && draft.newUserOnly() != null
                ? draft.newUserOnly()
                : entity.newUserOnlyFlag();
        int newUserDays = draft != null && draft.newUserDays() != null
                ? draft.newUserDays()
                : entity.newUserDaysOrDefault();
        Integer userDaily = draft != null ? draft.userDailyLimit() : entity.getUserDailyLimit();
        Integer userTotal = draft != null ? draft.userTotalLimit() : entity.getUserTotalLimit();
        Integer globalDaily = draft != null ? draft.globalDailyLimit() : entity.getGlobalDailyLimit();
        List<String> regions = draft != null && draft.regions() != null
                ? draft.regions()
                : ActivityFieldCodec.strings(entity.getRegions());
        Long prizeId = draft != null ? draft.participationPrizeId() : entity.getParticipationPrizeId();
        List<SubmoduleView> submoduleViews = new ArrayList<>();
        int i = 0;
        for (ActivitySubmoduleCommand row : subs) {
            int sort = row.sort() == null ? i : row.sort();
            submoduleViews.add(new SubmoduleView(row.type(), row.refId(), sort));
            i++;
        }
        return new ActivityView(
                entity.getId(),
                entity.getCode(),
                name,
                start,
                end,
                entity.getStatus(),
                entity.versionOrZero(),
                entity.pendingFlag(),
                ActivityDates.toInstant(entity.getSchedulePublishAt()),
                ActivityDates.toInstant(entity.getScheduleOfflineAt()),
                html,
                entity.getContentHash(),
                new ActivityGrayView(grayCmd.type(), grayCmd.ratio()),
                submoduleViews,
                prizeId,
                allowUsers,
                allowCrowds,
                newUserOnly,
                newUserDays,
                userDaily,
                userTotal,
                globalDaily,
                regions);
    }

    static ParticipationView toParticipationView(ActParticipationEntity row) {
        Instant created = row.getCreatedAt() == null ? null : ActivityDates.toInstant(row.getCreatedAt());
        return new ParticipationView(
                row.getId(),
                row.getActivityId(),
                row.getUserId(),
                row.getPeriodKey(),
                row.getResult(),
                row.getHitRule(),
                created);
    }
}
