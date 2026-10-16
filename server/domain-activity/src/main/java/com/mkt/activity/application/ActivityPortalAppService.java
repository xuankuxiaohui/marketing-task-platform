package com.mkt.activity.application;

import com.mkt.activity.convert.ActivityOwnership;
import com.mkt.activity.response.ParticipateResponse;
import com.mkt.activity.response.PortalActivityDetailView;
import com.mkt.activity.response.PortalActivityView;
import com.mkt.contract.CrowdPort;
import com.mkt.contract.RewardPort;
import com.mkt.contract.UserAttributePort;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityPortalAppService {

    private final ActivityPortalParticipate activityPortalParticipate;
    private final ActivityPortalReads activityPortalReads;

    @Autowired
    public ActivityPortalAppService(
            ActivityActivityStore activities,
            ActivityParticipationStore participations,
            RewardPort rewards,
            UserAttributePort users,
            CrowdPort crowds,
            Clock clock,
            ActivityPortalParticipate activityPortalParticipate,
            ActivityPortalReads activityPortalReads) {
        // Manual/IT chain passes null. Production passes the injected collaborator.
        this.activityPortalParticipate = activityPortalParticipate != null
                ? activityPortalParticipate
                : new ActivityPortalParticipate(activities, participations, rewards, users, crowds, clock);
        this.activityPortalReads = activityPortalReads != null
                ? activityPortalReads
                : new ActivityPortalReads(activities, participations, clock);
    }

    public ActivityPortalAppService(
            ActivityActivityStore activities,
            ActivityParticipationStore participations,
            RewardPort rewards,
            UserAttributePort users,
            CrowdPort crowds,
            Clock clock) {
        this(activities, participations, rewards, users, crowds, clock, null, null);
    }

    public List<PortalActivityView> listPublished(Long userId) {
        return activityPortalReads.listPublished(userId);
    }

    public List<ActivityOwnership.Ref> ownershipForTask(long taskId) {
        return activityPortalReads.ownershipForTask(taskId);
    }

    public ActivityOwnership.Ref ownershipForParticipation(long participationId) {
        return activityPortalReads.ownershipForParticipation(participationId);
    }

    public PortalActivityDetailView detail(long activityId, Long userId) {
        return activityPortalReads.detail(activityId, userId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ParticipateResponse participate(long activityId, long userId) {
        return activityPortalParticipate.participate(activityId, userId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ParticipateResponse participate(long activityId, long userId, String ip, String deviceId) {
        return activityPortalParticipate.participate(activityId, userId, ip, deviceId);
    }
}
