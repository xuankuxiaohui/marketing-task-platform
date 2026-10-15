package com.mkt.task.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.domain.CycleKeyResolver;
import com.mkt.task.domain.ExpireAtCalculator;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.support.TaskSettings;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Offline in-progress expiry recompute extracted from {@link TaskPublishAppService} (R15-1).
 * Move-only: recomputeInProgressExpiry / OFFLINE_BATCH semantics unchanged.
 */
@Component
public class PublishOfflineExpiry {

    private static final int OFFLINE_BATCH = 100;

    private final TaskInstanceStore instances;
    private final TaskVersionSnapshotStore snapshots;
    private final TaskSettings settings;

    @Autowired
    public PublishOfflineExpiry(
            TaskInstanceStore instances, TaskVersionSnapshotStore snapshots, TaskSettings settings) {
        this.instances = instances;
        this.snapshots = snapshots;
        this.settings = settings;
    }

    void recomputeInProgressExpiry(long taskId, LocalDateTime offlineAt) {
        Instant offline = TaskTime.toInstant(offlineAt);
        int days = settings.expireAfterWindowDays();
        long afterId = 0L;
        List<TaskInstanceEntity> batch;
        do {
            batch = instances.listInProgressByTaskAfterId(taskId, afterId, OFFLINE_BATCH);
            List<Long> snapshotIds = batch.stream()
                    .map(TaskInstanceEntity::getSnapshotId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            Map<Long, SnapshotContent> contents = new LinkedHashMap<>(snapshotIds.size());
            for (TaskVersionSnapshotEntity snapshot : snapshots.listByIds(snapshotIds)) {
                contents.put(snapshot.getId(), JsonUtil.fromJson(snapshot.getContent(), SnapshotContent.class));
            }
            for (TaskInstanceEntity instance : batch) {
                afterId = instance.getId();
                LocalDateTime expectedExpiry = instance.getExpireAt();
                if (expectedExpiry != null && !expectedExpiry.isAfter(offlineAt)) {
                    continue;
                }
                SnapshotContent content = contents.get(instance.getSnapshotId());
                Instant startedAt = TaskTime.toInstant(instance.getStartedAt());
                if (content == null || startedAt == null) {
                    throw new BusinessException(CommonErrorCodes.SERVER_ERROR, "实例快照或开始时间缺失");
                }
                Instant cycleEnd = CycleKeyResolver.cycleEndForKey(
                        content.cycleType(), content.cronExpr(), content.specialEnd(), instance.getCycleKey());
                LocalDateTime expiry = TaskTime.toUtc(ExpireAtCalculator.compute(
                        content.endTime(), offline, cycleEnd, startedAt, days));
                if (Objects.equals(expectedExpiry, expiry)) {
                    continue;
                }
                int affected = instances.updateExpireAtCas(instance.getId(), expectedExpiry, expiry, offlineAt);
                if (affected != 1) {
                    TaskInstanceEntity fresh = instances.getByIdForUpdate(instance.getId());
                    if (fresh != null && InstanceStatuses.IN_PROGRESS.equals(fresh.getStatus())
                            && (fresh.getExpireAt() == null || fresh.getExpireAt().isAfter(offlineAt))) {
                        throw new BusinessException(CommonErrorCodes.SERVER_ERROR, "实例期限更新冲突，请重试");
                    }
                }
            }
        } while (batch.size() == OFFLINE_BATCH);
    }
}
