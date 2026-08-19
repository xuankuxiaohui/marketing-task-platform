package com.mkt.task.testsupport;

import com.mkt.task.application.TaskDefinitionStore;
import com.mkt.task.entity.TaskDefinitionEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class MemoryTaskDefinitionStore implements TaskDefinitionStore {

    private final ConcurrentHashMap<Long, TaskDefinitionEntity> rows = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);

    @Override
    public TaskDefinitionEntity getById(long id) {
        return rows.get(id);
    }

    @Override
    public TaskDefinitionEntity getByCode(String code) {
        return rows.values().stream()
                .filter(row -> !row.deletedFlag() && code.equals(row.getCode()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public int insert(TaskDefinitionEntity entity) {
        entity.setId(seq.getAndIncrement());
        rows.put(entity.getId(), entity);
        return 1;
    }

    @Override
    public int update(TaskDefinitionEntity entity) {
        rows.put(entity.getId(), entity);
        return 1;
    }

    @Override
    public long countByQuery(String code, String name, String status, String category) {
        return listByQuery(code, name, status, category, 0, Integer.MAX_VALUE).size();
    }

    @Override
    public List<TaskDefinitionEntity> listByQuery(
            String code, String name, String status, String category, long offset, int limit) {
        return rows.values().stream()
                .filter(row -> !row.deletedFlag())
                .filter(row -> code == null || code.equals(row.getCode()))
                .filter(row -> name == null || (row.getName() != null && row.getName().contains(name)))
                .filter(row -> status == null || status.equals(row.getStatus()))
                .filter(row -> category == null || category.equals(row.getCategory()))
                .sorted(Comparator.comparing(TaskDefinitionEntity::getId).reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public List<String> cycleTypesInMutexGroup(long mutexGroupId, Long excludeTaskId) {
        List<String> types = new ArrayList<>();
        for (TaskDefinitionEntity row : rows.values()) {
            if (row.deletedFlag() || row.getMutexGroupId() == null || row.getMutexGroupId() != mutexGroupId) {
                continue;
            }
            if (excludeTaskId != null && excludeTaskId.equals(row.getId())) {
                continue;
            }
            types.add(row.getCycleType());
        }
        return types;
    }

    @Override
    public int countByMutexGroup(long mutexGroupId) {
        int count = 0;
        for (TaskDefinitionEntity row : rows.values()) {
            if (!row.deletedFlag() && row.getMutexGroupId() != null && row.getMutexGroupId() == mutexGroupId) {
                count++;
            }
        }
        return count;
    }

    @Override
    public int countReferencingCrowd(long crowdId) {
        return 0;
    }

    public int liveCount() {
        return (int) rows.values().stream().filter(row -> !row.deletedFlag()).count();
    }
}
