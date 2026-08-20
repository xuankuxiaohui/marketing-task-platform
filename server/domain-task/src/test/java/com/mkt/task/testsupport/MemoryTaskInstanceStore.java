package com.mkt.task.testsupport;

import com.mkt.task.application.TaskInstanceStore;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.dao.DuplicateKeyException;

public final class MemoryTaskInstanceStore implements TaskInstanceStore {

    public final ConcurrentHashMap<Long, TaskInstanceEntity> rows = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<Long, List<TaskInstanceStepEntity>> steps = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);
    private final AtomicLong stepSeq = new AtomicLong(1);
    private final ConcurrentHashMap<String, Long> unique = new ConcurrentHashMap<>();

    @Override
    public int insert(TaskInstanceEntity entity) {
        String key = uniqueKey(entity.getUserId(), entity.getTaskId(), entity.getCycleKey());
        long id = seq.getAndIncrement();
        entity.setId(id);
        Long existing = unique.putIfAbsent(key, id);
        if (existing != null) {
            throw new DuplicateKeyException("uk_user_task_cycle");
        }
        rows.put(id, entity);
        return 1;
    }

    @Override
    public TaskInstanceEntity getById(long id) {
        return rows.get(id);
    }

    @Override
    public TaskInstanceEntity getByUserTaskCycle(long userId, long taskId, String cycleKey) {
        Long id = unique.get(uniqueKey(userId, taskId, cycleKey));
        return id == null ? null : rows.get(id);
    }

    @Override
    public List<TaskInstanceEntity> listByUser(long userId) {
        return rows.values().stream()
                .filter(row -> row.getUserId() == userId)
                .sorted(Comparator.comparing(TaskInstanceEntity::getId).reversed())
                .toList();
    }

    @Override
    public List<TaskInstanceEntity> listInProgressByUser(long userId) {
        return rows.values().stream()
                .filter(row -> row.getUserId() == userId && "IN_PROGRESS".equals(row.getStatus()))
                .toList();
    }

    @Override
    public List<TaskInstanceEntity> listMine(
            long userId, String status, List<Long> categoryTaskIds, long offset, int limit) {
        return rows.values().stream()
                .filter(row -> row.getUserId() == userId)
                .filter(row -> status == null || status.equals(row.getStatus()))
                .filter(row -> categoryTaskIds == null || categoryTaskIds.contains(row.getTaskId()))
                .sorted(Comparator.comparing(TaskInstanceEntity::getId).reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public long countMine(long userId, String status, List<Long> categoryTaskIds) {
        return listMine(userId, status, categoryTaskIds, 0, Integer.MAX_VALUE).size();
    }

    @Override
    public int countToday(long userId, LocalDateTime from, LocalDateTime to) {
        return (int) rows.values().stream()
                .filter(row -> row.getUserId() == userId)
                .filter(row -> row.getCreatedAt() != null
                        && !row.getCreatedAt().isBefore(from)
                        && row.getCreatedAt().isBefore(to))
                .count();
    }

    @Override
    public boolean existsInProgress(long userId, List<Long> taskIds, String cycleKey) {
        if (taskIds == null || taskIds.isEmpty()) {
            return false;
        }
        return rows.values().stream().anyMatch(row -> row.getUserId() == userId
                && "IN_PROGRESS".equals(row.getStatus())
                && taskIds.contains(row.getTaskId())
                && (cycleKey == null || cycleKey.equals(row.getCycleKey())));
    }

    @Override
    public long countInProgress(long userId) {
        return rows.values().stream()
                .filter(row -> row.getUserId() == userId && "IN_PROGRESS".equals(row.getStatus()))
                .count();
    }

    @Override
    public long countHistory(long userId) {
        return rows.values().stream()
                .filter(row -> row.getUserId() == userId
                        && ("COMPLETED".equals(row.getStatus())
                                || "ABANDONED".equals(row.getStatus())
                                || "EXPIRED".equals(row.getStatus())))
                .count();
    }

    @Override
    public int completeInstance(long id, LocalDateTime completedAt, int costSeconds) {
        TaskInstanceEntity row = rows.get(id);
        if (row == null || !"IN_PROGRESS".equals(row.getStatus())) {
            return 0;
        }
        row.setStatus("COMPLETED");
        row.setCompletedAt(completedAt);
        row.setCostSeconds(costSeconds);
        return 1;
    }

    @Override
    public int insertStep(TaskInstanceStepEntity entity) {
        entity.setId(stepSeq.getAndIncrement());
        steps.computeIfAbsent(entity.getInstanceId(), key -> new ArrayList<>()).add(entity);
        return 1;
    }

    @Override
    public List<TaskInstanceStepEntity> listSteps(long instanceId) {
        List<TaskInstanceStepEntity> list = steps.get(instanceId);
        if (list == null) {
            return List.of();
        }
        return list.stream()
                .sorted(Comparator.comparing(TaskInstanceStepEntity::getSeq))
                .toList();
    }

    @Override
    public int activateStep(long id, LocalDateTime activatedAt) {
        TaskInstanceStepEntity row = step(id);
        if (row == null || !"INACTIVE".equals(row.getStatus())) {
            return 0;
        }
        row.setStatus("ACTIVE");
        row.setActivatedAt(activatedAt);
        return 1;
    }

    @Override
    public int completeStep(long id, LocalDateTime completedAt) {
        TaskInstanceStepEntity row = step(id);
        if (row == null || !"ACTIVE".equals(row.getStatus())) {
            return 0;
        }
        row.setStatus("COMPLETED");
        row.setCompletedAt(completedAt);
        row.setVersion((row.getVersion() == null ? 0 : row.getVersion()) + 1);
        return 1;
    }

    private TaskInstanceStepEntity step(long id) {
        for (List<TaskInstanceStepEntity> list : steps.values()) {
            for (TaskInstanceStepEntity row : list) {
                if (row.getId() != null && row.getId() == id) {
                    return row;
                }
            }
        }
        return null;
    }

    private static String uniqueKey(Long userId, Long taskId, String cycleKey) {
        return userId + ":" + taskId + ":" + cycleKey;
    }
}
