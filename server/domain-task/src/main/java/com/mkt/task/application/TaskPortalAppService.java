package com.mkt.task.application;

import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.RiskListType;
import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.contract.UserRiskSummary;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.convert.SnapshotViews;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.domain.CycleKeyResolver;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.domain.StepStatuses;
import com.mkt.task.domain.VisibilityEvaluator;
import com.mkt.task.domain.VisibilityResult;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.expression.CrowdResolver;
import com.mkt.task.expression.EvalContexts;
import com.mkt.task.response.CurrentStepView;
import com.mkt.task.response.InstanceStepView;
import com.mkt.task.response.MineTaskView;
import com.mkt.task.response.TaskBriefView;
import com.mkt.task.response.TaskCardView;
import com.mkt.task.response.TaskDetailResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TaskPortalAppService {

    private final TaskDefinitionStore definitions;
    private final TaskVersionSnapshotStore snapshots;
    private final TaskInstanceStore instances;
    private final TaskCrowdStore crowds;
    private final UserAttributePort users;
    private final RiskCheckPort risk;
    private final Clock clock;

    @Autowired
    public TaskPortalAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            ObjectProvider<UserAttributePort> users,
            ObjectProvider<RiskCheckPort> risk,
            Clock clock) {
        this(
                definitions,
                snapshots,
                instances,
                crowds,
                users.getIfAvailable(),
                risk.getIfAvailable(),
                clock);
    }

    public TaskPortalAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            UserAttributePort users,
            RiskCheckPort risk,
            Clock clock) {
        this.definitions = definitions;
        this.snapshots = snapshots;
        this.instances = instances;
        this.crowds = crowds;
        this.users = users;
        this.risk = risk;
        this.clock = clock;
    }

    public PageData<TaskCardView> list(long userId, String category, Integer page, Integer pageSize) {
        if (users == null) {
            return new PageData<>(0, List.of());
        }
        if (blacklisted(userId)) {
            return new PageData<>(0, List.of());
        }
        UserAttributes attrs = users.attributes(userId);
        Instant now = clock.instant();
        List<TaskDefinitionEntity> published = definitions.listPublished();
        List<TaskCardView> cards = new ArrayList<>();
        for (TaskDefinitionEntity definition : published) {
            SnapshotContent snapshot = snapshotOf(definition);
            if (snapshot == null) {
                continue;
            }
            if (category != null && !category.isBlank() && !category.equals(snapshot.category())) {
                continue;
            }
            String cycleKey = CycleKeyResolver.resolve(
                    snapshot.cycleType(),
                    snapshot.cronExpr(),
                    snapshot.specialStart(),
                    snapshot.specialEnd(),
                    now);
            TaskInstanceEntity current = instances.getByUserTaskCycle(userId, definition.getId(), cycleKey);
            boolean inProgressThisCycle =
                    current != null && InstanceStatuses.IN_PROGRESS.equals(current.getStatus());
            VisibilityResult visibility = visibility(definition, snapshot, userId, attrs, now);
            if (!visibility.visible() && !inProgressThisCycle) {
                continue;
            }
            String userStatus = current == null ? InstanceStatuses.NOT_STARTED : current.getStatus();
            cards.add(toCard(definition, snapshot, userStatus));
        }
        cards.sort(Comparator.comparingInt(TaskCardView::sortWeight).thenComparingLong(TaskCardView::taskId));
        PageQuery query = PageQuery.of(page, pageSize);
        long total = cards.size();
        int from = (int) Math.min(query.offset(), total);
        int to = (int) Math.min(from + query.pageSize(), total);
        return new PageData<>(total, cards.subList(from, to));
    }

    public TaskDetailResponse detail(long taskId, long userId, String platform) {
        TaskDefinitionEntity definition = definitions.getById(taskId);
        if (definition == null || definition.deletedFlag()) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        Instant now = clock.instant();
        SnapshotContent live = snapshotOf(definition);
        String cycleKey = live == null
                ? CycleKeyResolver.resolve(
                        definition.getCycleType(),
                        definition.getCronExpr(),
                        TaskTime.toInstant(definition.getSpecialStart()),
                        TaskTime.toInstant(definition.getSpecialEnd()),
                        now)
                : CycleKeyResolver.resolve(
                        live.cycleType(), live.cronExpr(), live.specialStart(), live.specialEnd(), now);
        TaskInstanceEntity current = instances.getByUserTaskCycle(userId, taskId, cycleKey);
        TaskInstanceEntity inProgress = pickInProgress(taskId, userId, cycleKey);
        if (inProgress != null) {
            return inProgressDetail(inProgress, platform);
        }
        if (current != null && InstanceStatuses.terminal(current.getStatus())) {
            return new TaskDetailResponse(current.getStatus(), current.getId(), null, null, null, null, null);
        }
        SnapshotContent snapshot = snapshotOf(definition);
        UserAttributes attrs = users == null ? UserAttributes.notFound() : users.attributes(userId);
        VisibilityResult visibility =
                snapshot == null
                        ? VisibilityResult.deny(List.of())
                        : visibility(definition, snapshot, userId, attrs, now);
        if (snapshot == null || !visibility.visible()) {
            return new TaskDetailResponse(InstanceStatuses.OFFLINE, null, null, null, null, null, null);
        }
        return new TaskDetailResponse(
                InstanceStatuses.NOT_STARTED,
                null,
                new TaskBriefView(snapshot.name(), snapshot.iconUrl(), snapshot.description(), snapshot.category()),
                SnapshotViews.stepsPreview(snapshot),
                SnapshotViews.rewardPreview(snapshot),
                null,
                null);
    }

    public PageData<MineTaskView> mine(long userId, String status, String category, Integer page, Integer pageSize) {
        PageQuery query = PageQuery.of(page, pageSize);
        List<Long> categoryTaskIds = null;
        if (category != null && !category.isBlank()) {
            categoryTaskIds = new ArrayList<>();
            for (TaskDefinitionEntity definition : definitions.listPublished()) {
                SnapshotContent snapshot = snapshotOf(definition);
                if (snapshot != null && category.equals(snapshot.category())) {
                    categoryTaskIds.add(definition.getId());
                }
            }
            for (TaskInstanceEntity row : instances.listByUser(userId)) {
                if (categoryTaskIds.contains(row.getTaskId())) {
                    continue;
                }
                TaskVersionSnapshotEntity snap = snapshots.getById(row.getSnapshotId());
                if (snap == null) {
                    continue;
                }
                SnapshotContent content = JsonUtil.fromJson(snap.getContent(), SnapshotContent.class);
                if (content != null && category.equals(content.category())) {
                    categoryTaskIds.add(row.getTaskId());
                }
            }
            if (categoryTaskIds.isEmpty()) {
                return new PageData<>(0, List.of());
            }
        }
        long total = instances.countMine(userId, blankToNull(status), categoryTaskIds);
        List<TaskInstanceEntity> rows =
                instances.listMine(userId, blankToNull(status), categoryTaskIds, query.offset(), query.pageSize());
        List<MineTaskView> views = new ArrayList<>(rows.size());
        for (TaskInstanceEntity row : rows) {
            views.add(toMine(row));
        }
        return new PageData<>(total, views);
    }

    private TaskDetailResponse inProgressDetail(TaskInstanceEntity instance, String platform) {
        TaskVersionSnapshotEntity snap = snapshots.getById(instance.getSnapshotId());
        SnapshotContent snapshot =
                snap == null ? null : JsonUtil.fromJson(snap.getContent(), SnapshotContent.class);
        List<TaskInstanceStepEntity> steps = instances.listSteps(instance.getId());
        List<InstanceStepView> stepViews = new ArrayList<>();
        CurrentStepView current = null;
        if (steps != null && snapshot != null) {
            for (TaskInstanceStepEntity step : steps) {
                TaskStepCommand def = SnapshotViews.step(snapshot, step.getStepCode());
                String name = def == null ? step.getStepCode() : def.name();
                Integer target = def == null ? null : def.progressTarget();
                stepViews.add(new InstanceStepView(
                        step.getStepCode(),
                        name,
                        step.getType(),
                        step.getStatus(),
                        step.getProgressCurrent() == null ? 0 : step.getProgressCurrent(),
                        target));
                if (current == null && StepStatuses.ACTIVE.equals(step.getStatus())) {
                    current = new CurrentStepView(
                            step.getStepCode(),
                            name,
                            step.getType(),
                            step.getProgressCurrent(),
                            target,
                            SnapshotViews.action(snapshot, step.getStepCode(), platform));
                }
            }
        }
        return new TaskDetailResponse(
                InstanceStatuses.IN_PROGRESS,
                instance.getId(),
                null,
                null,
                SnapshotViews.rewardPreview(snapshot),
                stepViews,
                current);
    }

    private VisibilityResult visibility(
            TaskDefinitionEntity definition,
            SnapshotContent snapshot,
            long userId,
            UserAttributes attrs,
            Instant now) {
        StoreCrowdMembership membership = new StoreCrowdMembership(crowds);
        CrowdResolver resolver = new StoreCrowdResolver(crowds, users, userId);
        return VisibilityEvaluator.evaluate(
                definition.getStatus(),
                snapshot.startTime(),
                snapshot.endTime(),
                snapshot.gray(),
                snapshot.filter(),
                definition.getId(),
                userId,
                attrs,
                now,
                membership,
                EvalContexts.filter(attrs, now, resolver));
    }

    private SnapshotContent snapshotOf(TaskDefinitionEntity definition) {
        if (definition.getVersion() == null || definition.getVersion() < 1) {
            return null;
        }
        TaskVersionSnapshotEntity snap = snapshots.getByTaskAndVersion(definition.getId(), definition.getVersion());
        if (snap == null || snap.getContent() == null) {
            return null;
        }
        return JsonUtil.fromJson(snap.getContent(), SnapshotContent.class);
    }

    private boolean blacklisted(long userId) {
        if (risk == null) {
            return false;
        }
        UserRiskSummary summary = risk.userSummary(userId);
        return summary != null && summary.listStatus().contains(RiskListType.BLACK);
    }

    private TaskInstanceEntity pickInProgress(long taskId, long userId, String cycleKey) {
        TaskInstanceEntity fallback = null;
        for (TaskInstanceEntity row : instances.listInProgressByUser(userId)) {
            if (taskId != row.getTaskId()) {
                continue;
            }
            if (cycleKey.equals(row.getCycleKey())) {
                return row;
            }
            if (fallback == null) {
                fallback = row;
            }
        }
        return fallback;
    }

    private static TaskCardView toCard(TaskDefinitionEntity definition, SnapshotContent snapshot, String userStatus) {
        int weight = snapshot.sortWeight();
        return new TaskCardView(
                definition.getId(),
                snapshot.code(),
                snapshot.name(),
                snapshot.category(),
                snapshot.iconUrl(),
                snapshot.badgeText(),
                SnapshotViews.rewardPreview(snapshot),
                userStatus,
                weight);
    }

    private MineTaskView toMine(TaskInstanceEntity row) {
        TaskVersionSnapshotEntity snap = snapshots.getById(row.getSnapshotId());
        SnapshotContent content =
                snap == null ? null : JsonUtil.fromJson(snap.getContent(), SnapshotContent.class);
        String name = content == null ? row.getTaskCode() : content.name();
        String icon = content == null ? null : content.iconUrl();
        String category = content == null ? null : content.category();
        String currentName = null;
        if (InstanceStatuses.IN_PROGRESS.equals(row.getStatus())) {
            List<TaskInstanceStepEntity> steps = instances.listSteps(row.getId());
            if (steps != null) {
                for (TaskInstanceStepEntity step : steps) {
                    if (StepStatuses.ACTIVE.equals(step.getStatus())) {
                        TaskStepCommand def = SnapshotViews.step(content, step.getStepCode());
                        currentName = def == null ? step.getStepCode() : def.name();
                        break;
                    }
                }
            }
        }
        return new MineTaskView(
                row.getId(),
                row.getTaskId(),
                name,
                icon,
                category,
                row.getStatus(),
                currentName,
                TaskTime.toInstant(row.getStartedAt()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
