package com.mkt.task.application;

import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.UserAttributePort;
import com.mkt.infra.cache.PlatformCache;
import com.mkt.kernel.PageData;
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

    private final TaskPortalPublishedCatalog publishedCatalog;
    private final TaskPortalCardViews cardViews;
    private final TaskPortalDetail detail;
    private final PortalList portalList;
    private final PortalMine portalMine;
    private final PortalDetailEntry portalDetailEntry;
    private final PortalSourceForStep portalSourceForStep;

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
            PortalDetailEntry portalDetailEntry,
            PortalSourceForStep portalSourceForStep) {
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
                portalDetailEntry,
                portalSourceForStep);
    }

    public TaskPortalAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            UserAttributePort users,
            RiskCheckPort risk,
            Clock clock) {
        this(definitions, snapshots, instances, crowds, users, risk, clock, null, null, null, null, null, null, null, null);
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
        this(definitions, snapshots, instances, crowds, users, risk, clock, cache, null, null, null, null, null, null, null);
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
            PortalDetailEntry portalDetailEntry,
            PortalSourceForStep portalSourceForStep) {
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
        // Manual/IT chain passes null. Production passes the injected collaborator.
        this.portalSourceForStep =
                portalSourceForStep != null
                        ? portalSourceForStep
                        : new PortalSourceForStep(snapshots, instances);
    }

    public TaskStepSource sourceForStep(long stepId) {
        return portalSourceForStep.sourceForStep(stepId);
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
