package com.mkt.task.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.task.command.TaskDefinitionSaveCommand;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.convert.TaskDefinitionConvert;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.entity.TaskMutexGroupEntity;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.response.PublishResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Revision reset extracted from {@link TaskPublishAppService} (R19-4).
 * Move-only: resetRevision semantics unchanged.
 */
@Component
public class PublishResetRevision {

    private final TaskDefinitionStore definitions;
    private final TaskMutexGroupStore mutexGroups;
    private final TaskVersionSnapshotStore snapshots;
    private final TaskDefinitionAppService definitionsApp;

    @Autowired
    public PublishResetRevision(
            TaskDefinitionStore definitions,
            TaskMutexGroupStore mutexGroups,
            TaskVersionSnapshotStore snapshots,
            TaskDefinitionAppService definitionsApp) {
        this.definitions = definitions;
        this.mutexGroups = mutexGroups;
        this.snapshots = snapshots;
        this.definitionsApp = definitionsApp;
    }

    PublishResponse resetRevision(long id) {
        TaskDefinitionEntity entity = requireLockedLive(id);
        int version = versionOf(entity);
        if (version < 1) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "没有可重置的版本快照");
        }
        TaskVersionSnapshotEntity snap = snapshots.getByTaskAndVersion(id, version);
        if (snap == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND, "版本快照不存在");
        }
        SnapshotContent content = JsonUtil.fromJson(snap.getContent(), SnapshotContent.class);
        Long mutexId = null;
        if (content.mutexGroupCode() != null && !content.mutexGroupCode().isBlank()) {
            TaskMutexGroupEntity group = mutexGroups.getByCode(content.mutexGroupCode());
            if (group == null) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "快照互斥组不存在");
            }
            mutexId = group.getId();
        }
        TaskDefinitionSaveCommand command = TaskDefinitionConvert.toSaveCommand(id, content, mutexId);
        definitionsApp.restoreEditState(id, command);
        entity = requireLive(id);
        return PublishResponse.done(entity.getId(), entity.getCode(), versionOf(entity), entity.getStatus());
    }

    private TaskDefinitionEntity requireLive(long id) {
        TaskDefinitionEntity existing = definitions.getById(id);
        if (existing == null || existing.deletedFlag()) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
    }

    private TaskDefinitionEntity requireLockedLive(long id) {
        TaskDefinitionEntity existing = definitions.getByIdForUpdate(id);
        if (existing == null || existing.deletedFlag()) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
    }

    private static int versionOf(TaskDefinitionEntity entity) {
        return entity.getVersion() == null ? 0 : entity.getVersion();
    }
}
