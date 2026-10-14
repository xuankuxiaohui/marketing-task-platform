package com.mkt.task.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.support.TaskErrorCodes;
import java.time.Duration;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Preload helpers extracted from {@link TaskStepAppService} (R8-1).
 * Move-only: locateSoft / lockInstance / cachedSnapshot / cachedAttrs semantics unchanged.
 * {@code cachedAttrs} still uses {@code this::attrs}; that loader is copied so the moved body stays verbatim.
 */
@Component
public class TaskStepPreload {

    /**
     * Architect 2026-10-12 hold-lock knife: TTL 3s (2–5s band) caches for snapshot/attrs
     * loaded BEFORE {@code getByIdForUpdate} on progress/callback.
     */
    private static final Duration PRELOAD_TTL = Duration.ofSeconds(3);

    private final TaskDefinitionStore definitions;
    private final TaskVersionSnapshotStore snapshots;
    private final TaskInstanceStore instances;
    private final UserAttributePort users;
    private final Cache<Long, SnapshotContent> snapshotById =
            Caffeine.newBuilder().expireAfterWrite(PRELOAD_TTL).maximumSize(4_096).build();
    private final Cache<Long, UserAttributes> attrsByUser =
            Caffeine.newBuilder().expireAfterWrite(PRELOAD_TTL).maximumSize(8_192).build();

    @Autowired
    public TaskStepPreload(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            ObjectProvider<UserAttributePort> users) {
        this(definitions, snapshots, instances, users.getIfAvailable());
    }

    public TaskStepPreload(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            UserAttributePort users) {
        this.definitions = definitions;
        this.snapshots = snapshots;
        this.instances = instances;
        this.users = users;
    }

    /** Non-locking resolve for preload (progress/callback hold-lock knife). */
    TaskInstanceEntity locateSoft(Long instanceId, Long userId, String taskCode, String cycleKey) {
        if (instanceId != null) {
            TaskInstanceEntity row = instances.getById(instanceId);
            if (row == null) {
                throw new BusinessException(TaskErrorCodes.INSTANCE_NOT_FOUND);
            }
            return row;
        }
        if (userId == null || taskCode == null || taskCode.isBlank() || cycleKey == null || cycleKey.isBlank()) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        TaskDefinitionEntity definition = definitions.getByCode(taskCode.trim());
        if (definition == null) {
            throw new BusinessException(TaskErrorCodes.INSTANCE_NOT_FOUND);
        }
        TaskInstanceEntity row = instances.getByUserTaskCycle(userId, definition.getId(), cycleKey.trim());
        if (row == null) {
            throw new BusinessException(TaskErrorCodes.INSTANCE_NOT_FOUND);
        }
        return row;
    }

    /** Take instance row lock only after preload; re-check identity. */
    TaskInstanceEntity lockInstance(TaskInstanceEntity soft) {
        TaskInstanceEntity locked = instances.getByIdForUpdate(soft.getId());
        if (locked == null) {
            throw new BusinessException(TaskErrorCodes.INSTANCE_NOT_FOUND);
        }
        if (!Objects.equals(locked.getUserId(), soft.getUserId())
                || !Objects.equals(locked.getSnapshotId(), soft.getSnapshotId())) {
            // rare race: fall back to fresh snapshot under lock (attrs/crowds still preloaded by userId)
            return locked;
        }
        return locked;
    }

    SnapshotContent cachedSnapshot(TaskInstanceEntity instance) {
        Long snapshotId = instance.getSnapshotId();
        if (snapshotId == null) {
            throw new BusinessException(CommonErrorCodes.SERVER_ERROR);
        }
        return snapshotById.get(snapshotId, id -> {
            TaskVersionSnapshotEntity snap = snapshots.getById(id);
            if (snap == null || snap.getContent() == null) {
                throw new BusinessException(CommonErrorCodes.SERVER_ERROR);
            }
            return JsonUtil.fromJson(snap.getContent(), SnapshotContent.class);
        });
    }

    UserAttributes cachedAttrs(long userId) {
        return attrsByUser.get(userId, this::attrs);
    }

    private UserAttributes attrs(long userId) {
        if (users == null) {
            return UserAttributes.notFound();
        }
        return users.attributes(userId);
    }
}
