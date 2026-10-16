package com.mkt.activity.application;

import com.mkt.activity.convert.ActivityFieldCodec;
import com.mkt.activity.domain.ActivityDates;
import com.mkt.activity.domain.ActivityStatuses;
import com.mkt.activity.domain.GrayBuckets;
import com.mkt.activity.domain.HitRules;
import com.mkt.activity.domain.ParticipationRules;
import com.mkt.activity.entity.ActActivityEntity;
import com.mkt.activity.entity.ActParticipationEntity;
import com.mkt.activity.response.ParticipateResponse;
import com.mkt.activity.support.ActivityErrorCodes;
import com.mkt.contract.CrowdPort;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Participate entries extracted from {@link ActivityPortalAppService} (R30-1).
 * Move-only: participate semantics unchanged.
 */
@Component
public class ActivityPortalParticipate {

    private final ActivityActivityStore activities;
    private final ActivityParticipationStore participations;
    private final RewardPort rewards;
    private final UserAttributePort users;
    private final CrowdPort crowds;
    private final Clock clock;

    @Autowired
    public ActivityPortalParticipate(
            ActivityActivityStore activities,
            ActivityParticipationStore participations,
            RewardPort rewards,
            UserAttributePort users,
            CrowdPort crowds,
            Clock clock) {
        this.activities = activities;
        this.participations = participations;
        this.rewards = rewards;
        this.users = users;
        this.crowds = crowds;
        this.clock = clock;
    }

    ParticipateResponse participate(long activityId, long userId) {
        return participate(activityId, userId, null, null);
    }

    ParticipateResponse participate(long activityId, long userId, String ip, String deviceId) {
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
                    now,
                    codes -> crowds.memberOfAny(codes, userId));
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
}
