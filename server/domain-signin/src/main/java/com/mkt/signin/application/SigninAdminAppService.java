package com.mkt.signin.application;

import com.mkt.contract.RewardPort;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.signin.command.SigninActivitySaveCommand;
import com.mkt.signin.command.SigninPublishCommand;
import com.mkt.signin.command.SigninScheduleCommand;
import com.mkt.signin.command.SigninTierCommand;
import com.mkt.signin.convert.SigninSnapshotContent;
import com.mkt.signin.domain.ActivityCodes;
import com.mkt.signin.domain.ActivityStatuses;
import com.mkt.signin.domain.SigninDates;
import com.mkt.signin.entity.SgnActivityEntity;
import com.mkt.signin.entity.SgnActivitySnapshotEntity;
import com.mkt.signin.entity.SgnRecordEntity;
import com.mkt.signin.query.SigninActivityQuery;
import com.mkt.signin.query.SigninRecordQuery;
import com.mkt.signin.response.OkResponse;
import com.mkt.signin.response.SigninActivitySaveResponse;
import com.mkt.signin.response.SigninActivityView;
import com.mkt.signin.response.SigninPublishResponse;
import com.mkt.signin.response.SigninRecordView;
import com.mkt.signin.response.SigninTierView;
import com.mkt.signin.support.SigninErrorCodes;
import com.mkt.signin.support.SigninOperator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SigninAdminAppService {

    private final SigninActivityStore activities;
    private final SigninSnapshotStore snapshots;
    private final SigninRecordStore records;
    private final RewardPort rewards;
    private final Clock clock;

    public SigninAdminAppService(
            SigninActivityStore activities,
            SigninSnapshotStore snapshots,
            SigninRecordStore records,
            RewardPort rewards,
            Clock clock) {
        this.activities = activities;
        this.snapshots = snapshots;
        this.records = records;
        this.rewards = rewards;
        this.clock = clock;
    }

    public PageData<SigninActivityView> page(SigninActivityQuery query) {
        PageQuery page = query.page() == null ? PageQuery.of(null, null) : query.page();
        long total = activities.countByQuery(query.code(), query.name(), query.status());
        List<SigninActivityView> rows = activities
                .listByQuery(query.code(), query.name(), query.status(), page.offset(), page.pageSize())
                .stream()
                .map(this::toView)
                .toList();
        return new PageData<>(total, rows);
    }

    public SigninActivityView get(long id) {
        return toView(requireLive(id));
    }

    @Transactional
    public SigninActivitySaveResponse save(SigninActivitySaveCommand command) {
        List<SigninTierCommand> tiers = normalizeTiers(command.tiers());
        if (!ActivityCodes.valid(command.code())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "活动编码格式不正确");
        }
        if (!command.startTime().isBefore(command.endTime())) {
            throw new BusinessException(SigninErrorCodes.TIME_WINDOW_INVALID);
        }
        LocalDateTime now = SigninDates.toUtc(clock.instant());
        if (command.id() == null) {
            return insertNew(command, tiers, now);
        }
        return updateExisting(command, tiers, now);
    }

    @Transactional
    public OkResponse delete(long id) {
        SgnActivityEntity entity = requireLive(id);
        if (!ActivityStatuses.deletable(entity.getStatus())) {
            throw new BusinessException(SigninErrorCodes.PUBLISHED_NOT_DELETABLE);
        }
        entity.setDeleted(1);
        entity.setUpdatedAt(SigninDates.toUtc(clock.instant()));
        activities.update(entity);
        return OkResponse.yes();
    }

    @Transactional
    public SigninPublishResponse publish(long id, SigninPublishCommand command) {
        SigninPublishCommand cmd = command == null ? new SigninPublishCommand(null, null) : command;
        SgnActivityEntity entity = requireLive(id);
        if (entity.pendingFlag() && !cmd.confirmTrue()) {
            return SigninPublishResponse.preview(
                    entity.getId(), entity.getCode(), entity.versionOrZero(), entity.getStatus());
        }
        String status = entity.getStatus();
        if (ActivityStatuses.DRAFT.equals(status)) {
            freezeToPublished(entity);
        } else if (ActivityStatuses.SCHEDULED.equals(status)) {
            if (cmd.earlyTrue() || entity.pendingFlag()) {
                freezeToPublished(entity);
            } else {
                freezeToPublished(entity);
            }
        } else if (ActivityStatuses.PUBLISHED.equals(status)) {
            if (!entity.pendingFlag()) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "无待发布修订");
            }
            freezeToPublished(entity);
        } else {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "当前状态不可发布");
        }
        SgnActivityEntity fresh = requireLive(id);
        return SigninPublishResponse.done(fresh.getId(), fresh.getCode(), fresh.versionOrZero(), fresh.getStatus());
    }

    @Transactional
    public SigninPublishResponse schedule(long id, SigninScheduleCommand command) {
        SgnActivityEntity entity = requireLive(id);
        if (!ActivityStatuses.DRAFT.equals(entity.getStatus())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "仅草稿可设定时发布");
        }
        LocalDateTime publishAt = SigninDates.toUtc(command.publishAt());
        if (!publishAt.isAfter(SigninDates.toUtc(clock.instant()))) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "定时发布时间必须晚于当前时间");
        }
        validateTiers(editTiers(entity));
        entity.setSchedulePublishAt(publishAt);
        entity.setStatus(ActivityStatuses.SCHEDULED);
        entity.setPendingRevision(0);
        entity.setUpdatedAt(SigninDates.toUtc(clock.instant()));
        activities.update(entity);
        return SigninPublishResponse.done(entity.getId(), entity.getCode(), entity.versionOrZero(), entity.getStatus());
    }

    @Transactional
    public SigninPublishResponse offline(long id) {
        SgnActivityEntity entity = requireLive(id);
        if (!ActivityStatuses.PUBLISHED.equals(entity.getStatus())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "仅已发布活动可下线");
        }
        entity.setStatus(ActivityStatuses.OFFLINE);
        entity.setUpdatedAt(SigninDates.toUtc(clock.instant()));
        activities.update(entity);
        return SigninPublishResponse.done(entity.getId(), entity.getCode(), entity.versionOrZero(), entity.getStatus());
    }

    public int publishDue(int limit) {
        List<SgnActivityEntity> due = activities.listDueScheduled(SigninDates.toUtc(clock.instant()), limit);
        int n = 0;
        for (SgnActivityEntity entity : due) {
            try {
                publish(entity.getId(), new SigninPublishCommand(true, true));
                n++;
            } catch (RuntimeException ignored) {
                // leave SCHEDULED for the next scan
            }
        }
        return n;
    }

    public PageData<SigninRecordView> pageRecords(SigninRecordQuery query) {
        PageQuery page = query.page() == null ? PageQuery.of(null, null) : query.page();
        long total = records.countByQuery(query.activityId(), query.userId(), query.from(), query.to());
        List<SigninRecordView> rows = records
                .listByQuery(
                        query.activityId(), query.userId(), query.from(), query.to(), page.offset(), page.pageSize())
                .stream()
                .map(SigninAdminAppService::toRecordView)
                .toList();
        return new PageData<>(total, rows);
    }

    private SigninActivitySaveResponse insertNew(
            SigninActivitySaveCommand command, List<SigninTierCommand> tiers, LocalDateTime now) {
        if (activities.getByCode(command.code()) != null) {
            throw new BusinessException(SigninErrorCodes.CODE_DUPLICATE);
        }
        SgnActivityEntity entity = new SgnActivityEntity();
        applyHeader(command, entity);
        entity.setStatus(ActivityStatuses.DRAFT);
        entity.setVersion(0);
        entity.setPendingRevision(0);
        entity.setDeleted(0);
        entity.setCreatedBy(SigninOperator.requireUserId());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setDraftContent(JsonUtil.toJson(new SigninSnapshotContent(
                command.code(), command.name(), command.startTime(), command.endTime(), tiers)));
        try {
            activities.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(SigninErrorCodes.CODE_DUPLICATE, ex);
        }
        return new SigninActivitySaveResponse(entity.getId(), entity.getCode(), 0, entity.getStatus());
    }

    private SigninActivitySaveResponse updateExisting(
            SigninActivitySaveCommand command, List<SigninTierCommand> tiers, LocalDateTime now) {
        SgnActivityEntity existing = requireLive(command.id());
        if (!existing.getCode().equals(command.code())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "code 不可修改");
        }
        applyHeader(command, existing);
        if (ActivityStatuses.OFFLINE.equals(existing.getStatus())) {
            existing.setStatus(ActivityStatuses.DRAFT);
            existing.setPendingRevision(0);
        } else if (ActivityStatuses.publishedFamily(existing.getStatus())) {
            existing.setPendingRevision(1);
        }
        existing.setDraftContent(JsonUtil.toJson(new SigninSnapshotContent(
                command.code(), command.name(), command.startTime(), command.endTime(), tiers)));
        existing.setUpdatedAt(now);
        activities.update(existing);
        return new SigninActivitySaveResponse(
                existing.getId(), existing.getCode(), existing.versionOrZero(), existing.getStatus());
    }

    private void freezeToPublished(SgnActivityEntity entity) {
        List<SigninTierCommand> tiers = editTiers(entity);
        validateTiers(tiers);
        int nextVersion = entity.versionOrZero() + 1;
        int expectedPending = entity.pendingFlag() ? 1 : 0;
        String expectedStatus = entity.getStatus();
        LocalDateTime now = SigninDates.toUtc(clock.instant());
        SgnActivitySnapshotEntity snap = new SgnActivitySnapshotEntity();
        snap.setActivityId(entity.getId());
        snap.setVersion(nextVersion);
        snap.setContent(JsonUtil.toJson(new SigninSnapshotContent(
                entity.getCode(),
                entity.getName(),
                SigninDates.toInstant(entity.getStartTime()),
                SigninDates.toInstant(entity.getEndTime()),
                tiers)));
        snap.setPublishedAt(now);
        snap.setPublishedBy(SigninOperator.optionalUserId());
        snapshots.insert(snap);
        int cas = activities.casPublish(
                entity.getId(), expectedStatus, entity.versionOrZero(), expectedPending, nextVersion, now);
        if (cas != 1) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "发布冲突，请重试");
        }
    }

    private void applyHeader(SigninActivitySaveCommand command, SgnActivityEntity entity) {
        entity.setCode(command.code());
        entity.setName(command.name());
        entity.setStartTime(SigninDates.toUtc(command.startTime()));
        entity.setEndTime(SigninDates.toUtc(command.endTime()));
    }

    private List<SigninTierCommand> editTiers(SgnActivityEntity entity) {
        if (entity.getDraftContent() != null && !entity.getDraftContent().isBlank()) {
            SigninSnapshotContent content = JsonUtil.fromJson(entity.getDraftContent(), SigninSnapshotContent.class);
            return content.tiers() == null ? List.of() : content.tiers();
        }
        if (entity.versionOrZero() > 0) {
            SgnActivitySnapshotEntity live = snapshots.getByActivityAndVersion(entity.getId(), entity.versionOrZero());
            if (live != null) {
                SigninSnapshotContent content = JsonUtil.fromJson(live.getContent(), SigninSnapshotContent.class);
                return content.tiers() == null ? List.of() : content.tiers();
            }
        }
        return List.of();
    }

    private List<SigninTierCommand> normalizeTiers(List<SigninTierCommand> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new BusinessException(SigninErrorCodes.TIER_INVALID);
        }
        List<SigninTierCommand> sorted = new ArrayList<>(raw);
        sorted.sort(Comparator.comparingInt(SigninTierCommand::day));
        Set<Integer> days = new HashSet<>();
        for (SigninTierCommand tier : sorted) {
            if (tier.day() == null || tier.prizeId() == null || tier.day() < 1) {
                throw new BusinessException(SigninErrorCodes.TIER_INVALID);
            }
            if (!days.add(tier.day())) {
                throw new BusinessException(SigninErrorCodes.TIER_INVALID);
            }
        }
        return List.copyOf(sorted);
    }

    private void validateTiers(List<SigninTierCommand> tiers) {
        List<SigninTierCommand> normalized = normalizeTiers(tiers);
        for (SigninTierCommand tier : normalized) {
            if (!rewards.prizeEnabled(tier.prizeId())) {
                throw new BusinessException(SigninErrorCodes.PRIZE_INVALID);
            }
        }
    }

    private SigninActivityView toView(SgnActivityEntity entity) {
        List<SigninTierView> tiers = editTiers(entity).stream()
                .map(tier -> new SigninTierView(tier.day(), tier.prizeId()))
                .toList();
        return new SigninActivityView(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                SigninDates.toInstant(entity.getStartTime()),
                SigninDates.toInstant(entity.getEndTime()),
                entity.getStatus(),
                entity.versionOrZero(),
                entity.pendingFlag(),
                SigninDates.toInstant(entity.getSchedulePublishAt()),
                tiers);
    }

    private static SigninRecordView toRecordView(SgnRecordEntity row) {
        Instant created = row.getCreatedAt() == null ? null : SigninDates.toInstant(row.getCreatedAt());
        return new SigninRecordView(
                row.getId(), row.getActivityId(), row.getUserId(), row.getSignDate(), row.getSource(), created);
    }

    private SgnActivityEntity requireLive(long id) {
        SgnActivityEntity entity = activities.getById(id);
        if (entity == null || entity.deletedFlag()) {
            throw new BusinessException(SigninErrorCodes.NOT_FOUND);
        }
        return entity;
    }
}
