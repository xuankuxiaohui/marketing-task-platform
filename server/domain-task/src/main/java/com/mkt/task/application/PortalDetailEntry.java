package com.mkt.task.application;

import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.convert.SnapshotViews;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.domain.CycleKeyResolver;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.domain.VisibilityResult;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.response.TaskBriefView;
import com.mkt.task.response.TaskDetailResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Detail entry extracted from {@link TaskPortalAppService} (R23-1).
 * Move-only: detail semantics unchanged.
 */
@Component
public class PortalDetailEntry {

    private final TaskDefinitionStore definitions;
    private final TaskInstanceStore instances;
    private final UserAttributePort users;
    private final Clock clock;
    private final TaskPortalDetail detail;

    @Autowired
    public PortalDetailEntry(
            TaskDefinitionStore definitions,
            TaskInstanceStore instances,
            ObjectProvider<UserAttributePort> users,
            Clock clock,
            TaskPortalDetail detail) {
        this(definitions, instances, users.getIfAvailable(), clock, detail);
    }

    PortalDetailEntry(
            TaskDefinitionStore definitions,
            TaskInstanceStore instances,
            UserAttributePort users,
            Clock clock,
            TaskPortalDetail detail) {
        this.definitions = definitions;
        this.instances = instances;
        this.users = users;
        this.clock = clock;
        this.detail = detail;
    }

    TaskDetailResponse detail(long taskId, Long userId, String platform) {
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
}
