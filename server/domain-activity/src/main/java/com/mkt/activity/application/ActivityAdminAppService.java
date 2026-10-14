package com.mkt.activity.application;

import com.mkt.activity.command.ActivityGrayCommand;
import com.mkt.activity.command.ActivityPublishCommand;
import com.mkt.activity.command.ActivitySaveCommand;
import com.mkt.activity.command.ActivityScheduleCommand;
import com.mkt.activity.command.ActivitySubmoduleCommand;
import com.mkt.activity.convert.ActivityDraftContent;
import com.mkt.activity.convert.ActivityFieldCodec;
import com.mkt.activity.domain.ActivityCodes;
import com.mkt.activity.domain.ActivityDates;
import com.mkt.activity.domain.ActivityStatuses;
import com.mkt.activity.entity.ActActivityEntity;
import com.mkt.activity.query.ActivityQuery;
import com.mkt.activity.query.ParticipationQuery;
import com.mkt.activity.response.ActivityPublishResponse;
import com.mkt.activity.response.ActivitySaveResponse;
import com.mkt.activity.response.ActivityView;
import com.mkt.activity.response.HitRuleCountView;
import com.mkt.activity.response.OkResponse;
import com.mkt.activity.response.ParticipationStatsView;
import com.mkt.activity.response.ParticipationView;
import com.mkt.activity.support.ActivityErrorCodes;
import com.mkt.activity.support.ActivityOperator;
import com.mkt.activity.support.ActivitySettings;
import com.mkt.contract.RewardPort;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.json.JsonUtil;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityAdminAppService {

    private final ActivityActivityStore activities;
    private final ActivityParticipationStore participations;
    private final RewardPort rewards;
    private final Clock clock;
    private final ActivityAdminNormalize normalize;
    private final ActivityAdminViews views;
    private final ActivityAdminDraft drafts;

    @Autowired
    public ActivityAdminAppService(
            ActivityActivityStore activities,
            ActivityParticipationStore participations,
            RewardPort rewards,
            Clock clock,
            ActivityAdminNormalize normalize,
            ActivityAdminViews views,
            ActivityAdminDraft drafts) {
        this.activities = activities;
        this.participations = participations;
        this.rewards = rewards;
        this.clock = clock;
        this.normalize = normalize;
        this.views = views;
        this.drafts = drafts;
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
                new ActivityAdminDraft(new ActivityAdminNormalize(settings), rewards));
    }

    public PageData<ActivityView> page(ActivityQuery query) {
        PageQuery page = query.page() == null ? PageQuery.of(null, null) : query.page();
        long total = activities.countByQuery(query.code(), query.name(), query.status());
        List<ActivityView> rows = activities
                .listByQuery(query.code(), query.name(), query.status(), page.offset(), page.pageSize())
                .stream()
                .map(views::toView)
                .toList();
        return new PageData<>(total, rows);
    }

    public ActivityView get(long id) {
        return views.toView(requireLive(id));
    }

    @Transactional
    public ActivitySaveResponse save(ActivitySaveCommand command) {
        String html = normalize.sanitizeHtml(command.richText());
        ActivityGrayCommand gray = normalize.normalizeGray(command.gray());
        List<ActivitySubmoduleCommand> submodules = normalize.normalizeSubmodules(command.submodules());
        if (!ActivityCodes.valid(command.code())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "活动编码格式不正确");
        }
        if (!command.startTime().isBefore(command.endTime())) {
            throw new BusinessException(ActivityErrorCodes.TIME_WINDOW_INVALID);
        }
        int newUserDays = normalize.normalizeNewUserDays(command.newUserDays());
        ActivityAdminNormalize.validateLimits(
                command.userDailyLimit(), command.userTotalLimit(), command.globalDailyLimit());
        if (command.participationPrizeId() != null && !rewards.prizeEnabled(command.participationPrizeId())) {
            throw new BusinessException(ActivityErrorCodes.PRIZE_INVALID);
        }
        LocalDateTime now = ActivityDates.toUtc(clock.instant());
        ActivityDraftContent draft = new ActivityDraftContent(
                command.name(),
                command.startTime(),
                command.endTime(),
                html,
                gray,
                submodules,
                command.participationPrizeId(),
                command.allowUserIds() == null ? List.of() : List.copyOf(command.allowUserIds()),
                command.allowCrowdCodes() == null ? List.of() : List.copyOf(command.allowCrowdCodes()),
                Boolean.TRUE.equals(command.newUserOnly()),
                newUserDays,
                command.userDailyLimit(),
                command.userTotalLimit(),
                command.globalDailyLimit(),
                command.regions() == null ? List.of() : List.copyOf(command.regions()),
                null);
        if (command.id() == null) {
            return insertNew(command, draft, now);
        }
        return updateExisting(command, draft, now);
    }

    @Transactional
    public OkResponse delete(long id) {
        ActActivityEntity entity = requireLive(id);
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
            freezeToPublished(entity);
        } else {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "当前状态不可发布");
        }
        ActActivityEntity fresh = requireLive(id);
        return ActivityPublishResponse.done(
                fresh.getId(), fresh.getCode(), fresh.versionOrZero(), fresh.getStatus());
    }

    @Transactional
    public ActivityPublishResponse schedule(long id, ActivityScheduleCommand command) {
        ActActivityEntity entity = requireLive(id);
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

    @Transactional
    public ActivityPublishResponse offline(long id) {
        ActActivityEntity entity = requireLive(id);
        if (!ActivityStatuses.PUBLISHED.equals(entity.getStatus())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "仅已发布活动可下线");
        }
        entity.setStatus(ActivityStatuses.OFFLINE);
        entity.setScheduleOfflineAt(null);
        entity.setUpdatedAt(ActivityDates.toUtc(clock.instant()));
        activities.update(entity);
        return ActivityPublishResponse.done(entity.getId(), entity.getCode(), entity.versionOrZero(), entity.getStatus());
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

    public ParticipationStatsView stats(long activityId) {
        requireLive(activityId);
        long total = participations.countAll(activityId);
        long pass = participations.countPassByActivity(activityId);
        long reject = total - pass;
        double rate = total == 0 ? 0d : (double) pass / (double) total;
        List<HitRuleCountView> reasons = participations.countRejectByRule(activityId);
        return new ParticipationStatsView(total, pass, reject, rate, reasons);
    }

    private ActivitySaveResponse insertNew(ActivitySaveCommand command, ActivityDraftContent draft, LocalDateTime now) {
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

    private ActivitySaveResponse updateExisting(
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

    private void freezeToPublished(ActActivityEntity entity) {
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

    private ActActivityEntity requireLive(long id) {
        ActActivityEntity entity = activities.getById(id);
        if (entity == null || entity.deletedFlag()) {
            throw new BusinessException(ActivityErrorCodes.NOT_FOUND);
        }
        return entity;
    }
}
