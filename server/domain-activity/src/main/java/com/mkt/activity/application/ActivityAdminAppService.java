package com.mkt.activity.application;

import com.mkt.activity.command.ActivityPublishCommand;
import com.mkt.activity.command.ActivitySaveCommand;
import com.mkt.activity.command.ActivityScheduleCommand;
import com.mkt.activity.domain.ActivityDates;
import com.mkt.activity.domain.ActivityStatuses;
import com.mkt.activity.entity.ActActivityEntity;
import com.mkt.activity.query.ActivityQuery;
import com.mkt.activity.query.ParticipationQuery;
import com.mkt.activity.response.ActivityPublishResponse;
import com.mkt.activity.response.ActivitySaveResponse;
import com.mkt.activity.response.ActivityView;
import com.mkt.activity.response.OkResponse;
import com.mkt.activity.response.ParticipationStatsView;
import com.mkt.activity.response.ParticipationView;
import com.mkt.activity.support.ActivityErrorCodes;
import com.mkt.activity.support.ActivitySettings;
import com.mkt.contract.RewardPort;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.PageData;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityAdminAppService {

    private final ActivityActivityStore activities;
    private final Clock clock;
    private final ActivityAdminPersist persist;
    private final ActivityAdminSave activityAdminSave;
    private final ActivityAdminLifecycle activityAdminLifecycle;
    private final ActivityAdminReads activityAdminReads;

    @Autowired
    public ActivityAdminAppService(
            ActivityActivityStore activities,
            ActivityParticipationStore participations,
            RewardPort rewards,
            Clock clock,
            ActivityAdminNormalize normalize,
            ActivityAdminViews views,
            ActivityAdminDraft drafts,
            ActivityAdminPersist persist,
            ActivityAdminSave activityAdminSave,
            ActivityAdminLifecycle activityAdminLifecycle,
            ActivityAdminReads activityAdminReads) {
        this.activities = activities;
        this.clock = clock;
        this.persist = persist;
        // Manual/IT chain passes null. Production passes the injected collaborator.
        this.activityAdminSave = activityAdminSave != null
                ? activityAdminSave
                : new ActivityAdminSave(normalize, rewards, clock, persist);
        this.activityAdminLifecycle = activityAdminLifecycle != null
                ? activityAdminLifecycle
                : new ActivityAdminLifecycle(activities, persist, drafts, clock);
        this.activityAdminReads = activityAdminReads != null
                ? activityAdminReads
                : new ActivityAdminReads(activities, participations, views, persist);
    }

    public ActivityAdminAppService(
            ActivityActivityStore activities,
            ActivityParticipationStore participations,
            RewardPort rewards,
            ActivitySettings settings,
            Clock clock) {
        this(
                activities,
                participations,
                rewards,
                clock,
                new ActivityAdminNormalize(settings),
                new ActivityAdminViews(),
                new ActivityAdminDraft(new ActivityAdminNormalize(settings), rewards),
                new ActivityAdminPersist(
                        activities,
                        new ActivityAdminDraft(new ActivityAdminNormalize(settings), rewards),
                        new ActivityAdminNormalize(settings),
                        rewards,
                        clock),
                null,
                null,
                null);
    }

    public PageData<ActivityView> page(ActivityQuery query) {
        return activityAdminReads.page(query);
    }

    public ActivityView get(long id) {
        return activityAdminReads.get(id);
    }

    @Transactional
    public ActivitySaveResponse save(ActivitySaveCommand command) {
        return activityAdminSave.save(command);
    }

    @Transactional
    public OkResponse delete(long id) {
        ActActivityEntity entity = persist.requireLive(id);
        if (!ActivityStatuses.deletable(entity.getStatus())) {
            throw new BusinessException(ActivityErrorCodes.PUBLISHED_NOT_DELETABLE);
        }
        entity.setDeleted(1);
        entity.setUpdatedAt(ActivityDates.toUtc(clock.instant()));
        activities.update(entity);
        return OkResponse.yes();
    }

    @Transactional
    public ActivityPublishResponse publish(long id, ActivityPublishCommand command) {
        return activityAdminLifecycle.publish(id, command);
    }

    @Transactional
    public ActivityPublishResponse schedule(long id, ActivityScheduleCommand command) {
        return activityAdminLifecycle.schedule(id, command);
    }

    @Transactional
    public ActivityPublishResponse offline(long id) {
        return activityAdminLifecycle.offline(id);
    }

    public int publishDue(int limit) {
        List<ActActivityEntity> due = activities.listDueScheduled(ActivityDates.toUtc(clock.instant()), limit);
        int n = 0;
        for (ActActivityEntity entity : due) {
            try {
                publish(entity.getId(), new ActivityPublishCommand(true, true));
                n++;
            } catch (RuntimeException ignored) {
                // leave SCHEDULED for the next scan
            }
        }
        return n;
    }

    public int offlineDue(int limit) {
        List<ActActivityEntity> due = activities.listDueOffline(ActivityDates.toUtc(clock.instant()), limit);
        int n = 0;
        for (ActActivityEntity entity : due) {
            try {
                offline(entity.getId());
                n++;
            } catch (RuntimeException ignored) {
                // leave PUBLISHED for the next scan
            }
        }
        return n;
    }

    public PageData<ParticipationView> pageParticipations(ParticipationQuery query) {
        return activityAdminReads.pageParticipations(query);
    }

    public ParticipationStatsView stats(long activityId) {
        return activityAdminReads.stats(activityId);
    }
}
