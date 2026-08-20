package com.mkt.task.testsupport;

import com.mkt.task.application.TaskProgressReportStore;
import com.mkt.task.entity.TaskProgressReportEntity;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.dao.DuplicateKeyException;

public final class MemoryTaskProgressReportStore implements TaskProgressReportStore {

    public final ConcurrentHashMap<Long, TaskProgressReportEntity> rows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> unique = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);

    @Override
    public int insert(TaskProgressReportEntity entity) {
        String key = key(entity.getInstanceId(), entity.getStepCode(), entity.getReportId());
        long id = entity.getId() == null ? seq.getAndIncrement() : entity.getId();
        entity.setId(id);
        Long existing = unique.putIfAbsent(key, id);
        if (existing != null) {
            throw new DuplicateKeyException("uk_dedup");
        }
        rows.put(id, entity);
        return 1;
    }

    @Override
    public TaskProgressReportEntity getByDedup(long instanceId, String stepCode, String reportId) {
        Long id = unique.get(key(instanceId, stepCode, reportId));
        return id == null ? null : rows.get(id);
    }

    @Override
    public int deleteBefore(LocalDateTime cutoff, int limit) {
        List<Long> victims = new ArrayList<>();
        for (TaskProgressReportEntity row : rows.values()) {
            if (row.getCreatedAt() != null && row.getCreatedAt().isBefore(cutoff)) {
                victims.add(row.getId());
                if (victims.size() >= limit) {
                    break;
                }
            }
        }
        for (Long id : victims) {
            TaskProgressReportEntity removed = rows.remove(id);
            if (removed != null) {
                unique.remove(key(removed.getInstanceId(), removed.getStepCode(), removed.getReportId()));
            }
        }
        return victims.size();
    }

    private static String key(Long instanceId, String stepCode, String reportId) {
        return instanceId + ":" + stepCode + ":" + reportId;
    }
}
