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
    public int remainingCompleteCasFailures;
    public int remainingAddProgressCasFailures;
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

    @Override
    public TaskInstanceStepEntity getStep(long instanceId, String stepCode) {
        List<TaskInstanceStepEntity> list = steps.get(instanceId);
        if (list == null) {
            return null;
        }
        for (TaskInstanceStepEntity row : list) {
            if (stepCode.equals(row.getStepCode())) {
                return row;
            }
        }
        return null;
    }

    @Override
    public TaskInstanceStepEntity getStepById(long id) {
        return step(id);
    }

    @Override
    public synchronized int completeStepCas(
            long id, int version, LocalDateTime completedAt, Integer progressCurrent) {
        if (remainingCompleteCasFailures > 0) {
            remainingCompleteCasFailures--;
            return 0;
        }
        TaskInstanceStepEntity row = step(id);
        if (row == null || !"ACTIVE".equals(row.getStatus()) || versionOf(row) != version) {
            return 0;
        }
        row.setStatus("COMPLETED");
        row.setCompletedAt(completedAt);
        row.setVersion(version + 1);
        if (progressCurrent != null) {
            row.setProgressCurrent(progressCurrent);
        }
        return 1;
    }

    @Override
    public synchronized int skipStepCas(long id, int version, LocalDateTime completedAt, String skipReason) {
        TaskInstanceStepEntity row = step(id);
        if (row == null || !"ACTIVE".equals(row.getStatus()) || versionOf(row) != version) {
            return 0;
        }
        row.setStatus("SKIPPED");
        row.setSkipReason(skipReason);
        row.setCompletedAt(completedAt);
        row.setVersion(version + 1);
        return 1;
    }

    @Override
    public synchronized int addProgressCas(long id, int version, int progressCurrent) {
        if (remainingAddProgressCasFailures > 0) {
            remainingAddProgressCasFailures--;
            return 0;
        }
        TaskInstanceStepEntity row = step(id);
        if (row == null || !"ACTIVE".equals(row.getStatus()) || versionOf(row) != version) {
            return 0;
        }
        row.setProgressCurrent(progressCurrent);
        row.setVersion(version + 1);
        return 1;
    }

    @Override
    public int updateLastBizNo(long id, String lastBizNo) {
        TaskInstanceStepEntity row = step(id);
        if (row == null) {
            return 0;
        }
        row.setLastBizNo(lastBizNo);
        return 1;
    }

    @Override
    public List<TaskInstanceEntity> listAdmin(
            Long taskId,
            Long userId,
            String status,
            Integer simulated,
            LocalDateTime from,
            LocalDateTime to,
            long offset,
            int limit) {
        return rows.values().stream()
                .filter(row -> matchesAdmin(row, taskId, userId, status, simulated, from, to))
                .sorted(Comparator.comparing(TaskInstanceEntity::getId).reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public long countAdmin(
            Long taskId, Long userId, String status, Integer simulated, LocalDateTime from, LocalDateTime to) {
        return rows.values().stream()
                .filter(row -> matchesAdmin(row, taskId, userId, status, simulated, from, to))
                .count();
    }

    @Override
    public List<TaskInstanceEntity> listDueToExpire(LocalDateTime now, int limit) {
        return rows.values().stream()
                .filter(row -> "IN_PROGRESS".equals(row.getStatus()))
                .filter(row -> row.getExpireAt() != null && !row.getExpireAt().isAfter(now))
                .sorted(Comparator.comparing(TaskInstanceEntity::getExpireAt)
                        .thenComparing(TaskInstanceEntity::getId))
                .limit(limit)
                .toList();
    }

    @Override
    public int abandonCas(long id, String source, LocalDateTime abandonedAt, int costSeconds) {
        TaskInstanceEntity row = rows.get(id);
        if (row == null || !"IN_PROGRESS".equals(row.getStatus())) {
            return 0;
        }
        row.setStatus("ABANDONED");
        row.setAbandonSource(source);
        row.setAbandonedAt(abandonedAt);
        row.setCostSeconds(costSeconds);
        return 1;
    }

    @Override
    public int expireCas(long id, LocalDateTime now, int costSeconds) {
        TaskInstanceEntity row = rows.get(id);
        if (row == null || !"IN_PROGRESS".equals(row.getStatus())) {
            return 0;
        }
        if (row.getExpireAt() == null || row.getExpireAt().isAfter(now)) {
            return 0;
        }
        row.setStatus("EXPIRED");
        row.setCostSeconds(costSeconds);
        return 1;
    }

    private static boolean matchesAdmin(
            TaskInstanceEntity row,
            Long taskId,
            Long userId,
            String status,
            Integer simulated,
            LocalDateTime from,
            LocalDateTime to) {
        if (taskId != null && (row.getTaskId() == null || row.getTaskId() != taskId)) {
            return false;
        }
        if (userId != null && (row.getUserId() == null || row.getUserId() != userId)) {
            return false;
        }
        if (status != null && !status.equals(row.getStatus())) {
            return false;
        }
        if (simulated != null && (row.getSimulated() == null || !simulated.equals(row.getSimulated()))) {
            return false;
        }
        if (from != null && (row.getStartedAt() == null || row.getStartedAt().isBefore(from))) {
            return false;
        }
        if (to != null && (row.getStartedAt() == null || row.getStartedAt().isAfter(to))) {
            return false;
        }
        return true;
    }

    private static int versionOf(TaskInstanceStepEntity row) {
        return row.getVersion() == null ? 0 : row.getVersion();
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
