package com.mkt.activity.application;

import com.mkt.activity.command.ActivitySubmoduleCommand;
import com.mkt.activity.convert.ActivityFieldCodec;
import com.mkt.activity.convert.ActivityOwnership;
import com.mkt.activity.domain.ActivityDates;
import com.mkt.activity.domain.ActivityStatuses;
import com.mkt.activity.domain.GrayBuckets;
import com.mkt.activity.domain.GrayTypes;
import com.mkt.activity.entity.ActActivityEntity;
import com.mkt.activity.entity.ActParticipationEntity;
import com.mkt.activity.response.ParticipateResponse;
import com.mkt.activity.response.PortalActivityDetailView;
import com.mkt.activity.response.PortalActivityView;
import com.mkt.activity.response.SubmoduleView;
import com.mkt.activity.support.ActivityErrorCodes;
import com.mkt.contract.CrowdPort;
import com.mkt.contract.RewardPort;
import com.mkt.contract.UserAttributePort;
import com.mkt.kernel.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityPortalAppService {

    private final ActivityActivityStore activities;
    private final ActivityParticipationStore participations;
    private final Clock clock;
    private final ActivityPortalParticipate activityPortalParticipate;

    @Autowired
    public ActivityPortalAppService(
            ActivityActivityStore activities,
            ActivityParticipationStore participations,
            RewardPort rewards,
            UserAttributePort users,
            CrowdPort crowds,
            Clock clock,
            ActivityPortalParticipate activityPortalParticipate) {
        this.activities = activities;
        this.participations = participations;
        this.clock = clock;
        // Manual/IT chain passes null. Production passes the injected collaborator.
        this.activityPortalParticipate = activityPortalParticipate != null
                ? activityPortalParticipate
                : new ActivityPortalParticipate(activities, participations, rewards, users, crowds, clock);
    }

    public ActivityPortalAppService(
            ActivityActivityStore activities,
            ActivityParticipationStore participations,
            RewardPort rewards,
            UserAttributePort users,
            CrowdPort crowds,
            Clock clock) {
        this(activities, participations, rewards, users, crowds, clock, null);
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

    public List<ActivityOwnership.Ref> ownershipForTask(long taskId) {
        return ActivityOwnership.forTask(taskId, ownershipCatalog());
    }

    public ActivityOwnership.Ref ownershipForParticipation(long participationId) {
        ActParticipationEntity row = participations.getById(participationId);
        if (row == null || row.getActivityId() == null) {
            return null;
        }
        ActActivityEntity entity = activities.getById(row.getActivityId());
        if (entity == null || entity.deletedFlag()) {
            return null;
        }
        return new ActivityOwnership.Ref(entity.getId(), entity.getName());
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
        return activityPortalParticipate.participate(activityId, userId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ParticipateResponse participate(long activityId, long userId, String ip, String deviceId) {
        return activityPortalParticipate.participate(activityId, userId, ip, deviceId);
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

    private ActActivityEntity requirePublished(long activityId) {
        ActActivityEntity entity = activities.getById(activityId);
        if (entity == null || entity.deletedFlag() || !ActivityStatuses.PUBLISHED.equals(entity.getStatus())) {
            throw new BusinessException(ActivityErrorCodes.NOT_FOUND);
        }
        return entity;
    }

    private List<ActivityOwnership.Module> ownershipCatalog() {
        List<ActivityOwnership.Module> out = new ArrayList<>();
        addOwnershipModules(out, activities.listPublished());
        addOwnershipModules(out, activities.listByQuery(null, null, ActivityStatuses.OFFLINE, 0, 500));
        return out;
    }

    private static void addOwnershipModules(List<ActivityOwnership.Module> out, List<ActActivityEntity> rows) {
        if (rows == null) {
            return;
        }
        for (ActActivityEntity entity : rows) {
            if (entity == null || entity.deletedFlag()) {
                continue;
            }
            out.add(new ActivityOwnership.Module(
                    entity.getId(), entity.getName(), ActivityFieldCodec.submodules(entity.getSubmodules())));
        }
    }

    private static PortalActivityView toCard(ActActivityEntity entity) {
        List<SubmoduleView> subs = submoduleViews(entity);
        return new PortalActivityView(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                ActivityDates.toInstant(entity.getStartTime()),
                ActivityDates.toInstant(entity.getEndTime()),
                subs);
    }

    private static List<SubmoduleView> submoduleViews(ActActivityEntity entity) {
        List<SubmoduleView> subs = new ArrayList<>();
        int i = 0;
        for (ActivitySubmoduleCommand row : ActivityFieldCodec.submodules(entity.getSubmodules())) {
            int sort = row.sort() == null ? i : row.sort();
            subs.add(new SubmoduleView(row.type(), row.refId(), sort));
            i++;
        }
        return subs;
    }

    private static PortalActivityDetailView toDetail(ActActivityEntity entity) {
        List<SubmoduleView> subs = submoduleViews(entity);
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
