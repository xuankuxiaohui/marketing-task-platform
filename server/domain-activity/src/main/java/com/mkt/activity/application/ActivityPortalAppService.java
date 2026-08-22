package com.mkt.activity.application;

import com.mkt.activity.command.ActivitySubmoduleCommand;
import com.mkt.activity.convert.ActivityFieldCodec;
import com.mkt.activity.domain.ActivityDates;
import com.mkt.activity.domain.ActivityStatuses;
import com.mkt.activity.domain.GrayBuckets;
import com.mkt.activity.domain.GrayTypes;
import com.mkt.activity.domain.HitRules;
import com.mkt.activity.domain.ParticipationRules;
import com.mkt.activity.entity.ActActivityEntity;
import com.mkt.activity.entity.ActParticipationEntity;
import com.mkt.activity.response.ParticipateResponse;
import com.mkt.activity.response.PortalActivityDetailView;
import com.mkt.activity.response.PortalActivityView;
import com.mkt.activity.response.SubmoduleView;
import com.mkt.activity.support.ActivityErrorCodes;
import com.mkt.contract.GrantContext;
import com.mkt.contract.GrantResult;
import com.mkt.contract.GrantSource;
import com.mkt.contract.RewardPort;
import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.kernel.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityPortalAppService {

    private final ActivityActivityStore activities;
    private final ActivityParticipationStore participations;
    private final RewardPort rewards;
    private final UserAttributePort users;
    private final Clock clock;

    public ActivityPortalAppService(
            ActivityActivityStore activities,
            ActivityParticipationStore participations,
            RewardPort rewards,
            UserAttributePort users,
            Clock clock) {
        this.activities = activities;
        this.participations = participations;
        this.rewards = rewards;
        this.users = users;
        this.clock = clock;
    }

    public List<PortalActivityView> listPublished(Long userId) {
        Instant now = clock.instant();
        List<PortalActivityView> out = new ArrayList<>();
        for (ActActivityEntity entity : activities.listPublished()) {
            if (visible(entity, userId, now)) {
                out.add(toCard(entity));
            }
        }
        return out;
    }

    public PortalActivityDetailView detail(long activityId, Long userId) {
        ActActivityEntity entity = requirePublished(activityId);
        Instant now = clock.instant();
        if (!visible(entity, userId, now)) {
            throw new BusinessException(ActivityErrorCodes.NOT_FOUND);
        }
        return toDetail(entity);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ParticipateResponse participate(long activityId, long userId) {
        return participate(activityId, userId, null, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ParticipateResponse participate(long activityId, long userId, String ip, String deviceId) {
        ActActivityEntity locked = activities.getByIdForUpdate(activityId);
        if (locked == null || locked.deletedFlag()) {
            throw new BusinessException(ActivityErrorCodes.NOT_FOUND);
        }
        if (!ActivityStatuses.PUBLISHED.equals(locked.getStatus())) {
            throw new BusinessException(ActivityErrorCodes.NOT_PUBLISHED);
        }
        Instant now = clock.instant();
        String periodKey = ActivityDates.periodKey(clock);
        LocalDate today = ActivityDates.today(clock);
        UserAttributes attrs = users.attributes(userId);
        String hit = precheck(locked, userId, now);
        if (hit == null) {
            int userDailyPass = participations.countPass(activityId, userId, periodKey);
            int userTotalPass = participations.countPass(activityId, userId, null);
            int globalPass = currentGlobalPass(locked, today);
            hit = ParticipationRules.firstReject(
                    ActivityFieldCodec.longs(locked.getAllowUserIds()),
                    ActivityFieldCodec.strings(locked.getAllowCrowdCodes()),
                    locked.newUserOnlyFlag(),
                    locked.newUserDaysOrDefault(),
                    locked.getUserDailyLimit(),
                    locked.getUserTotalLimit(),
                    locked.getGlobalDailyLimit(),
                    userDailyPass,
                    userTotalPass,
                    globalPass,
                    ActivityFieldCodec.strings(locked.getRegions()),
                    userId,
                    attrs,
                    now);
        }
        if (hit != null) {
            long id = insertRow(locked.getId(), userId, periodKey, HitRules.REJECT, hit, now);
            return new ParticipateResponse(id, HitRules.REJECT, false, null);
        }
        if (locked.getGlobalDailyLimit() != null) {
            bumpQuota(locked, today);
        }
        locked.setUpdatedAt(ActivityDates.toUtc(now));
        activities.update(locked);
        long id = insertRow(locked.getId(), userId, periodKey, HitRules.PASS, null, now);
        Long grantId = null;
        boolean granted = false;
        if (locked.getParticipationPrizeId() != null) {
            GrantResult result = rewards.grant(
                    locked.getParticipationPrizeId(),
                    userId,
                    GrantSource.ACTIVITY_PARTICIPATION,
                    String.valueOf(id),
                    GrantContext.defaults().withClient(ip, deviceId));
            grantId = result.recordId();
            granted = true;
        }
        return new ParticipateResponse(id, HitRules.PASS, granted, grantId);
    }

    private String precheck(ActActivityEntity entity, long userId, Instant now) {
        Instant start = ActivityDates.toInstant(entity.getStartTime());
        Instant end = ActivityDates.toInstant(entity.getEndTime());
        if (!ActivityDates.inWindow(start, end, now)) {
            return HitRules.WINDOW;
        }
        if (!GrayBuckets.hit(entity.getGrayType(), entity.getGrayRatio(), userId, entity.getId())) {
            return HitRules.GRAY;
        }
        return null;
    }

    private boolean visible(ActActivityEntity entity, Long userId, Instant now) {
        Instant start = ActivityDates.toInstant(entity.getStartTime());
        Instant end = ActivityDates.toInstant(entity.getEndTime());
        if (!ActivityDates.inWindow(start, end, now)) {
            return false;
        }
        if (userId == null) {
            return entity.getGrayType() == null || GrayTypes.NONE.equals(entity.getGrayType());
        }
        return GrayBuckets.hit(entity.getGrayType(), entity.getGrayRatio(), userId, entity.getId());
    }

    private int currentGlobalPass(ActActivityEntity locked, LocalDate today) {
        if (locked.getQuotaDay() == null || !today.equals(locked.getQuotaDay())) {
            return 0;
        }
        return locked.quotaCountOrZero();
    }

    private void bumpQuota(ActActivityEntity locked, LocalDate today) {
        if (locked.getQuotaDay() == null || !today.equals(locked.getQuotaDay())) {
            locked.setQuotaDay(today);
            locked.setQuotaCount(1);
            return;
        }
        locked.setQuotaCount(locked.quotaCountOrZero() + 1);
    }

    private long insertRow(long activityId, long userId, String periodKey, String result, String hitRule, Instant now) {
        ActParticipationEntity row = new ActParticipationEntity();
        row.setActivityId(activityId);
        row.setUserId(userId);
        row.setPeriodKey(periodKey);
        row.setResult(result);
        row.setHitRule(hitRule);
        row.setSimulated(0);
        row.setCreatedAt(ActivityDates.toUtc(now));
        participations.insert(row);
        return row.getId();
    }

    private ActActivityEntity requirePublished(long activityId) {
        ActActivityEntity entity = activities.getById(activityId);
        if (entity == null || entity.deletedFlag() || !ActivityStatuses.PUBLISHED.equals(entity.getStatus())) {
            throw new BusinessException(ActivityErrorCodes.NOT_FOUND);
        }
        return entity;
    }

    private static PortalActivityView toCard(ActActivityEntity entity) {
        return new PortalActivityView(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                ActivityDates.toInstant(entity.getStartTime()),
                ActivityDates.toInstant(entity.getEndTime()));
    }

    private static PortalActivityDetailView toDetail(ActActivityEntity entity) {
        List<SubmoduleView> subs = new ArrayList<>();
        int i = 0;
        for (ActivitySubmoduleCommand row : ActivityFieldCodec.submodules(entity.getSubmodules())) {
            int sort = row.sort() == null ? i : row.sort();
            subs.add(new SubmoduleView(row.type(), row.refId(), sort));
            i++;
        }
        return new PortalActivityDetailView(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                ActivityDates.toInstant(entity.getStartTime()),
                ActivityDates.toInstant(entity.getEndTime()),
                entity.getRichText(),
                entity.getContentHash(),
                entity.versionOrZero(),
                subs);
    }
}
