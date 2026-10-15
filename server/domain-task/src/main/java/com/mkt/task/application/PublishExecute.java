package com.mkt.task.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.task.command.PublishCommand;
import com.mkt.task.domain.DefinitionStatuses;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.response.PublishResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Immediate publish extracted from {@link TaskPublishAppService} (R19-2).
 * Move-only: publish semantics unchanged.
 */
@Component
public class PublishExecute {

    private final TaskDefinitionStore definitions;
    private final TaskPublishChecks publishChecks;
    private final TaskPublishFreeze publishFreeze;
    private final Clock clock;

    @Autowired
    public PublishExecute(
            TaskDefinitionStore definitions,
            TaskPublishChecks publishChecks,
            TaskPublishFreeze publishFreeze,
            Clock clock) {
        this.definitions = definitions;
        this.publishChecks = publishChecks;
        this.publishFreeze = publishFreeze;
        this.clock = clock;
    }

    PublishResponse publish(long id, PublishCommand command) {
        PublishCommand cmd = command == null ? new PublishCommand(null, null) : command;
        TaskDefinitionEntity entity = requireLockedLive(id);
        if (pending(entity) && !cmd.confirmTrue()) {
            return PublishResponse.preview(
                    entity.getId(),
                    entity.getCode(),
                    versionOf(entity),
                    entity.getStatus(),
                    definitions.countInProgressInstances(id));
        }
        String status = entity.getStatus();
        if (DefinitionStatuses.DRAFT.equals(status)) {
            publishChecks.rejectIfInvalid(entity);
            requireCas(publishFreeze.freezeToPublished(entity, false));
        } else if (DefinitionStatuses.SCHEDULED.equals(status)) {
            if (cmd.earlyTrue()) {
                publishChecks.rejectIfInvalid(entity);
                requireCas(publishFreeze.freezeToPublished(entity, false));
            } else {
                entity.setPendingRevision(0);
                entity.setUpdatedAt(nowUtc());
                definitions.update(entity);
            }
        } else if (DefinitionStatuses.PUBLISHED.equals(status)) {
            if (!pending(entity)) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "无待发布修订");
            }
            publishChecks.rejectIfInvalid(entity);
            requireCas(publishFreeze.freezeToPublished(entity, true));
        } else {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "当前状态不可发布");
        }
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

    private static void requireCas(boolean applied) {
        if (!applied) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "发布冲突，请重试");
        }
    }

    private static boolean pending(TaskDefinitionEntity entity) {
        return entity.getPendingRevision() != null && entity.getPendingRevision() == 1;
    }

    private static int versionOf(TaskDefinitionEntity entity) {
        return entity.getVersion() == null ? 0 : entity.getVersion();
    }
}
