package com.mkt.task.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.contract.InstanceCounts;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.testsupport.MemoryTaskInstanceStore;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TaskReadPortImplTest {

    @Test
    void countsSplitInProgressAndHistory() {
        MemoryTaskInstanceStore store = new MemoryTaskInstanceStore();
        store.insert(row(1L, "IN_PROGRESS"));
        store.insert(row(2L, "COMPLETED"));
        store.insert(row(3L, "ABANDONED"));
        InstanceCounts counts = new TaskReadPortImpl(store).instanceCounts(9L);
        assertThat(counts.inProgressInstanceCount()).isEqualTo(1L);
        assertThat(counts.historyInstanceCount()).isEqualTo(2L);
    }

    private static TaskInstanceEntity row(long taskId, String status) {
        TaskInstanceEntity entity = new TaskInstanceEntity();
        entity.setTaskId(taskId);
        entity.setTaskCode("t" + taskId);
        entity.setVersion(1);
        entity.setSnapshotId(1L);
        entity.setUserId(9L);
        entity.setCycleKey("NONE-" + taskId);
        entity.setStatus(status);
        entity.setExpireAt(LocalDateTime.parse("2026-12-31T00:00:00"));
        entity.setStartedAt(LocalDateTime.parse("2026-08-19T00:00:00"));
        entity.setCreatedAt(LocalDateTime.parse("2026-08-19T00:00:00"));
        return entity;
    }
}
