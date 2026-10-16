package com.mkt.task.application;

import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.UserAttributePort;
import com.mkt.infra.cache.PlatformCache;
import com.mkt.kernel.PageData;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.response.MineTaskView;
import com.mkt.task.response.TaskStepSource;
import com.mkt.task.response.TaskCardView;
import com.mkt.task.response.TaskDetailResponse;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TaskPortalAppService {

    private final TaskVersionSnapshotStore snapshots;
    private final TaskInstanceStore instances;
    private final TaskPortalPublishedCatalog publishedCatalog;
    private final TaskPortalCardViews cardViews;
    private final TaskPortalDetail detail;
    private final PortalList portalList;
    private final PortalMine portalMine;
    private final PortalDetailEntry portalDetailEntry;

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
            PortalMine portalMine,
            PortalDetailEntry portalDetailEntry) {
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
                portalMine,
                portalDetailEntry);
    }

    public TaskPortalAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            UserAttributePort users,
            RiskCheckPort risk,
            Clock clock) {
        this(definitions, snapshots, instances, crowds, users, risk, clock, null, null, null, null, null, null, null);
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
        this(definitions, snapshots, instances, crowds, users, risk, clock, cache, null, null, null, null, null, null);
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
            PortalMine portalMine,
            PortalDetailEntry portalDetailEntry) {
        this.snapshots = snapshots;
        this.instances = instances;
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
        // Manual/IT chain passes null. Production passes the injected collaborator.
        this.portalDetailEntry =
                portalDetailEntry != null
                        ? portalDetailEntry
                        : new PortalDetailEntry(definitions, instances, users, clock, this.detail);
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
        return portalDetailEntry.detail(taskId, userId, platform);
    }

    public PageData<MineTaskView> mine(long userId, String status, String category, Integer page, Integer pageSize) {
        return portalMine.mine(userId, status, category, page, pageSize);
    }
}
