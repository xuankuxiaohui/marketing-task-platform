package com.mkt.task.testsupport;

import com.mkt.task.application.TaskVersionSnapshotStore;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class MemoryTaskVersionSnapshotStore implements TaskVersionSnapshotStore {

    public final ConcurrentHashMap<Long, TaskVersionSnapshotEntity> rows = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);

    @Override
    public int insert(TaskVersionSnapshotEntity entity) {
        for (TaskVersionSnapshotEntity existing : rows.values()) {
            if (existing.getTaskId().equals(entity.getTaskId())
                    && existing.getVersion().equals(entity.getVersion())) {
                throw new org.springframework.dao.DuplicateKeyException("uk_task_version");
            }
        }
        entity.setId(seq.getAndIncrement());
        rows.put(entity.getId(), entity);
        return 1;
    }

    @Override
    public TaskVersionSnapshotEntity getById(long id) {
        return rows.get(id);
    }

    @Override
    public TaskVersionSnapshotEntity getByTaskAndVersion(long taskId, int version) {
        return rows.values().stream()
                .filter(row -> row.getTaskId() == taskId && row.getVersion() == version)
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<TaskVersionSnapshotEntity> listByTaskId(long taskId) {
        return rows.values().stream()
                .filter(row -> row.getTaskId() == taskId)
                .sorted(Comparator.comparing(TaskVersionSnapshotEntity::getVersion).reversed())
                .toList();
    }
}
