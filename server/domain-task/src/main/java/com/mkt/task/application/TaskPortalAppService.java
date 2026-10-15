package com.mkt.task.application;

import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.infra.cache.PlatformCache;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.convert.SnapshotViews;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.domain.CycleKeyResolver;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.domain.VisibilityResult;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.response.MineTaskView;
import com.mkt.task.response.TaskBriefView;
import com.mkt.task.response.TaskStepSource;
import com.mkt.task.response.TaskCardView;
import com.mkt.task.response.TaskDetailResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TaskPortalAppService {

    private final TaskDefinitionStore definitions;
    private final TaskVersionSnapshotStore snapshots;
    private final TaskInstanceStore instances;
    private final UserAttributePort users;
    private final Clock clock;
    private final TaskPortalPublishedCatalog publishedCatalog;
    private final TaskPortalCardViews cardViews;
    private final TaskPortalDetail detail;
    private final PortalList portalList;
    private final PortalMine portalMine;

    @Autowired
    public TaskPortalAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            ObjectProvider<UserAttributePort> users,
            ObjectProvider<RiskCheckPort> risk,
            ObjectProvider<PlatformCache> cache,
            Clock clock,
            TaskPortalPublishedCatalog publishedCatalog,
            TaskPortalCardViews cardViews,
            TaskPortalDetail detail,
            PortalList portalList,
            PortalMine portalMine) {
        this(
                definitions,
                snapshots,
                instances,
                crowds,
                users.getIfAvailable(),
                risk.getIfAvailable(),
                clock,
                cache.getIfAvailable(),
                publishedCatalog,
                cardViews,
                detail,
                portalList,
                portalMine);
    }

    public TaskPortalAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            UserAttributePort users,
            RiskCheckPort risk,
            Clock clock) {
        this(definitions, snapshots, instances, crowds, users, risk, clock, null, null, null, null, null, null);
    }

    public TaskPortalAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            UserAttributePort users,
            RiskCheckPort risk,
            Clock clock,
            PlatformCache cache) {
        this(definitions, snapshots, instances, crowds, users, risk, clock, cache, null, null, null, null, null);
    }

    TaskPortalAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            UserAttributePort users,
            RiskCheckPort risk,
            Clock clock,
            PlatformCache cache,
            TaskPortalPublishedCatalog publishedCatalog,
            TaskPortalCardViews cardViews,
            TaskPortalDetail detail,
            PortalList portalList,
            PortalMine portalMine) {
        this.definitions = definitions;
        this.snapshots = snapshots;
        this.instances = instances;
        this.users = users;
        this.clock = clock;
        this.publishedCatalog =
                publishedCatalog != null
                        ? publishedCatalog
                        : new TaskPortalPublishedCatalog(definitions, snapshots, instances, cache);
        this.cardViews =
                cardViews != null
                        ? cardViews
                        : new TaskPortalCardViews(snapshots, instances);
        this.detail =
                detail != null
                        ? detail
                        : new TaskPortalDetail(snapshots, instances, crowds, users, risk, cache);
        this.portalList =
                portalList != null
                        ? portalList
                        : new PortalList(users, this.detail, clock, this.publishedCatalog, crowds);
        this.portalMine =
                portalMine != null
                        ? portalMine
                        : new PortalMine(instances, this.publishedCatalog, this.cardViews);
    }

    public TaskStepSource sourceForStep(long stepId) {
        TaskInstanceStepEntity step = instances.getStepById(stepId);
        if (step == null || step.getInstanceId() == null) {
            return null;
        }
        TaskInstanceEntity instance = instances.getById(step.getInstanceId());
        if (instance == null || instance.getTaskId() == null) {
            return null;
        }
        TaskVersionSnapshotEntity snap =
                instance.getSnapshotId() == null ? null : snapshots.getById(instance.getSnapshotId());
        SnapshotContent content =
                snap == null ? null : JsonUtil.fromJson(snap.getContent(), SnapshotContent.class);
        String name = content == null || content.name() == null || content.name().isBlank()
                ? instance.getTaskCode()
                : content.name();
        return new TaskStepSource(instance.getTaskId(), name);
    }

    public PageData<TaskCardView> list(Long userId, String category, Integer page, Integer pageSize) {
        return portalList.list(userId, category, page, pageSize);
    }

    public TaskDetailResponse detail(long taskId, Long userId, String platform) {
        TaskDefinitionEntity definition = definitions.getById(taskId);
        if (definition == null || definition.deletedFlag()) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        Instant now = clock.instant();
        if (userId == null) {
            return detail.anonymousDetail(definition, now);
        }
        SnapshotContent live = detail.snapshotOf(definition);
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
        TaskInstanceEntity inProgress = detail.pickInProgress(taskId, userId, cycleKey);
        if (inProgress != null) {
            return detail.inProgressDetail(inProgress, platform);
        }
        if (current != null && InstanceStatuses.terminal(current.getStatus())) {
            return new TaskDetailResponse(current.getStatus(), current.getId(), null, null, null, null, null);
        }
        SnapshotContent snapshot = detail.snapshotOf(definition);
        UserAttributes attrs = users == null ? UserAttributes.notFound() : users.attributes(userId);
        VisibilityResult visibility =
                snapshot == null
                        ? VisibilityResult.deny(List.of())
                        : detail.visibility(definition, snapshot, userId, attrs, now);
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
        return portalMine.mine(userId, status, category, page, pageSize);
    }
}
