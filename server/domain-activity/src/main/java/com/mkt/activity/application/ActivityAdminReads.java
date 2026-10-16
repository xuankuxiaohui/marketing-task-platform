package com.mkt.activity.application;

import com.mkt.activity.query.ActivityQuery;
import com.mkt.activity.query.ParticipationQuery;
import com.mkt.activity.response.ActivityView;
import com.mkt.activity.response.HitRuleCountView;
import com.mkt.activity.response.ParticipationStatsView;
import com.mkt.activity.response.ParticipationView;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Read entries extracted from {@link ActivityAdminAppService} (R29-3).
 * Move-only: page / get / pageParticipations / stats semantics unchanged.
 */
@Component
public class ActivityAdminReads {

    private final ActivityActivityStore activities;
    private final ActivityParticipationStore participations;
    private final ActivityAdminViews views;
    private final ActivityAdminPersist persist;

    @Autowired
    public ActivityAdminReads(
            ActivityActivityStore activities,
            ActivityParticipationStore participations,
            ActivityAdminViews views,
            ActivityAdminPersist persist) {
        this.activities = activities;
        this.participations = participations;
        this.views = views;
        this.persist = persist;
    }

    PageData<ActivityView> page(ActivityQuery query) {
        PageQuery page = query.page() == null ? PageQuery.of(null, null) : query.page();
        long total = activities.countByQuery(query.code(), query.name(), query.status());
        List<ActivityView> rows = activities
                .listByQuery(query.code(), query.name(), query.status(), page.offset(), page.pageSize())
                .stream()
                .map(views::toView)
                .toList();
        return new PageData<>(total, rows);
    }

    ActivityView get(long id) {
        return views.toView(persist.requireLive(id));
    }

    PageData<ParticipationView> pageParticipations(ParticipationQuery query) {
        PageQuery page = query.page() == null ? PageQuery.of(null, null) : query.page();
        long total = participations.countByQuery(query.activityId(), query.userId(), query.result(), query.periodKey());
        List<ParticipationView> rows = participations
                .listByQuery(
                        query.activityId(),
                        query.userId(),
                        query.result(),
                        query.periodKey(),
                        page.offset(),
                        page.pageSize())
                .stream()
                .map(ActivityAdminViews::toParticipationView)
                .toList();
        return new PageData<>(total, rows);
    }

    ParticipationStatsView stats(long activityId) {
        persist.requireLive(activityId);
        long total = participations.countAll(activityId);
        long pass = participations.countPassByActivity(activityId);
        long reject = total - pass;
        double rate = total == 0 ? 0d : (double) pass / (double) total;
        List<HitRuleCountView> reasons = participations.countRejectByRule(activityId);
        return new ParticipationStatsView(total, pass, reject, rate, reasons);
    }
}
