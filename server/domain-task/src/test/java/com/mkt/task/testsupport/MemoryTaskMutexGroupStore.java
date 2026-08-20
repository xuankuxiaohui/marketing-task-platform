package com.mkt.task.testsupport;

import com.mkt.task.application.TaskMutexGroupStore;
import com.mkt.task.entity.TaskMutexGroupEntity;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class MemoryTaskMutexGroupStore implements TaskMutexGroupStore {

    private final ConcurrentHashMap<Long, TaskMutexGroupEntity> rows = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);

    @Override
    public TaskMutexGroupEntity getById(long id) {
        return rows.get(id);
    }

    @Override
    public TaskMutexGroupEntity getByCode(String code) {
        return rows.values().stream().filter(row -> code.equals(row.getCode())).findFirst().orElse(null);
    }

    @Override
    public int insert(TaskMutexGroupEntity entity) {
        entity.setId(seq.getAndIncrement());
        rows.put(entity.getId(), entity);
        return 1;
    }

    @Override
    public int update(TaskMutexGroupEntity entity) {
        rows.put(entity.getId(), entity);
        return 1;
    }

    @Override
    public int deleteById(long id) {
        return rows.remove(id) == null ? 0 : 1;
    }

    @Override
    public long countAll() {
        return rows.size();
    }

    @Override
    public List<TaskMutexGroupEntity> list(long offset, int limit) {
        return rows.values().stream()
                .sorted(Comparator.comparing(TaskMutexGroupEntity::getId).reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }
}
