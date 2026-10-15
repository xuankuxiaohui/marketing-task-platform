package com.mkt.task.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.task.command.ScheduleCommand;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.domain.DefinitionStatuses;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.response.PublishCheckError;
import com.mkt.task.response.PublishResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Schedule and cancel-schedule extracted from {@link TaskPublishAppService} (R19-3).
 * Move-only: schedule / cancelSchedule semantics unchanged.
 */
@Component
public class PublishSchedule {

    private final TaskDefinitionStore definitions;
    private final TaskPublishChecks publishChecks;
    private final Clock clock;

    @Autowired
    public PublishSchedule(TaskDefinitionStore definitions, TaskPublishChecks publishChecks, Clock clock) {
        this.definitions = definitions;
        this.publishChecks = publishChecks;
        this.clock = clock;
    }

    PublishResponse schedule(long id, ScheduleCommand command) {
        if (command == null || command.publishAt() == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "publishAt 必填");
        }
        TaskDefinitionEntity entity = requireLockedLive(id);
        if (!DefinitionStatuses.DRAFT.equals(entity.getStatus())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "仅草稿可设定时发布");
        }
        LocalDateTime publishAt = TaskTime.toUtc(command.publishAt());
        if (!publishAt.isAfter(nowUtc())) {
            throw publishChecks.validateFailed(List.of(new PublishCheckError("schedule", "定时发布时间必须晚于当前时间")));
        }
        entity.setSchedulePublishAt(publishAt);
        publishChecks.rejectIfInvalid(entity);
        entity.setStatus(DefinitionStatuses.SCHEDULED);
        entity.setPendingRevision(0);
        entity.setUpdatedAt(nowUtc());
        definitions.update(entity);
        return PublishResponse.done(entity.getId(), entity.getCode(), versionOf(entity), entity.getStatus());
    }

    PublishResponse cancelSchedule(long id) {
        TaskDefinitionEntity entity = requireLockedLive(id);
        if (!DefinitionStatuses.SCHEDULED.equals(entity.getStatus())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "仅定时任务可取消定时");
        }
        entity.setStatus(DefinitionStatuses.DRAFT);
        entity.setSchedulePublishAt(null);
        entity.setPendingRevision(0);
        entity.setUpdatedAt(nowUtc());
        definitions.update(entity);
        return PublishResponse.done(entity.getId(), entity.getCode(), versionOf(entity), entity.getStatus());
    }

    private TaskDefinitionEntity requireLockedLive(long id) {
        TaskDefinitionEntity existing = definitions.getByIdForUpdate(id);
        if (existing == null || existing.deletedFlag()) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static int versionOf(TaskDefinitionEntity entity) {
        return entity.getVersion() == null ? 0 : entity.getVersion();
    }
}
